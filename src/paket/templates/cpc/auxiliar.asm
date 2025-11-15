;-----------------------------------------------
; clears a memory area with 0
; input:
; - hl: memory area
; - bc: amount to clear
clear_memory:
    xor a
clear_memory_to_a:
    ld d,h
    ld e,l
    inc de
    ld (hl),a
    dec bc
    ldir
    ret


;-----------------------------------------------
; waits a given number of "halts"
; bc - number of halts
wait_bc_halts:
    halt
    dec bc
    ld a, b
    or c
    jr nz,wait_bc_halts
    ret


;-----------------------------------------------
; Multiply "de" by "a", output is:
; - multiplication result in "hl"
; Code borrowed from: http://sgate.emt.bme.hu/patai/publications/z80guide/part4.html
mul8:                            ; this routine performs the operation HL=DE*A
    ld hl,0                        ; HL is used to accumulate the result
    ld b,8                         ; the multiplier (A) is 8 bits wide
Mul8Loop:
    rrca                           ; putting the next bit into the carry
    jp nc,mul8skip                 ; if zero, we skip the addition (jp is used for speed)
    add hl,de                      ; adding to the product if necessary
mul8skip:
    sla e                          ; calculating the next auxiliary product by shifting
    rl d                           ; DE one bit leftwards (refer to the shift instructions!)
    djnz Mul8Loop
    ret

;Mul8SignedA:
;    or a
;    jp p,Mul8
;    neg
;    call Mul8
;    ; negate the sign of hl:
;    xor a
;    sub l
;    ld l,a
;    sbc a,a
;    sub h
;    ld h,a
;    ret    


;-----------------------------------------------
; adapted source code from: http://www.cpcwiki.eu/index.php/How_to_access_the_PSG_via_PPI
; input:
; - C = register number
; - A = register data
; assumption: PPI port A and PPI port C are setup in output mode.
WRTPSG_PKT:
    ld b, #f4  ; setup PSG register number on PPI port A
    out (c), c
    ld bc, #f6c0  ; Tell PSG to select register from data on PPI port A
    out (c), c
    ld bc, #f600  ; Put PSG into inactive state.
    out (c), c
    ld b, #f4  ; setup register data on PPI port A
    out (c), a
    ld bc, #f680  ; Tell PSG to write data on PPI port A into selected register
    out (c), c
    ld bc, #f600  ; Put PSG into inactive state
    out (c), c
    ret


;-----------------------------------------------
; input:
; - hl: ptr to the text line
change_text_line_color:
    ld a, 8
change_current_dialogue_option_color_loop:
    push hl
        push af
            ld b, SCREEN_WIDTH_IN_BYTES
change_current_dialogue_option_color_loop2:
            ld a, (hl)
            srl a
            srl a
            srl a
            srl a
            xor (hl)  ; this basically inverts the least significant nibble (only for non black pixels), causing it to alternate between grey and white
            ld (hl), a
            inc hl
            djnz change_current_dialogue_option_color_loop2
         pop af
    pop hl
    ld bc, #800
    add hl, bc
    dec a
    jr nz, change_current_dialogue_option_color_loop
    ret


;-----------------------------------------------
; A couple of useful macros for adding 16 and 8 bit numbers

ADD_HL_A: MACRO 
    add a,l
    ld l,a
    jr nc, $+3
    inc h
    ENDM

ADD_DE_A: MACRO 
    add a,e
    ld e,a
    jr nc, $+3
    inc d
    ENDM    

ADD_HL_A_VIA_BC: MACRO
    ld b,0
    ld c,a
    add hl,bc
    ENDM


