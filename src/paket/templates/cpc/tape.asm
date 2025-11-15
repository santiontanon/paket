;------------------------------------------------------------------------
; PAKETCAS v1
; Amstrad CPC cassette tape file load/save library for the PAKET engine
; Santiago Ontañón, 2023
;
; PAKETCAS is an assembler library to interface with the cassette tape in Amstrad CPC computers,
; without using the firmware. It was created by starting from a firmware disassembly, and extracting
; those functions that were necessary for loading and saving files to tape, annotating
; the whole source code to understand what each function did, and then simplifying as much as possible.
;
; How to use:
; - Include this file in your project
; - The file assumes the existence of a label called "general_buffer", where it
;   can place it's RAM variables (it just needs 6 bytes of RAM to work).
; - The two main functions are: paketcas_read and paketcas_write.

; mdl:no-opt-start  (we disable MDL optimizations in this file, as timing is key)

PAKETCAS_HALF_A_ZERO_DURATION: equ #53
PAKETCAS_PRECOMPENSATION: equ #06
PAKETCAS_SYNCHRONIZATION_BYTE: equ #16
PAKETCAS_PILOT_SIGNAL_WAIT: equ 10000


; ------------------------------------------------
; RAM variables:
paketcas_pulse_state: equ general_buffer  ; keeping track if we need to read a pulse going up or down the next time
paketcas_period_estimate: equ general_buffer + 1  ; estimated from the pilot tone
paketcas_last_pulse_period: equ general_buffer + 2
paketcas_checksum: equ general_buffer + 3


; ------------------------------------------------
; Read data from tape.
; Input:
; - e: amount of data to read.
; - ix: data buffer to read to.
; Output:
; - c: success
; - nc: failure
paketcas_read:
    call paketcas_read_write_setup
    ld l, a
    push hl  ; push the previous state of the tape motor.
        call paketcas_read_find_sync_byte
        jr nc, paketcas_read_failure_pop
        ld bc, #ffff
        ld (paketcas_checksum), bc    
        call paketcas_read_block_internal
paketcas_read_write_finish:
        ld bc, #f782
        out (c), c  ; 8255 PIO Control-Register: set direction of port A to "output"
        ld bc, #f610
        out (c), c  ; 8255 PIO Port C: motor on, write data bit = 0
        ei
    pop hl  ; recover the state of the tape motor
    push af  ; preserve the status of the carry flag
        ld a, l
        call paketcas_restore_motor  ; set the motor to whatever state it was before we called "paketcas_read" or "paketcas_write"
    pop af
    ret
paketcas_read_failure_pop:
    pop hl
    ; ccf
    ret


; ------------------------------------------------
; Write data to tape.
; Input:
; - e: amount of data to write.
; - ix: data buffer to write.
; Output:
; - c: success
; - nc: failure
paketcas_write:
    call paketcas_read_write_setup
    ld l, a
    push hl  ; push the previous state of the tape motor.
        call paketcas_write_pilot_tone_and_sync_byte
        ld bc, #ffff
        ld (paketcas_checksum), bc
        call c, paketcas_write_block_internal
        call c, paketcas_write_33_one_bits
        jr paketcas_read_write_finish


; ------------------------------------------------
; Reads a block of data from the tape:
; - First reads "e" bytes and saves them to "ix"
; - Then gets the checksum and verifies it matches
; input:
; - ix: data pointer
; - e: amount of bytes to read in the tape block
; output:
; - c: success
; - nc: error
paketcas_read_block_internal:
paketcas_read_block_internal_loop:
    call paketcas_read_byte
    ret nc
    ld (ix), a
    inc ix
    dec e
    jr nz, paketcas_read_block_internal_loop
    ; Check if the checksum matches:
    ld de, (paketcas_checksum)
    call paketcas_read_byte
    ret nc
    xor d  ; cp d
    ret nz
    call paketcas_read_byte
    ret nc
    xor e  ; cp e
    ret nz  ; return if failure (here, we have "nc" because of the previous xor)
    ; success (checksum did match):
    scf
    ret


