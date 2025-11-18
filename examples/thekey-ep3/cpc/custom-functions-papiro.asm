;-----------------------------------------------
custom_papiro_characters:
    db #40, #a0, #a0, #e0, #a0  ; A
    db #c0, #a0, #c0, #a0, #c0  ; B
    db #c0, #a0, #a0, #a0, #c0  ; D
    db #e0, #80, #c0, #80, #e0  ; E
    db #a0, #a0, #e0, #a0, #a0  ; H
    db #60, #20, #20, #a0, #60  ; J
    db #a0, #c0, #c0, #a0, #a0  ; K
    db #80, #80, #80, #80, #c0  ; L
    db #c0, #a0, #a0, #a0, #a0  ; N
    db #40, #a0, #a0, #a0, #40  ; O
    db #c0, #a0, #a0, #c0, #80  ; P
    db #c0, #a0, #a0, #c0, #a0  ; R
    db #e0, #80, #e0, #20, #e0  ; S
    db #e0, #40, #40, #40, #40  ; T
    db #a0, #a0, #a0, #a0, #e0  ; U
    db #a0, #a0, #e0, #40, #40  ; Y

custom_papiro_word1:
    db 2, #60  ; KA: length, and then the characters (2 per byte)
custom_papiro_word2:
    db 4, #0d, #98  ; ATON
custom_papiro_word3:
    db 4, #08, #64  ; ANKH
custom_papiro_word4:
    db 2, #05  ; AJ
custom_papiro_word5:
    db 3, #c0, #40  ; SAH
custom_papiro_word6:
    db 7, #08, #2f, #3d, #f0   ; ANDYETY
custom_papiro_word7:
    db 4, #40, #af  ; HAPY
custom_papiro_word8:
    db 2, #10  ; BA

; rows of background letters, 11 rows of 14 letters each
; vocabulary: ABDE HJKL NOPR STUY
custom_papiro_background:
    db #ec, #df, #76, #cb, #9f, #c3, #d4  ; USTYLKSROYSETH
    db #6e, #fc, #f9, #8c, #f9, #db, #f3  ; KUYSYONSYOTRYE
    db #98, #60, #f3, #cf, #8e, #63, #d6  ; ONKAYESYNUKETK
    db #09, #cf, #cc, #9d, #bc, #fd, #f4  ; AOSYSSOTRSYTYH
    db #fd, #9d, #0f, #d3, #f2, #80, #df  ; YTOTAYTEYDNATY
    db #c0, #86, #4d, #c4, #a8, #0c, #7d  ; SANKHTSHPNASLT
    db #f3, #05, #dc, #3d, #0d, #1b, #f6  ; YEAJTSETATBRYK 
    db #1c, #48, #7b, #18, #4f, #c4, #7f  ; BSHNLRBNHYSHLY
    db #4f, #6f, #81, #c7, #3b, #6f, #b8  ; HYKYNBSLERKYRN
    db #f7, #c4, #bf, #d4, #b8, #db, #c6  ; YLSHRYTHRNTRSK
    db #48, #f3, #c0, #d6, #1d, #08, #1f  ; HNYESATKBTANBY

custom_papiro_letter_states:
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00
    db #00, #00

; RADIO_COLOR_WHITE_MASK: equ #54
PAPIRO_FOREGROUND_MASK: equ #45
PAPIRO_BACKGROUND_MASK: equ #15
PAPIRO_BACKGROUND_SELECTED_MASK: equ #00
PAPIRO_FOREGROUND_SELECTED_MASK: equ #44


;-----------------------------------------------
custom_papiro_draw_letters:
    ld a, (game_state_variable_papiro_palabras)
    add a, a
    jr z, custom_papiro_draw_letters_bg
    ld hl, custom_papiro_word1
    ld de, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*6 + 6 + #0000
    ld ixl, PAPIRO_BACKGROUND_MASK
    ld ixh, RADIO_COLOR_WHITE_MASK
custom_papiro_draw_letters_loop:
    push af
        ld b, (hl)
        inc hl
        push de
            call custom_papiro_draw_word
        pop de
        ex de, hl
            ld bc, SCREEN_WIDTH_IN_BYTES
            add hl, bc
        ex de, hl
    pop af
    dec a
    jr nz, custom_papiro_draw_letters_loop

custom_papiro_draw_letters_bg:
    ; draw the letter matrix:
    ld b, 11  ; number of rows
    ld hl, custom_papiro_background
    ld de, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*6 + 22 + #0000
    ld iy, custom_papiro_letter_states
custom_papiro_draw_letters_loop2:
    push bc
        push de
            ld c, (iy)
            inc iy
            ld b, 4
            call custom_papiro_draw_bg_row
            ld c, (iy)
            inc iy
            ld b, 3
            call custom_papiro_draw_bg_row
        pop de
        ex de, hl
            ld bc, SCREEN_WIDTH_IN_BYTES
            add hl, bc
        ex de, hl
    pop bc
    djnz custom_papiro_draw_letters_loop2
    ret


