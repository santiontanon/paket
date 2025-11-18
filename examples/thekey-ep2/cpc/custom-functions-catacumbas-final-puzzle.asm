;-----------------------------------------------
catabumbas_final_puzzle__walked_numbers_first:
    db 1  ; hardcoded first number (the second one which is an upside down T)
catabumbas_final_puzzle__walked_numbers:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__selected_letters:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__next_letters:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__n_selected_letters:
    db 0


;-----------------------------------------------
catabumbas_final_puzzle__number_gfx:
    db #72  ; T
    db #27  ; upside down T
    db #74  ; r
    db #71  ; not
    db #47  ; L
    db #17  ; upsidedown not


catabumbas_final_puzzle__letter_gfx:
    db #07, #57 ,#50  ; A
    db #06, #44, #60  ; C
    db #64, #64, #60  ; E
    db #04, #44, #60  ; L
    db #07, #55, #70  ; O
    db #07, #57, #10  ; Q
    db #07, #22, #20  ; T
    db #05, #55, #70  ; U
    db #06, #24, #60  ; Z


PUZZLE_NUMBER_POSITION: macro n, x, y, letter1, letter2
    db n
    dw #c000 + (x / 2) + (y % 8) * #0800 + (FIRST_SCREEN_ROOM_ROW + (y / 8)) * SCREEN_WIDTH_IN_BYTES
    db letter1, letter2
    endm

CATACUMBAS_FINAL_PUZZLE__N_NUMBERS: equ 28
catabumbas_final_puzzle__number_positions:
    PUZZLE_NUMBER_POSITION 3, 10, 6, 0, 2
    PUZZLE_NUMBER_POSITION 1, 26, 5, 2, 1
    PUZZLE_NUMBER_POSITION 5, 42, 7, 1, 3

    PUZZLE_NUMBER_POSITION 1, 2, 12, 0, 4
    PUZZLE_NUMBER_POSITION 0, 34, 12, 1, 5

    PUZZLE_NUMBER_POSITION 1, 10, 17, 2, 4
    PUZZLE_NUMBER_POSITION 5, 26, 17, 2, 5
    PUZZLE_NUMBER_POSITION 0, 42, 17, 5, 3

    PUZZLE_NUMBER_POSITION 2, 18, 24, 2, 6
    PUZZLE_NUMBER_POSITION 3, 50, 24, 3, 7

    PUZZLE_NUMBER_POSITION 4, 10, 29, 4, 6
    PUZZLE_NUMBER_POSITION 1, 26, 29, 6, 5
    PUZZLE_NUMBER_POSITION 3, 42, 29, 5, 7

    PUZZLE_NUMBER_POSITION 4, 2, 36, 4, 8
    PUZZLE_NUMBER_POSITION 2, 34, 36, 5, 9

    PUZZLE_NUMBER_POSITION 0, 10, 41, 6, 8
    PUZZLE_NUMBER_POSITION 3, 26, 41, 6, 9
    PUZZLE_NUMBER_POSITION 5, 42, 41, 9, 7

    PUZZLE_NUMBER_POSITION 5, 18, 48, 6, 10
    PUZZLE_NUMBER_POSITION 0, 50, 48, 7, 11

    PUZZLE_NUMBER_POSITION 3, 10, 53, 8, 10
    PUZZLE_NUMBER_POSITION 0, 26, 53, 9, 10
    PUZZLE_NUMBER_POSITION 4, 42, 53, 9, 11

    PUZZLE_NUMBER_POSITION 3, 2, 60, 8, 12
    PUZZLE_NUMBER_POSITION 4, 34, 60, 9, 13

    PUZZLE_NUMBER_POSITION 1, 10, 66, 10, 12
    PUZZLE_NUMBER_POSITION 3, 26, 66, 10, 13
    PUZZLE_NUMBER_POSITION 2, 42, 66, 13, 11


PUZZLE_LETTER_POSITION: macro letter, x, y
    db letter
    dw #c000 + (x / 2) + (y % 8) * #0800 + (FIRST_SCREEN_ROOM_ROW + (y / 8)) * SCREEN_WIDTH_IN_BYTES
    endm