; ------------------------------------------------
; Writes "e" bytes of data from "ix" to the tape, and then pads with zeros, until
; at least "d" bytes are written. After the whole thing is written, the checksum 
; (2 bytes) are written.
; input:
; - ix: data pointer
; - e: amount of bytes of content left to write
; output:
; - c: success
; - nc: error
paketcas_write_block_internal:
paketcas_write_block_internal_loop:
    ld a, (ix)
    call paketcas_write_byte
    ret nc
    inc ix
    dec e
    jr nz, paketcas_write_block_internal_loop
    ld de, (paketcas_checksum)
    ld a, d
    call paketcas_write_byte  ; writes "d"
    ret nc  ; return if error
    ld a, e
    jp paketcas_write_byte  ; write "e"


; ------------------------------------------------
; Keeps reading the pilot tone until we find the sync byte.
; input:
; - ix: data buffer pointer
; output:
; - c: success
; - nc: failure
paketcas_read_find_sync_byte:
    ld b, 5  ; 5 retries
paketcas_read_find_sync_byte_loop:
    push bc
    push de
        call paketcas_read_pilot_tone_and_sync_byte
    pop de
    pop bc
    jr nc, paketcas_read_find_sync_byte_failure
    ; ret nc  ; failure
    or a
    scf
    ret z
paketcas_read_find_sync_byte_failure:
    djnz paketcas_read_find_sync_byte_loop
    or a  ; nc: failure
    ret


; ------------------------------------------------
; Reads the pilot tone trying to estimate the wave frequency (period). Once it has a good estimate,
; if it detects a pulse that is much shorter, it assumes it was a "0" (end of pilot tone), and 
; it tries to read the sync byte.
; input:
; - ix: data buffer pointer
; output:
; - carry: success
; - no carry: failure. In this case if a != 0 it means we did not match the sync byte but there
;             was no bit read error, so we might want to keep reading the pilot tone again.
paketcas_read_pilot_tone_and_sync_byte:
    ; Wait for the first pulse:
    ld de, PAKETCAS_PILOT_SIGNAL_WAIT  ; amount of wait allowed (10-20 seconds)
paketcas_read_pilot_tone_and_sync_byte_loop:
    dec de
    ld a, e
    or d
    ret z  ; timeout (nc)
    ld l, #aa
    call wait_for_tape_bit_change
    jr nc, paketcas_read_pilot_tone_and_sync_byte_loop
    ; Read 256 bits of the pilot tone, and accumulate the period in "de":
    ld de, 0
    ld h, d
paketcas_read_pilot_tone_and_sync_byte__pilot_tone_estimate_loop:
    call wait_for_tape_bit_change
    ret nc
    ex de, hl
        ld b, 0
        add hl, bc
    ex de, hl
    dec h
    jr nz, paketcas_read_pilot_tone_and_sync_byte__pilot_tone_estimate_loop
    ; - "d" now contains an estimate of the period of the pilot tone.
paketcas_read_pilot_tone_and_sync_byte__pilot_tone_period_adjust_loop:
    ; Adjust the period estimate:
    ; - We should be reading all "1", if we read a "0", it means we have the wrong
    ;   period estimate. So, we adjust it by reducing the estimate until we read all "1"s.
    ld h, c  ; h = "period of last pulse"
    ld a, c
    sub d
    ld c, a  ; c = "period of last pulse" - "period estimate" (delta1)
    sbc a, a
    ld b, a  ; b = 0 if we read a "1", and -1 if we read a "0"
    ex de, hl
        add hl, bc  ; adjust the "period estimate"
    ex de, hl
    call wait_for_tape_bit_change
    ret nc
    ld a, d
    srl a
    srl a
    adc a, d  ; a = "period estimate" * 1.25 + 1
    sub h  ; a = "period estimate" * 1.25 + 1  - "period of last pulse" (delta2)
    ; If the period we read was < than 1.25 times our estimate, we are fine (loop again).
    jr c, paketcas_read_pilot_tone_and_sync_byte__pilot_tone_period_adjust_loop
    sub c  ; otherwise, if delta1 > delta2 -> we are still in the pilot wave
    jr c, paketcas_read_pilot_tone_and_sync_byte__pilot_tone_period_adjust_loop
    ; We noticed a big difference, it's likely to be the final 0 of the pilot tone:
    ld a, d
    rra
    adc a, d
    ld h, a  ; h = "period estimate" * 1.5
    ld (paketcas_pulse_state), hl  ; save the period estimate to "paketcas_period_estiamte" and the pulse state to "paketcas_pulse_state"
    ; Verify that we can properly read the sync byte:
    call paketcas_read_byte
    ret nc
    xor PAKETCAS_SYNCHRONIZATION_BYTE
    ret nz
    scf  ; indicate success
    ret