;-----------------------------------------------
; input:
; - hl: ptr to letters
; - b: length in letters
; - de: video mem ptr
; - ixl / ixh: background, foreground attributes
custom_papiro_draw_word:
    ld a, (hl)
    rrca
    rrca
    rrca
    rrca
    and #0f
    call custom_papiro_draw_word_letter
    dec b
    jr z, custom_papiro_draw_word_done_odd
    inc de
    inc de
    ld a, (hl)
    and #0f
    call custom_papiro_draw_word_letter
    inc de
    inc de
    inc hl
    dec b
    jr nz, custom_papiro_draw_word
    ret
custom_papiro_draw_word_done_odd:
    inc hl
    ret


;-----------------------------------------------
; input:
; - hl: ptr to letters
; - de: video mem ptr
; - b: number of letters to draw / 2
; - c: bitmask with selected status
custom_papiro_draw_bg_row:
    call custom_papiro_draw_bg_row_choose_attributes
    push bc
    push hl
        ld a, (hl)
        rrca
        rrca
        rrca
        rrca
        and #0f
        call custom_papiro_draw_word_letter
        inc de
        inc de
    pop hl
    pop bc
    call custom_papiro_draw_bg_row_choose_attributes
    push bc
    push hl
        ld a, (hl)
        and #0f
        call custom_papiro_draw_word_letter
        inc de
        inc de
    pop hl
    pop bc
    inc hl
    djnz custom_papiro_draw_bg_row
    ret


;-----------------------------------------------
custom_papiro_draw_bg_row_choose_attributes:
    rl c
    jr c, custom_papiro_draw_bg_row_selected
custom_papiro_draw_bg_row_not_selected:
    ld ix, PAPIRO_FOREGROUND_MASK * 256 + PAPIRO_BACKGROUND_MASK
    ret
custom_papiro_draw_bg_row_selected:
    ld ix, PAPIRO_FOREGROUND_SELECTED_MASK * 256 + PAPIRO_BACKGROUND_SELECTED_MASK
    ret


;-----------------------------------------------
; input:
; - a: letter to draw
; - de: video mem ptr
; - ixl / ixh: background, foreground attributes
custom_papiro_draw_word_letter:
    push hl
    push de
    push bc
        ; two rows above the letter:
        push af
            ld a, ixl
            rlca
            or ixl
            ld (de), a
            inc de
            ld (de), a
            ex de, hl
                ld bc, #800 - 1
                add hl, bc
            ex de, hl
            ld (de), a
            inc de
            ld (de), a
            ex de, hl
                ld bc, #800 - 1
                add hl, bc
            ex de, hl
        pop af

        ld hl, custom_papiro_characters
        ld c, a
        add a, a
        add a, a
        add a, c
        ADD_HL_A
        ; ptr to bytes to draw:
        ld b, 5
custom_papiro_draw_word_letter_loop:
        push bc
            ld b, (hl)
            call custom_papiro_draw_word_letter_2pixels
            inc de
            call custom_papiro_draw_word_letter_2pixels
            inc hl
            ex de, hl
                ld bc, #800 - 1
                add hl, bc
            ex de, hl
        pop bc
        djnz custom_papiro_draw_word_letter_loop

        ; last row below the letter:
        ld a, ixl
        rlca
        or ixl
        ld (de), a
        inc de
        ld (de), a
    pop bc
    pop de
    pop hl
    ret

;-----------------------------------------------
; input:
; - b: letter bits
; - de: video mem ptr
; - ixl / ixh: background, foreground attributes
custom_papiro_draw_word_letter_2pixels:
    xor a  ; byte we will draw
    ld c, 0  ; byte we will write
    rl b
    jr nc, custom_papiro_draw_word_letter_2pixels_bg1
    or ixh
    jr custom_papiro_draw_word_letter_2pixels_continue1
custom_papiro_draw_word_letter_2pixels_bg1:
    or ixl
custom_papiro_draw_word_letter_2pixels_continue1:
    rlca
    rl b
    jr nc, custom_papiro_draw_word_letter_2pixels_bg2
    or ixh
    jr custom_papiro_draw_word_letter_2pixels_continue2
custom_papiro_draw_word_letter_2pixels_bg2:
    or ixl
custom_papiro_draw_word_letter_2pixels_continue2:
    ld (de), a  ; draw firt block of 2 pixels
    ret


;-----------------------------------------------
custom_papiro_click_letter:
    ; get coordinate:
    ; - ((pointer_x) - 42) / 4
    ; - (pointer_y) / 8
    ld a, (pointer_x)
    sub 42
    ret c
    rrca
    rrca
    and #3f
    cp 14
    ret nc
    ld c, a  ; c = column clicked
    ld a, (pointer_y)
    add a, 3
    rrca
    rrca
    rrca
    and #1f
    cp 11
    ret nc
    ld b, a  ; b = row clicked

    ; ptr: custom_papiro_letter_states + b * 2 + c/8
    push bc
        ld a, c
        rrca
        rrca
        rrca
        and #07
        add a, b
        add a, b
        ld hl, custom_papiro_letter_states
        ADD_HL_A  ; hl = ptr where the bit we need to flip is
    pop bc
    ld a, c
    and #07
    ld c, #80
    jr z, custom_papiro_click_letter_done
custom_papiro_click_letter_bit_loop:
    rr c
    dec a
    jr nz, custom_papiro_click_letter_bit_loop
custom_papiro_click_letter_done:
    ld a, c
    xor (hl)
    ld (hl), a

    ; redraw:
    call clear_pointer
    call custom_papiro_draw_letters_bg
    jp draw_pointer
