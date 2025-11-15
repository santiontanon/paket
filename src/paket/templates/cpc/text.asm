;-----------------------------------------------
; Decompresses a specific text sentence from a text bank
; input:
; - de: target destination of decompression
; - c: bank #
; - a: text # within the bank
; output:
; - de: pointer to immediately after the text
get_text_from_bank:
IF IS_6128 == 1
    SET_128K_PAGE_4000_PUSHING_PREVIOUS TEXT_PAGE
ENDIF
    ld hl, textBankPointers
    ld b, 0
    add hl, bc
    add hl, bc
    push de
        ld e, (hl)
        inc hl
        ld d, (hl)
        ex de, hl  ; hl has the pointer of the text bank
        push af
            ld de, general_buffer + ROOM_LOAD_BUFFER_ALLOCATED_SPACE  ; we add this offset to be safe during room loading
            call PAKET_UNPACK
        pop af
        ld hl, general_buffer + ROOM_LOAD_BUFFER_ALLOCATED_SPACE
get_text_from_bank_loop:
        or a
        jr z, get_text_from_bank_found
        ld b, 0
        ld c, (hl)
        inc hl
        add hl, bc
        dec a
        jr get_text_from_bank_loop
get_text_from_bank_found:
    pop de
    ; copy the desired string to "de":
    ld b, 0
    ld c, (hl)
    inc bc
    ldir
IF IS_6128 == 1
    SET_128K_PAGE_4000_FROM_POP
ENDIF
    ret


; ------------------------------------------------
; Calculates the maximum length that can be rendered without overflowing a single line of text.
; Only "spaces" are considered as possible splits
; input:
; - hl: sentence
; output:
; - a: number of characters that can be drawn
max_length_of_sentence_in_one_line:
IF IS_6128 == 1
    SET_128K_PAGE_4000_PUSHING_PREVIOUS FONT_DATA_PAGE
ENDIF
    ld ixl, 0  ; best found so far
    ld ixh, 0  ; current character
    ld b, (hl)  ; get the sentence length
    inc hl
    xor a  ; pixels drawn
max_length_of_sentence_in_one_line_loop:
    ld d, 0
    ld e, (hl)
    ex af, af'
        ld a, e
        or a  ; is it a space?
        jr nz, max_length_of_sentence_in_one_line_no_space
        ld a, ixh
        inc a  ; we increase it in one to skip the space
        ld ixl, a
max_length_of_sentence_in_one_line_no_space:
    ex af, af'
    inc ixh
    push hl
        ld hl, font
        add hl, de
        add hl, de
        add hl, de  ; index of the letter is a*3
        add a, (hl)  ; letter width in pixels
    pop hl
    inc hl  ; does not modify the flags
IF SCREEN_WIDTH_IN_BYTES*4 < 256
    cp SCREEN_WIDTH_IN_BYTES*4
    jr nc, max_length_of_sentence_in_one_line_limit_reached
ELSE
    jr c, max_length_of_sentence_in_one_line_limit_reached
ENDIF
    djnz max_length_of_sentence_in_one_line_loop
IF IS_6128 == 1
    SET_128K_PAGE_4000_FROM_POP
ENDIF
    ld a, ixh
    ret
max_length_of_sentence_in_one_line_limit_reached:
IF IS_6128 == 1
    SET_128K_PAGE_4000_FROM_POP
ENDIF
    ld a, ixl
    ret


; ------------------------------------------------
; Calculates the width of a sentence in bytes when rendered to screen in mode 1
; input:
; - hl: sentence
; output:
; - a: width in pixels
sentence_width_in_bytes:
IF IS_6128 == 1
    SET_128K_PAGE_4000_PUSHING_PREVIOUS FONT_DATA_PAGE
ENDIF
    ld b, (hl)  ; get the sentence length
    inc hl
    ld a, 3  ; pixels drawn    (we start by 3, since at the end we will do /4, and we want the "ceiling" of the division)
sentence_width_in_bytes_loop:
    ld d, 0
    ld e, (hl)
    push hl
        ld hl, font
        add hl, de
        add hl, de
        add hl, de  ; index of the letter is a*3
        add a, (hl)  ; letter width in pixels
    pop hl
    inc hl  
    djnz sentence_width_in_bytes_loop
    srl a
    srl a  ; a = number of bytes drawn
    ld c, a
IF IS_6128 == 1
    SET_128K_PAGE_4000_FROM_POP
ENDIF
    ld a, c
    ret


; ------------------------------------------------
; Draws a sentence to video memory (mode 1)
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - de: video memory address
; - iyl: color  #08: 1, #80: 2, #88: 3
; Returns:
; - ixl: width of the text in bytes drawn
draw_sentence:
IF IS_6128 == 1
    SET_128K_PAGE_4000_PUSHING_PREVIOUS FONT_DATA_PAGE
ENDIF
    ld b, (hl)   ; get the sentence length
    inc hl
    ld c, 0  ; start in pixel 0
    ld ixl, 1    ; pixels drawn
draw_sentence_loop:
    push bc
    push hl
    push de
        ld a, (hl)
        call draw_font_character
    pop de
    pop hl
    pop bc

    ; next character
    inc hl  

    ; move the position on the screen to the next character:
    add a, c  ; "a" contains the font width
draw_sentence_loop_de_increase_loop:
    cp 4
    jr c, draw_sentence_loop_no_de_increase
    sub 4
    inc de
    inc ixl
    jr draw_sentence_loop_de_increase_loop
draw_sentence_loop_no_de_increase:
    ld c, a
    djnz draw_sentence_loop
IF IS_6128 == 1
    SET_128K_PAGE_4000_FROM_POP