; ------------------------------------------------
; - Waits a little bit to get time to the motor to start,
; - then writes the pilot tone, and 0 bit, and the synchronization byte
; output:
; - c: success
; - nc: failure
paketcas_write_pilot_tone_and_sync_byte:
    ; wait to give time to the motor to start:
    ld b, 2
paketcas_write_pilot_tone_and_sync_byte_wait_loop:
    ld hl, 0
paketcas_write_pilot_tone_and_sync_byte_wait_loop2:
    push iy
    pop iy
    dec hl
    ld a, h
    or l
    jr nz, paketcas_write_pilot_tone_and_sync_byte_wait_loop2
    djnz paketcas_write_pilot_tone_and_sync_byte_wait_loop

    ; write pilot tone:
    ld hl, #0801  ; length of the pilot tone
    call paketcas_write_hl_one_bits
    ret nc
    or a  ; write a 0
    call paketcas_write_bit
    ret nc  ; write the synchronization byte
    ld a, PAKETCAS_SYNCHRONIZATION_BYTE
    jp paketcas_write_byte


; ------------------------------------------------
; Writes "hl" bits to the tape, all 1s.
; input:
; - hl: number of bits to write
paketcas_write_33_one_bits:
    ld hl, #0021
paketcas_write_hl_one_bits:
    push hl
        scf
        call paketcas_write_bit
    pop hl
    dec hl
    ld a, h
    or l
    jr nz, paketcas_write_hl_one_bits
    scf
    ret


; ------------------------------------------------
; - Rotate "(paketcas_checksum)" one bit to the left, flipping two bits
;   when the sign of "a" and "hl" differ.
; - bits flipped:   0000x000000x0000
; Input:
; - a: if the sign of "a" and "hl" differs, two bits will be flipped before rotating.
paketcas_update_checksum:
    ld hl, (paketcas_checksum)
    xor h
    jp p, paketcas_update_checksum_no_flip
    ; Sign differs, invert the two center bits:
    ld a, h
    xor #08
    ld h, a
    ld a, l
    xor #10
    ld l, a
    scf
paketcas_update_checksum_no_flip:
    ; shift "hl" one bit to the left, putting the "carry" bit to the right.
    adc hl, hl
    ld (paketcas_checksum), hl
    ret


; ------------------------------------------------
; Read a byte from tape
; output:
; - a: byte we just read
; - c: success
; - nc: failure
paketcas_read_byte:
    push de
        ld e, 8
paketcas_read_byte_loop:
        ld hl, (paketcas_pulse_state)  ; l = "paketcas_pulse_state", h = "paketcas_period_estimate"
        call wait_for_tape_bit_change
        call c, wait_for_tape_bit_change_c_set
        jr nc, paketcas_read_byte_failure
        ; determine if we have read a 0 or a 1:
        ; - c: contains the duration (period) of the pulse we just read
        ; - h: period estimate from the pilot tone
        ; - if c > h -> it's a 1
        ;       else -> it's a 0
        ld a, h
        sub c
        sbc a, a
        rl d
        call paketcas_update_checksum
        dec e
        jr nz, paketcas_read_byte_loop
        ld a, d
        scf
