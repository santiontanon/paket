;-----------------------------------------------
; Decompresses a specific text sentence from a text bank
; input:
; - de: target destination of decompression
; - c: bank #
; - a: text # within the bank
; output:
; - de: pointer to immediately after the text
get_text_from_bank:
    push af
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 TEXT_MEGAROM_PAGE
ENDIF
    pop af
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
            ld de, text_decompression_buffer  ; this is somewhere in the general buffer, in a safe spot
            call PAKET_UNPACK
        pop af
        ld hl, text_decompression_buffer
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
    ret


; ------------------------------------------------
; clears all the text area lines (except the action preview line)
clear_text_area:
    xor a
    ld hl, CHRTBL2 + 32 * 8
    ld bc, 32 * 8 * N_REGULAR_TEXT_ROWS
    jp FILVRM


; ------------------------------------------------
; clears the whole line where the command is being previewed
clear_command_preview_text_whole_line:
clear_command_preview_text:
    xor a
    ld (current_action_text_id), a    ; clear the id of the last action preview text drawn
    ld hl, CHRTBL2 + 8 * ((32 - SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE) / 2)
    ld bc, SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE * 8
    jp FILVRM


; ------------------------------------------------
clear_text_draw_buffer:
    ld hl, text_draw_buffer
    ld bc, SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE * 8
    jp clear_memory


;-----------------------------------------------
; Scrolls the whole text area (including the action preview line), one line up
scroll_text_area_up:
    ; ld a,1
    ; ld (scrolled_at_least_once),a
    
    ld hl,text_scrolling_next_row
    dec (hl)
    
    ; scroll one line up:
    ld hl,CLRTBL2+32*8
    ld de,general_buffer
    ld bc,32*8*N_REGULAR_TEXT_ROWS
    call LDIRMV
    ld hl,general_buffer
    ld de,CLRTBL2
    ld bc,32*8*N_REGULAR_TEXT_ROWS
    call fast_LDIRVM

    ld hl,CHRTBL2+32*8
    ld de,general_buffer
    ld bc,32*8*N_REGULAR_TEXT_ROWS
    call LDIRMV
    ld hl,general_buffer
    ld de,CHRTBL2
    ld bc,32*8*N_REGULAR_TEXT_ROWS
    call fast_LDIRVM

    ; clear the last line:
    xor a
    ld hl,CHRTBL2+(8*32*N_REGULAR_TEXT_ROWS)+8*((32-SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE)/2)
    ld bc,SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE*8
    jp FILVRM


; ------------------------------------------------
; - de: VRAM address where to start drawing
; - a: attribute (color)
render_text_draw_buffer_full_line:
    ld bc,SCREEN_WIDTH_IN_TILES * 2*8
render_text_draw_buffer_bc_bytes:
    ld hl,text_draw_buffer
    push af
    push bc
        push de
            call fast_LDIRVM
        pop hl
        ld bc,CLRTBL2 - CHRTBL2
        add hl,bc
    pop bc
    pop af
    jp FILVRM



; ------------------------------------------------
; Calculates the maximum length that can be rendered without overflowing a single line of text.
; Only "spaces" are considered as possible splits
; input:
; - hl: sentence
; output:
; - a: number of characters that can be drawn
max_length_of_sentence_in_one_line:
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 FONT_DATA_PAGE
ENDIF
    ld ixl, 0  ; best found so far
    ld ixh, 0  ; current character
    ld b, (hl)  ; get the sentence length
    inc hl
    xor a  ; pixels drawn
max_length_of_sentence_in_one_line_loop:
    ld d,0
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
        add hl, de   ; index of the letter is a*3
        add a, (hl)   ; letter width in pixels
    pop hl
    inc hl  ; does not modify the flags
IF SCREEN_WIDTH_IN_TILES*MSX_TILES_PER_ENGINE_TILE*8 < 256
    cp SCREEN_WIDTH_IN_TILES*MSX_TILES_PER_ENGINE_TILE*8
    jr nc, max_length_of_sentence_in_one_line_limit_reached
ELSE
    jr c, max_length_of_sentence_in_one_line_limit_reached
ENDIF
    djnz max_length_of_sentence_in_one_line_loop
    ld a, ixh
    ret
max_length_of_sentence_in_one_line_limit_reached:
    ld a, ixl
    ret