; ACELOQTUZ
CATACUMBAS_FINAL_PUZZLE__N_LETTERS: equ 14
catabumbas_final_puzzle__letter_positions:
    PUZZLE_LETTER_POSITION 3,  2,  4  ; L
    PUZZLE_LETTER_POSITION 4, 34,  4  ; O

    PUZZLE_LETTER_POSITION 0, 18, 10  ; A
    PUZZLE_LETTER_POSITION 1, 50, 10  ; C

    PUZZLE_LETTER_POSITION 6,  2, 22  ; T
    PUZZLE_LETTER_POSITION 5, 34, 22  ; Q

    PUZZLE_LETTER_POSITION 7, 18, 34  ; U
    PUZZLE_LETTER_POSITION 3, 50, 34  ; L

    PUZZLE_LETTER_POSITION 2,  2, 46  ; E
    PUZZLE_LETTER_POSITION 0, 34, 46  ; A

    PUZZLE_LETTER_POSITION 6, 18, 58  ; T
    PUZZLE_LETTER_POSITION 3, 50, 58  ; L

    PUZZLE_LETTER_POSITION 5,  2, 65  ; Q
    PUZZLE_LETTER_POSITION 8, 34, 65  ; Z


PUZZLE_SCREEN_POSITION: macro x, y
    dw #c000 + (x / 2) + (y % 8) * #0800 + (FIRST_SCREEN_ROOM_ROW + (y / 8)) * SCREEN_WIDTH_IN_BYTES
    endm

catabumbas_final_puzzle__walked_numbers_positions:
    PUZZLE_SCREEN_POSITION 66, 18
    PUZZLE_SCREEN_POSITION 66, 21
    PUZZLE_SCREEN_POSITION 74, 18
    PUZZLE_SCREEN_POSITION 74, 21
    PUZZLE_SCREEN_POSITION 82, 18
    PUZZLE_SCREEN_POSITION 82, 21
    PUZZLE_SCREEN_POSITION 90, 18
    PUZZLE_SCREEN_POSITION 90, 21
    PUZZLE_SCREEN_POSITION 98, 18
    PUZZLE_SCREEN_POSITION 98, 21
    PUZZLE_SCREEN_POSITION 106, 18
    PUZZLE_SCREEN_POSITION 106, 21


; AEGILNRU
catabumbas_final_puzzle__expected_letter_states:
    db OBJECT_STATE_NUMBER + 0 * 16  ; A
    db OBJECT_STATE_NUMBER + 7 * 16  ; U
    db OBJECT_STATE_NUMBER + 6 * 16  ; R
    db OBJECT_STATE_NUMBER + 3 * 16  ; I
    db OBJECT_STATE_NUMBER + 2 * 16  ; G
    db OBJECT_STATE_NUMBER + 0 * 16  ; A


;-----------------------------------------------
catabumbas_final_puzzle__reset:
    ; init variables:
    ld a, #ff
    ld hl, catabumbas_final_puzzle__walked_numbers
    ld bc, 11 + 12 + 10
    call clear_memory_to_a
    xor a
    ld (catabumbas_final_puzzle__n_selected_letters), a

catabumbas_final_puzzle__redraw:
    call clear_pointer
catabumbas_final_puzzle__room_redraw:
    ; redraw:
    call catabumbas_final_puzzle__draw_numbers
    call catabumbas_final_puzzle__draw_letters
    call catacumbas_final_puzzle__draw_walked_numbers
;     call calculate_pointer_video_mem_address
    call save_pointer_background
    ret


;-----------------------------------------------
catabumbas_final_puzzle__press_letter
    ld a, (iy + OBJECT_STRUCT_ID)
    sub 3
    ld c, a  ; c = letter number
    ld a, (catabumbas_final_puzzle__n_selected_letters)
    ; if it's the first letter, any letter is allowed:
    or a
    jr z, catabumbas_final_puzzle__press_letter_first
    ; otherwise, we only allow one among the "next letters":
    ld a, c
    call catabumbas_final_puzzle__is_next_letter
    jr z, catabumbas_final_puzzle__press_letter_allowed
    ; failed:
    ld hl, sfx_sfx_error_afx
    jp play_SFX_with_high_priority

catabumbas_final_puzzle__press_letter_first:
catabumbas_final_puzzle__press_letter_allowed:
    ; increment n letters:
    ld hl, catabumbas_final_puzzle__n_selected_letters
    ld a, (hl)
    inc (hl)

    ; mark selected letter:
    ld hl, catabumbas_final_puzzle__selected_letters
    ADD_HL_A
    ld (hl), c  ; letter we just selected

    ; mark next letters, and last added number:
    push bc
        ld a, #ff
        ld hl, catabumbas_final_puzzle__next_letters
        ld bc, 10
        call clear_memory_to_a
    pop bc
    
    ld hl, catabumbas_final_puzzle__number_positions + 3  ; + 3, so that we point to the linked letters
    ld b, CATACUMBAS_FINAL_PUZZLE__N_NUMBERS
catabumbas_final_puzzle__press_letter_mark_next_loop:
    ld a, (hl)
    inc hl
    cp c
    jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_match_1st
    ld a, (hl)
    cp c
    jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_match_2nd