paketcas_read_byte_failure:
    pop de
    ret


; ------------------------------------------------
; Waits for the "read tape bit" to differ from l's msb.
; It will only wait for a fixed number of time (controlled by the "r" register, see the
; docstring for the "change_tape_write_bit_with_desired_period" method for more details).
; input:
; - l: we will read the bit from the tape until the read bit is != from the msb of "l" (or until timeout).
; output:
; - l: shifted to the right 1 bit
; - c: period of the pulse that we just read
; - carry: success
; - no carry: failure
wait_for_tape_bit_change:
    ld a, r
    add a, 3
    rrca
    rrca
    and #1f
    ld c, a  ; c = (r + 3) / 4
wait_for_tape_bit_change_c_set:
    ld b, #f5  ; 8255 PIO Port B
    ; loop until the tape read bit is != from the msb of "l", or until
    ; we run out of time (accounted in "c")
wait_for_tape_bit_change_loop:
    ld a, c
    add a, 2  ; + 2, since each "1" is 4 instructions, and there are 8 instructions in this cycle.
    ld c, a
    jr c, wait_for_tape_bit_change_fail
    in a, (c)  ; 8255 PIO Port B (to read from tape)
    xor l
    and #80  ; keep only the "CAS.IN" bit
    jr nz, wait_for_tape_bit_change_loop
    ; success:
    ; - reset "r", and return with carry flag set, a = 0
    xor a
    ld r, a
    rrc l
    scf
    ret
wait_for_tape_bit_change_fail:
    ; failure:
    ; - reset "r", and return with carry flag cleared, a = 1, and nz
    xor a
    ld r, a
    inc a
    ret


; ------------------------------------------------
; Writes a byte to the tape
; input:
; - a: byte to write
; output:
; - c: success
; - nc: failure
paketcas_write_byte:
    push de
        ld e, 8  ; we will write 8 bits
        ld d, a
paketcas_write_byte_loop:
        rlc d  ; get next bit to the carry flag
        call paketcas_write_bit
        jr nc, paketcas_write_byte_exit
        dec e
        jr nz, paketcas_write_byte_loop
paketcas_write_byte_exit:
    pop de
    ret


; ------------------------------------------------
; Writes one bit (a pulse consisting of a 0, and then a 1). Depending on the status of the carry flag,
; it will write a pulse with period:
;   - "PAKETCAS_HALF_A_ZERO_DURATION" (no carry) -> 0
;   - "PAKETCAS_HALF_A_ZERO_DURATION * 2 + PAKETCAS_PRECOMPENSATION" (carry) -> 1
; input:
; - carry/no carry: determines the period/frequency of the pulse (0 or 1)
; output:
; - carry: successfully kept period
; - no carry: missed desired period
paketcas_write_bit:
    ; If carry (1):
    ;   - c (period) = (paketcas_last_pulse_period) - PAKETCAS_PRECOMPENSATION
    ;   - (paketcas_last_pulse_period) = PAKETCAS_HALF_A_ZERO_DURATION * 2 + PAKETCAS_PRECOMPENSATION
    ; If no carry (0):
    ;   - c (period) = (paketcas_last_pulse_period)
    ;   - (paketcas_last_pulse_period) = PAKETCAS_HALF_A_ZERO_DURATION
    ld hl, paketcas_last_pulse_period
    ld c, (hl)
    ld l, PAKETCAS_HALF_A_ZERO_DURATION
    sbc a, a  ; if carry == 0, a = 0, else a = #ff
    ld h, a
    jr z, paketcas_write_bit_continue
    ld l, PAKETCAS_HALF_A_ZERO_DURATION * 2 + PAKETCAS_PRECOMPENSATION
    ld a, c
    sub PAKETCAS_PRECOMPENSATION
    ld c, a
