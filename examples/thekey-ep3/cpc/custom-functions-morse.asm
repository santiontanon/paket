custom_current_song: equ general_buffer

;-----------------------------------------------
custom_cpc_morse:
    ; pause music:
    ld hl, MUSIC_current_song
    ld a, (hl)
    ld (custom_current_song), a
    xor a
    ld (hl), a
    call clear_PSG_volume

    ; set beep frequency:
    ld a, #80
    ld c,0
    call WRTPSG_PKT
    xor a
    ld c,1
    call WRTPSG_PKT

    ld hl, current_action_text_buffer
    ld b, (hl)  ; length
    inc hl
    call custom_cpc_morse_loop

    ; resume music:
    ld hl, MUSIC_current_song
    ld a, (hl)
    ld (MUSIC_current_song), a
    ret


;-----------------------------------------------
MORSE_CHARACTER_LINE: equ #19
MORSE_CHARACTER_DOT: equ #1b

custom_cpc_morse_loop:
    push bc
        ld a,(hl)
        inc hl
        or a
        jr z, custom_cpc_morse_loop_space
        cp MORSE_CHARACTER_LINE
        jr z, custom_cpc_morse_loop_line
        cp MORSE_CHARACTER_DOT
        jr nz, custom_cpc_morse_loop_skip
custom_cpc_morse_loop_dot:
        ld a, 15
        ld c, 8
        call WRTPSG_PKT
        ld bc, 20
        jr custom_cpc_morse_loop_skip

custom_cpc_morse_loop_line:
        ld a, 15
        ld c, 8
        call WRTPSG_PKT
        ld bc, 60
        jr custom_cpc_morse_loop_skip

custom_cpc_morse_loop_space:
        ld bc, 60

custom_cpc_morse_loop_skip:
        call wait_bc_halts
        call clear_PSG_volume
        ld bc, 20
        call wait_bc_halts
    pop bc
    djnz custom_cpc_morse_loop
    ret