catabumbas_final_puzzle__press_letter_mark_next_loop_continue:
    inc hl
    inc hl
    inc hl
    inc hl
    djnz catabumbas_final_puzzle__press_letter_mark_next_loop
    ; success sfx:
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority
    jp catabumbas_final_puzzle__redraw


catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number:
    push hl
        push bc
            ; get the two letters this number connects:
            ld c, (hl)
            dec hl
            ld b, (hl)
            ; check if it's the last two we selected:

            ld a, (catabumbas_final_puzzle__n_selected_letters)
            ld hl, catabumbas_final_puzzle__selected_letters - 2
            ADD_HL_A
            ld a, (hl)
            cp c
            jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match1
            cp b
            jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match1
catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_no_match:
        pop bc
catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_no_match_no_pop:
    pop hl
    jp catabumbas_final_puzzle__press_letter_mark_next_loop_continue
catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match1:
            inc hl
            ld a, (hl)
            cp c
            jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match2
            cp b
            jr z, catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match2
            jp catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_no_match
catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_match2:
            ; add the number:
            ld hl, catabumbas_final_puzzle__walked_numbers - 1
catabumbas_final_puzzle__press_letter_mark_next_loop_add_number_loop:
            inc hl
            ld a, (hl)
            inc a  ; try to find an empty spot
            jr nz, catabumbas_final_puzzle__press_letter_mark_next_loop_add_number_loop
            ld a, CATACUMBAS_FINAL_PUZZLE__N_NUMBERS
        pop bc
        sub b
        ld (hl), a
        jp catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number_no_match_no_pop


catabumbas_final_puzzle__press_letter_mark_next_loop_match_1st:
    push bc
        ld c, (hl)
        call catabumbas_final_puzzle__mark_letter_as_next
    pop bc
    jp catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number

catabumbas_final_puzzle__press_letter_mark_next_loop_match_2nd:
    push bc
        dec hl
        ld c, (hl)
        call catabumbas_final_puzzle__mark_letter_as_next
        inc hl
    pop bc
    jp catabumbas_final_puzzle__press_letter_mark_next_loop_maybe_add_number


;-----------------------------------------------
catabumbas_final_puzzle__up_arrow
    ld a, (iy + OBJECT_STRUCT_ID)
    sub 31
    srl a
    add a, 43  ; a = ID of the letter to change
    call find_room_object_ptr_by_id
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    cp OBJECT_STATE_NUMBER + 7 * 16
    jr z, catabumbas_final_puzzle__up_arrow_reset
    add a, 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    jr catabumbas_final_puzzle__up_arrow_done
catabumbas_final_puzzle__up_arrow_reset:
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), OBJECT_STATE_NUMBER
    jr catabumbas_final_puzzle__up_arrow_done


;-----------------------------------------------
catabumbas_final_puzzle__down_arrow
    ld a, (iy + OBJECT_STRUCT_ID)
    sub 31
    srl a
    add a, 43  ; a = ID of the letter to change
    call find_room_object_ptr_by_id
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    cp OBJECT_STATE_NUMBER
    jr z, catabumbas_final_puzzle__down_arrow_reset
    add a, -16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    jr catabumbas_final_puzzle__down_arrow_done
catabumbas_final_puzzle__down_arrow_reset:
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), OBJECT_STATE_NUMBER + 7 * 16
catabumbas_final_puzzle__up_arrow_done:
catabumbas_final_puzzle__down_arrow_done:
    ld a, (ix + OBJECT_STRUCT_ID)
    sub 43
    add a, a
    add a, a
    ld hl, dirty_column_buffer + 8*4
    ADD_HL_A
    ld (hl), 1
    inc hl
    ld (hl), 1
    inc hl
    ld (hl), 1
    inc hl
    ld (hl), 1
    inc hl
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority

    ; check for final solution:
    ld c, 43
    ld b, 6
    ld hl, catabumbas_final_puzzle__expected_letter_states
catabumbas_final_puzzle__check_solution_loop:
    push bc
    push hl
        ld a, c
        call find_room_object_ptr_by_id
    pop hl
    pop bc
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    cp (hl)
    ret nz
    inc hl
    inc c
    djnz catabumbas_final_puzzle__check_solution_loop
    ; puzzle solved!
    ld a, 2
    ld (game_state_variable_catacumbas_final_puzzle), a
    ret