paketcas_write_bit_continue:
    ld a, l
    ld (paketcas_last_pulse_period), a
    ld l, #0a  ; tape write bit = 0
    call change_tape_write_bit_with_desired_period  ; c == period
    jr c, paketcas_write_bit_period_ok  ; if we could use the desired period, jump
    ; "a" here has by how much we missed the period
    sub c  ; subtract the period
    ret nc  ; if we were more than a whole period late, we are done
    ; change sign: (not sure why not just "neg")
    cpl
    inc a
    ld c, a  ; update "c" with how much do we want to still wait
paketcas_write_bit_period_ok:
    ld a, h  ; #ff or #00 depending on whether we are writing a 1 or a 0 to the tape.
    call paketcas_update_checksum
    ld l, #0b  ; tape write bit = 1
    jp change_tape_write_bit_with_desired_period  ; c == period


; ------------------------------------------------
; - The "r" register increments by 1 at each "M1" cycle (this is the very first cycle of
;   each Z80 instruction). So, it counts how many instructions have been executed (with a
;   7 bit counter). So, this method uses this feature to keep track of the frequency with
;   which we change the tape write data bit, to accomplished the desired frequency.
; - The desired frequency is controlled by "c", which contains the period (number of M1
;   cycles between each tape write data bit change.
; - If enough cycles have passed, the bit is directly changed; otherwise, a wait loop, 
;   waits for the necessary number of M1 cycles.
; - After the bit is changed, the "r" register is reset, to start the count again.
; Set the tape write data bit to 0 or 1.
; Input:
; - c: period of the desired frequency
; - l: #0a: tape write data bit = 0 
;      #0b: tape write data bit = 1
; - carry: we managed to use the desired period
; - no carry: we had overflowed in the period (and "a" has by how much)
change_tape_write_bit_with_desired_period:
    ; loop for "r/2 - c" times (if r/2 < c):
    ld a, r
    srl a
    sub c  ; a = r/2 - c
    ; If "r" has recorded more than c * 2 M1 cycles, we directly update the bit.
    jr nc, change_tape_write_bit_with_desired_period_skip_loop
    ; Otherwise, we wait for the necessary M1 cycles with this loop (notice that
    ; each iteration of the loop is two M1 cycles):
change_tape_write_bit_with_desired_period_loop:
    inc a
    jr nz, change_tape_write_bit_with_desired_period_loop
change_tape_write_bit_with_desired_period_skip_loop:
    ld b, #f7
    out (c), l  ; 8255 PIO Control-Register: set tape write data bit to 0 (if l == #0a), or 1 (if l == #0b).
    ; reset the "r" register:
    push af
        xor a
        ld r, a
    pop af
    ret


; ------------------------------------------------
; Starts the motor and initializes the pointer to the data to read/write in ix, etc.
; Input:
; - ix: ptr to data buffer to write from/read to
; Output:
; - a: contains the motor's previous state
paketcas_read_write_setup:
    di
    ld hl, paketcas_pulse_state
    ld (hl), #55
    inc hl
    ld (hl), #62


; ------------------------------------------------
; Starts the tape motor.
; Output:
; - a: the state of the motor before we changed it.
; - carry set to true
paketcas_start_motor:
    ld a, #10
    jr paketcas_restore_motor


; ------------------------------------------------
; Stops the tape motor.
; Output:
; - a: the state of the motor before we changed it.
; - carry set to true
paketcas_stop_motor:
    xor a


; ------------------------------------------------
; Sets the motor to the desired on/off state
; Input:
; - a: the desired motor state (only bit 4 matters: 1: on, 0: off)
; Output:
; - a: the state of the motor before we changed it.
; - carry set to true
paketcas_restore_motor:
    push bc
        ld b, #f6
        in c, (c)  ; 8255 PIO Port C
        inc b  ; b = #f7
        and #10  ; keep the "motor on" bit
        ld a, 8  ; select bit 4
        jr z, paketcas_restore_motor_continue
        ; motor was on in the previous state
        inc a
paketcas_restore_motor_continue:
        out (c), a  ; 8255 PIO Control-Register (with bit 7 == 0): set bit 4 of port C to desired motor status.
        scf
        ld a, c
    pop bc
    ret

; mdl:no-opt-end