ENDIF
    ret
    

; ------------------------------------------------
; Draws a character to video memory (mode 1)
; Arguments:
; - a: character to draw
; - de: video memory address to draw
; - c: pixel offset (to determine whether we start in pixel 0, 1, 2 or 3 in the current memory address)
; - iyl: color  #08: 1, #80: 2, #88: 3
draw_font_character:
    ; get the pointer to the character:
    push de
        ; for variable size fonts:
        ld d,0
        ld e,a
        ld hl, font
        add hl, de
        add hl, de
        add hl, de    ; index of the letter is a*3
    pop de

    ; set up the bit masks:
    ld a, c
    or a
    ld a, iyl  ; mask for pixel 0
    ld ixh, #77  ; and mask
    jr z, draw_font_character_mask_calculated
    ld b, c
    ld c, ixh
draw_font_character_mask_loop:
    rrca
    rrc c
    djnz draw_font_character_mask_loop
    ld ixh, c
draw_font_character_mask_calculated:
    ld c, a

    ; draw it:
    ld a, (hl)
    ld b, a  ; we need it both in a and b
    push af

draw_font_character_x_loop:
        push bc
        push de
            inc hl
            ld a,(hl)
            ld b,8
draw_font_character_y_loop:
            push af
                and #01
                jr z,draw_font_character_y_loop_no_pixel

                ld a,(de)
                and ixh
                or c
                ld (de),a   ; draw the pixel of the font

draw_font_character_y_loop_no_pixel:
                ; move de one pixel down
                ld a,d
                add a,#08
                ld d,a
                ; NOTE: commenting these lines out assumes that the y coordinate is a multiple of 8!
;                 sub #C0
;                   jr nc, draw_font_character_no_de_correction_needed
;                    push bc
;                    ld bc, #c000 + SCREEN_WIDTH_IN_BYTES
;                    ex de,hl
;                    add hl,bc
;                    ex de,hl
;                pop bc
;draw_font_character_no_de_correction_needed:
            pop af
            srl a   ; a has one column of the character, encoded as one pixel per byte, so we move to the next pixel
            djnz draw_font_character_y_loop
        pop de
        pop bc
        rrc c   ; we rotate the mask
        ld a,ixh
        rrca
        ld ixh,a
        ; ld a,c
        ; cp iyh
        cp #77
        jr nz,draw_font_character_x_loop_no_de_increase
        ld c,iyl
        inc de
draw_font_character_x_loop_no_de_increase:
        djnz draw_font_character_x_loop
    pop af
    ret


; ------------------------------------------------
; Same as "draw_sentence", but it splits the text into multiple lines
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - de: video memory address
; - iyl: color  #08: 1, #80: 2, #88: 3
draw_multi_line_sentence:
    push hl
    push de
        call max_length_of_sentence_in_one_line
    pop de
    pop hl    
    ; limit the sentence to that width and rraw it:
    ld c, (hl)  ; we save the old length
    ld (hl), a
    push bc
    push hl
    push de
        call draw_sentence
    pop hl
    ld bc, SCREEN_WIDTH_IN_BYTES
    add hl, bc
    ex de, hl    ; next line
    pop hl
    pop bc
    ld a, c
    sub (hl)  ; length left
    ret z  ; if we have drawn the whole sentence, we are done
    ld c, a
    push bc
        ld b, 0
        ld c, (hl)
        add hl, bc   ; we advance hl to the remainder of the sentence
    pop bc
    ld (hl), c
    jr draw_multi_line_sentence



; ------------------------------------------------
; calculates the video mem pointer of the next line to be drawn via scroll in "hl"
next_scroll_line_ptr:
    ld hl, text_scrolling_next_row
    ld a, (hl)

    ; calculate the video memory address to render:
    ld hl, VIDEO_MEMORY
    ld de, SCREEN_WIDTH_IN_BYTES
    or a
    jr z,next_scroll_line_ptr_loop_done
next_scroll_line_ptr_loop:
    add hl, de
    dec a
    jr nz, next_scroll_line_ptr_loop
next_scroll_line_ptr_loop_done:
    ret


; ------------------------------------------------
; Same as "draw_multi_line_sentence", but draws the text at the (text_scrolling_next_row) row. 
; If (text_scrolling_next_row) == N_REGULAR_TEXT_ROWS+1, then it scrolls the whole text area up one line at the start
; It increments (text_scrolling_next_row) after each line drawn (potentially triggering more scroll)
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - iyl: color  #08: 1, #80: 2, #88: 3
draw_multi_line_sentence_scrolling:
    push hl
        ld hl, text_scrolling_next_row
        ld a, (hl)
        cp N_REGULAR_TEXT_ROWS+1
        jr nz, draw_multi_line_sentence_scrolling_no_scroll
        exx
            call scroll_text_area_up
        exx
draw_multi_line_sentence_scrolling_no_scroll:
        call next_scroll_line_ptr
        ex de, hl
        ld hl, text_scrolling_next_row
        inc (hl)
    pop hl
    push hl
    push de
        call max_length_of_sentence_in_one_line
    pop de
    pop hl    

    ; limit the sentence to that width and draw it:
    ld c, (hl)  ; we save the old length
    ld (hl), a
    push bc
    push hl
    push de
        call draw_sentence
    pop de
    pop hl
    pop bc
    ld a, c
    sub (hl)  ; length left
    ret z  ; if we have drawn the whole sentence, we are done
    ld c, a
    push bc
        ld b, 0
        ld c, (hl)
        add hl, bc  ; we advance hl to the remainder of the sentence
    pop bc
    ld (hl), c
    jr draw_multi_line_sentence_scrolling