;-----------------------------------------------
; input:
; - c: letter to mark as next
catabumbas_final_puzzle__mark_letter_as_next:
    push hl
    push bc
    push de
    push af
        ld a, (catabumbas_final_puzzle__n_selected_letters)
        cp 12  ; if we have 12 letters, do not mark any letters    
        jr z, catabumbas_final_puzzle__mark_letter_as_next_done
        ld a, c
        call catabumbas_final_puzzle__is_selected_letter
        jr z, catabumbas_final_puzzle__mark_letter_as_next_done
        call catabumbas_final_puzzle__is_next_letter
        jr z, catabumbas_final_puzzle__mark_letter_as_next_done
        ld hl, catabumbas_final_puzzle__next_letters - 1
catabumbas_final_puzzle__mark_letter_as_next_loop:
        inc hl
        ld a, (hl)
        inc a  ; try to find an empty spot
        jr nz, catabumbas_final_puzzle__mark_letter_as_next_loop
        ld (hl), c
catabumbas_final_puzzle__mark_letter_as_next_done:
    pop af
    pop de
    pop bc
    pop hl
    ret


;-----------------------------------------------
catabumbas_final_puzzle__draw_letters:
    ld hl, catabumbas_final_puzzle__letter_positions
    xor a
    ; blue   -> 00000001b
    ; d. blue  -> 00000100b
    ; purple -> 00000101b
    ; red    -> 00010000b
    ; orange -> 00010001b
    ; pink   -> 00010100b
    ; cyan   -> 00010101b
    ; black  -> 01000000b
    ; l blue -> 01000001b
    ; grey   -> 01010000b
    ; yellow -> 01010001b
    ; white  -> 01010100b
    ; l. green  -> 01010101b
catabumbas_final_puzzle__draw_letters_loop:
    push af
        push hl
            ld c, 00010001b  ; orange -> 00010001b
            call catabumbas_final_puzzle__is_selected_letter
            jr z, catabumbas_final_puzzle__draw_letters_loop_continue
            ld c, 01010001b  ; yellow -> 01010001b
            call catabumbas_final_puzzle__is_next_letter
            jr z, catabumbas_final_puzzle__draw_letters_loop_continue
            ld c, 01000001b  ; light blue -> 01000001b
catabumbas_final_puzzle__draw_letters_loop_continue:
        pop hl
        call catabumbas_final_puzzle__draw_letter  ; increments hl by 5
    pop af
    inc a
    cp CATACUMBAS_FINAL_PUZZLE__N_LETTERS
    jr nz, catabumbas_final_puzzle__draw_letters_loop
    ret


;-----------------------------------------------
; input:
; - a: letter number
catabumbas_final_puzzle__is_selected_letter:
    ld hl, catabumbas_final_puzzle__selected_letters
    ld b, 12
catabumbas_final_puzzle__is_selected_letter_loop:    
    cp (hl)
    ret z
    inc hl
    djnz catabumbas_final_puzzle__is_selected_letter_loop
    cp #ff  ; for "nz"
    ret


;-----------------------------------------------
; input:
; - a: letter number
catabumbas_final_puzzle__is_next_letter:
    ld hl, catabumbas_final_puzzle__next_letters
    ld b, 10
    jr catabumbas_final_puzzle__is_selected_letter_loop


;-----------------------------------------------
; input:
; - a: number
catabumbas_final_puzzle__is_walked_number:
    ld hl, catabumbas_final_puzzle__walked_numbers
    ld b, 11
    jr catabumbas_final_puzzle__is_selected_letter_loop


;-----------------------------------------------
catabumbas_final_puzzle__draw_numbers:
    ld hl, catabumbas_final_puzzle__number_positions
    xor a
catabumbas_final_puzzle__draw_numbers_loop:
    push af
        ld c, 01000101b  ; dark yellow -> 01000101b
        ; Check if we have already walked this number:
        push hl
            call catabumbas_final_puzzle__is_walked_number
        pop hl
        jr nz, catabumbas_final_puzzle__draw_numbers_loop_continue
        ld c, 00010101b  ; cyan -> 00010101b
catabumbas_final_puzzle__draw_numbers_loop_continue:
        call catabumbas_final_puzzle__draw_number  ; increments hl by 5
    pop af
    inc a
    cp CATACUMBAS_FINAL_PUZZLE__N_NUMBERS
    jr nz, catabumbas_final_puzzle__draw_numbers_loop
    ret


;-----------------------------------------------
catacumbas_final_puzzle__draw_walked_numbers:
    ld hl, catabumbas_final_puzzle__walked_numbers_first
    ld iy, catabumbas_final_puzzle__walked_numbers_positions
    ld b, 12
    ld c, 00010101b  ; cyan -> 00010101b
    ld ixl, 0  ; background color for pixels