; ------------------------------------------------
; Same as "draw_sentence", but it splits the text into multiple lines
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - de: video memory address
; - iyl: color attribute
draw_multi_line_sentence:
    push hl
    push de
        call max_length_of_sentence_in_one_line
    pop de
    pop hl    
    ; limit the sentence to that width and rraw it:
    ld c, (hl)   ; we save the old length
    ld (hl), a
    push bc
    push hl
    push de
        call draw_sentence
    pop hl
    ld bc, 32*8
    add hl, bc
    ex de, hl  ; next line
    pop hl
    pop bc
    ld a, c
    sub (hl)  ; length left
    ret z  ; if we have drawn the whole sentence, we are done
    ld c, a
    push bc
        ld b, 0
        ld c,( hl)
        add hl, bc  ; we advance hl to the remainder of the sentence
    pop bc
    ld (hl), c
    jr draw_multi_line_sentence


; ------------------------------------------------
; calculates the video mem pointer of the next line to be drawn via scroll in "hl"
next_scroll_line_ptr:
    ; calculate the video memory address to render:
    ld hl, CHRTBL2+8*((32-SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE)/2)
next_scroll_line_ptr_entry_point:
    ld a, (text_scrolling_next_row)
    ld de, 8*32
    or a
    jr z,next_scroll_line_ptr_loop_done
next_scroll_line_ptr_loop:
    add hl, de
    dec a
    jr nz, next_scroll_line_ptr_loop
next_scroll_line_ptr_loop_done:
    ret


next_scroll_line_att_ptr:
    ld hl,CLRTBL2+8*((32-SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE)/2)
    jr next_scroll_line_ptr_entry_point


; ------------------------------------------------
; Same as "draw_multi_line_sentence", but draws the text at the (text_scrolling_next_row) row. 
; If (text_scrolling_next_row) == N_REGULAR_TEXT_ROWS+1, then it scrolls the whole text area up one line at the start
; It increments (text_scrolling_next_row) after each line drawn (potentially triggering more scroll)
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - iyl: color attribute
draw_multi_line_sentence_scrolling:
    push hl
        ld hl, text_scrolling_next_row
        ld a, (hl)
        cp N_REGULAR_TEXT_ROWS + 1
        jr nz,draw_multi_line_sentence_scrolling_no_scroll
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
    ld c, (hl)   ; we save the old length
    ld (hl), a
    push bc
    push hl
    push de
        call draw_sentence
    pop de
    pop hl
    pop bc
    ld a,c
    sub (hl)  ; length left
    ret z  ; if we have drawn the whole sentence, we are done
    ld c,a
    push bc
        ld b, 0
        ld c, (hl)
        add hl, bc   ; we advance hl to the remainder of the sentence
    pop bc
    ld (hl), c
    jr draw_multi_line_sentence_scrolling


; ------------------------------------------------
; Draws a sentence to video memory (mode 1)
; Arguments:
; - hl: sentence to draw (first byte is the length)
; - de: target VRAM address (to CHRTBL2)
; - iyl: color (attribute byte)
draw_sentence:
    ld bc, SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE*8  ; width to render
draw_sentence_bc_width:
    push bc
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 FONT_DATA_PAGE
ENDIF
    push de
    push iy
        push hl
            call clear_text_draw_buffer
        pop hl

        ld de, text_draw_buffer
        ld b, (hl)  ; get the sentence length
        inc hl
        ld c, 128  ; start in pixel 0
draw_sentence_loop:
        push bc
        push hl
            ld a, (hl)
            call draw_font_character
            ld a, c  ; we save the pixel mask
        pop hl
        pop bc
        ld c, a

        ; next character
        inc hl  
        djnz draw_sentence_loop
    pop iy
    pop de
    pop bc
    ld a, iyl
    jp render_text_draw_buffer_bc_bytes


; ------------------------------------------------
; Draws a character to video memory (mode 1)
; Arguments:
; - a: character to draw
; - de: memory address to draw
; - c: pixel offset (to determine whether we start in pixel 0, 1, 2, 3, 4, 5, 6, or 7 in the current tile)
;    - This is a a "mask": 1, 2, 4, 8, 16, 32, ...
draw_font_character:
    ; get the pointer to the character:
    push de
        ; for variable size fonts:
        ld d,0
        ld e,a
        ld hl,font
        add hl,de
        add hl,de
        add hl,de    ; index of the letter is a*3
    pop de

    ld b,(hl)    ; character size
    inc hl
draw_font_character_loop:
    push bc
        ld b,(hl)    ; column bitmap
        inc hl

        ; render one column of the character:
        ld ixl,8
        push de
draw_font_character_loop2:
            ld a,(de)
            sra b
            jr nc,draw_font_character_loop_no_pixel
            or c
            ld (de),a
draw_font_character_loop_no_pixel:
            inc de
            dec ixl
            jr nz,draw_font_character_loop2
        pop de
    pop bc
    rrc c
    jr nc,draw_font_character_loop_no_next_tile
    push hl
        ld hl,8
        add hl,de
        ex de,hl
    pop hl
draw_font_character_loop_no_next_tile:
    djnz draw_font_character_loop
    ret