catacumbas_final_puzzle__draw_walked_numbers_loop:
    ld a, (hl)  ; selected number
    inc hl
    cp #ff
    push hl
    push bc
        jr nz, catacumbas_final_puzzle__draw_walked_numbers_not_empty
        ; empty image, to delete previous numbers:
        xor a
        ld de, general_buffer
        ld (de), a
        inc de
        ld (de), a
        inc de
        ld (de), a
        inc de
        ld (de), a
        jr catacumbas_final_puzzle__draw_walked_numbers_loop2_done
catacumbas_final_puzzle__draw_walked_numbers_not_empty:
        ; draw number:
        ld hl, catabumbas_final_puzzle__number_positions
        ld b, a
        add a, a
        add a, a
        add a, b
        ADD_HL_A
        ld a, (hl)

        ld hl, catabumbas_final_puzzle__number_gfx
        ADD_HL_A

        ; synthesize the gfx in "general_buffer":
        ld b, (hl)
        ld de, general_buffer
        ld a, 4
catacumbas_final_puzzle__draw_walked_numbers_loop2:
        push af
            call catabumbas_final_puzzle__synthesize_byte
            ld (de), a
            inc de
        pop af
        dec a
        jr nz, catacumbas_final_puzzle__draw_walked_numbers_loop2
catacumbas_final_puzzle__draw_walked_numbers_loop2_done:
        ld e, (iy)
        ld d, (iy + 1)
        ld hl, general_buffer
        ld bc, #0202
        call draw_sprite_variable_size_to_screen_no_transparency
        inc iy
        inc iy
    pop bc
    pop hl
    djnz catacumbas_final_puzzle__draw_walked_numbers_loop
    ret


;-----------------------------------------------
; input:
; - hl: ptr to letter to draw
; - c: target color
catabumbas_final_puzzle__draw_letter:
    ld ixl, 00001100b  ; background color for pixels
    ld d, 0
    ld e, (hl)
    inc hl
    push hl
        ld hl, catabumbas_final_puzzle__letter_gfx
        add hl, de
        add hl, de
        add hl, de
        ; synthesize the gfx in "general_buffer":
        ld de, general_buffer
        ld a, 3
catabumbas_final_puzzle__draw_letter_loop2:
        push af
            ld b, (hl)
            ld a, 4
catabumbas_final_puzzle__draw_letter_loop:
            push af
                call catabumbas_final_puzzle__synthesize_byte
                ld (de), a
                inc de
            pop af
            dec a
            jr nz, catabumbas_final_puzzle__draw_letter_loop
        pop af
        inc hl
        dec a
        jr nz, catabumbas_final_puzzle__draw_letter_loop2
    pop hl
    ld e, (hl)
    inc hl
    ld d, (hl)
    inc hl
    push hl
        ld hl, general_buffer
        ld bc, #0502
        call draw_sprite_variable_size_to_screen_no_transparency
    pop hl
    ret


;-----------------------------------------------
; input:
; - hl: ptr to number to draw
; - c: target color
catabumbas_final_puzzle__draw_number:
    ld ixl, 0  ; background color for pixels
    ld d, 0
    ld e, (hl)
    inc hl
    push hl
        ld hl, catabumbas_final_puzzle__number_gfx
        add hl, de
        ; synthesize the gfx in "general_buffer":
        ld de, general_buffer
        ld b, (hl)
        ld a, 4
catabumbas_final_puzzle__draw_number_loop:
        push af
            call catabumbas_final_puzzle__synthesize_byte
            ld (de), a
            inc de
        pop af
        dec a
        jr nz, catabumbas_final_puzzle__draw_number_loop
    pop hl
    ld e, (hl)
    inc hl
    ld d, (hl)
    inc hl
    push hl
        ld hl, general_buffer
        ld bc, #0202
        call draw_sprite_variable_size_to_screen_no_transparency
    pop hl
    inc hl  ; letter 1
    inc hl  ; letter 2
    ret


;-----------------------------------------------
; input:
; - b: the 2 most significant bits will be used to draw pixels
; - c: color to use to draw
; output:
; - a: byte to draw
catabumbas_final_puzzle__synthesize_byte:
        ld a, ixl  ; byte we will draw
        sla c
        rl b
        jr nc, catabumbas_final_puzzle__draw_number_skip1
        ; draw pixel:
        and 01010101b
        or c
catabumbas_final_puzzle__draw_number_skip1:
        srl c
        rl b
        jr nc, catabumbas_final_puzzle__draw_number_skip2
        ; draw pixel:
        and 10101010b
        or c
catabumbas_final_puzzle__draw_number_skip2:
        ret
