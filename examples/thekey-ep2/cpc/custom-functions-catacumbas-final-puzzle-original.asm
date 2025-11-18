CATACUMBAS_FINAL_PUZZLE__N_LETTERS: equ 28


;-----------------------------------------------
catabumbas_final_puzzle__last_added_number:
    db #ff
catabumbas_final_puzzle__walked_numbers:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__selected_letters:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__next_letters:
    db #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff, #ff
catabumbas_final_puzzle__n_selected_letters:
    db 0


;-----------------------------------------------
catabumbas_final_puzzle__number_gfx:
    db #e4  ; T
    db #4e  ; upside down T
    db #e8  ; r
    db #e2  ; not
    db #8e  ; L
    db #2e  ; upsidedown not

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


PUZZLE_NUMBER_POSITION: macro n, x, y
    db n
    dw #c000 + (x / 2) + (y % 8) * #0800 + (FIRST_SCREEN_ROOM_ROW + (y / 8)) * SCREEN_WIDTH_IN_BYTES
    endm

catabumbas_final_puzzle__number_positions:
    PUZZLE_NUMBER_POSITION 1, 2, 5
    PUZZLE_NUMBER_POSITION 2, 34, 5
    PUZZLE_NUMBER_POSITION 0, 18, 13
    PUZZLE_NUMBER_POSITION 3, 50, 13
    PUZZLE_NUMBER_POSITION 2, 2, 24
    PUZZLE_NUMBER_POSITION 0, 34, 24
    PUZZLE_NUMBER_POSITION 1, 18, 36
    PUZZLE_NUMBER_POSITION 4, 50, 36
    PUZZLE_NUMBER_POSITION 1, 2, 48
    PUZZLE_NUMBER_POSITION 3, 34, 48
    PUZZLE_NUMBER_POSITION 2, 18, 60
    PUZZLE_NUMBER_POSITION 5, 50, 60
    PUZZLE_NUMBER_POSITION 3, 2, 67
    PUZZLE_NUMBER_POSITION 3, 34, 67


PUZZLE_LETTER_POSITION: macro letter, x, y, number1, number2
    db letter
    dw #c000 + (x / 2) + (y % 8) * #0800 + (FIRST_SCREEN_ROOM_ROW + (y / 8)) * SCREEN_WIDTH_IN_BYTES
    db number1, number2
    endm

catabumbas_final_puzzle__letter_positions:
    PUZZLE_LETTER_POSITION 3, 10,  4,  0,  2  ; L
    PUZZLE_LETTER_POSITION 7, 26,  4,  2,  1  ; U
    PUZZLE_LETTER_POSITION 7, 42,  4,  1,  3  ; U

    PUZZLE_LETTER_POSITION 4,  2, 10,  0,  4  ; O
    PUZZLE_LETTER_POSITION 5, 34, 10,  1,  5  ; Q

    PUZZLE_LETTER_POSITION 6, 10, 16,  4,  2  ; T
    PUZZLE_LETTER_POSITION 5, 26, 16,  2,  5  ; Q
    PUZZLE_LETTER_POSITION 1, 42, 16,  5,  3  ; C

    PUZZLE_LETTER_POSITION 7, 18, 22,  2,  6  ; U
    PUZZLE_LETTER_POSITION 3, 50, 22,  3,  7  ; L

    PUZZLE_LETTER_POSITION 0, 10, 28,  4,  6  ; A
    PUZZLE_LETTER_POSITION 4, 26, 28,  6,  5  ; O
    PUZZLE_LETTER_POSITION 8, 42, 28,  5,  7  ; Z

    PUZZLE_LETTER_POSITION 8,  2, 34,  4,  8  ; Z
    PUZZLE_LETTER_POSITION 6, 34, 34,  5,  9  ; T

    PUZZLE_LETTER_POSITION 2, 10, 40,  8,  6  ; E
    PUZZLE_LETTER_POSITION 2, 26, 40,  6,  9  ; E
    PUZZLE_LETTER_POSITION 0, 42, 40,  9,  7  ; A

    PUZZLE_LETTER_POSITION 5, 18, 46,  6, 10  ; Q
    PUZZLE_LETTER_POSITION 4, 50, 46,  7, 11  ; O

    PUZZLE_LETTER_POSITION 6, 10, 52,  8, 10  ; T
    PUZZLE_LETTER_POSITION 8, 26, 52, 10,  9  ; Z
    PUZZLE_LETTER_POSITION 1, 42, 52,  9, 11  ; C

    PUZZLE_LETTER_POSITION 0,  2, 58,  8, 12  ; A
    PUZZLE_LETTER_POSITION 3, 34, 58,  9, 13  ; L

    PUZZLE_LETTER_POSITION 8, 10, 64, 12, 10  ; Z
    PUZZLE_LETTER_POSITION 6, 26, 64, 10, 13  ; T
    PUZZLE_LETTER_POSITION 3, 42, 64, 13, 11  ; L


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


catabumbas_final_puzzle__expected_letter_states:
    db OBJECT_STATE_NUMBER + 6 * 16  ; S
    db OBJECT_STATE_NUMBER + 1 * 16  ; E
    db OBJECT_STATE_NUMBER + 5 * 16  ; R
    db OBJECT_STATE_NUMBER + 4 * 16  ; P
    db OBJECT_STATE_NUMBER + 1 * 16  ; E
    db OBJECT_STATE_NUMBER + 6 * 16  ; S


;-----------------------------------------------
catabumbas_final_puzzle__reset:
    ; init variables:
    ld a, #ff
    ld hl, catabumbas_final_puzzle__last_added_number
    ld bc, 1 + 12 + 12 + 10
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
    
    ld hl, catabumbas_final_puzzle__letter_positions + 3  ; + 3, so that we point to the linked numbers
    ld a, c
    add a, a
    add a, a
    add a, c  ; a = c * 5
    ADD_HL_A

    push hl
        ld a, (catabumbas_final_puzzle__n_selected_letters)
        cp 2
        jr z, catabumbas_final_puzzle__press_letter_second
        dec a
        jr nz, catabumbas_final_puzzle__press_letter_not_first

        ; mark letters from both numbers as next:
        ld a, (hl)  ; number 1
        push hl
            call catabumbas_final_puzzle__mark_next_letters
        pop hl
        inc hl
        ld a, (hl)  ; number 2
        call catabumbas_final_puzzle__mark_next_letters
    pop hl
    jr catabumbas_final_puzzle__press_letter_skip_number_add
catabumbas_final_puzzle__press_letter_second:
        ; look for the number that the two last selected letters have in common, and add to walked numbers:
        ld a, (catabumbas_final_puzzle__selected_letters)
        ld c, a
        ld hl, catabumbas_final_puzzle__letter_positions + 3
        add a, a
        add a, a
        add a, c
        ADD_HL_A
        ld c, (hl)
        inc hl
        ld b, (hl)  ; c, b: numbers of the first letter
        ld a, (catabumbas_final_puzzle__selected_letters + 1)
        ld e, a
        ld hl, catabumbas_final_puzzle__letter_positions + 3
        add a, a
        add a, a
        add a, e
        ADD_HL_A
        ld e, (hl)
        inc hl
        ld d, (hl)  ; e, d: numbers of the second letter
        ld a, c
        cp e
        jr z, catabumbas_final_puzzle__press_letter_second_number_found
        cp d
        jr z, catabumbas_final_puzzle__press_letter_second_number_found
        ld a, b
catabumbas_final_puzzle__press_letter_second_number_found:
        ld (catabumbas_final_puzzle__walked_numbers), a
        ld (catabumbas_final_puzzle__last_added_number), a

catabumbas_final_puzzle__press_letter_not_first:
    pop hl  ; hl points to the numbers of the selected lettere

    ; add number to walked numbers:
    ld a, (hl)
    push hl
        ld hl, catabumbas_final_puzzle__last_added_number
        cp (hl)
    pop hl
    jr nz, catabumbas_final_puzzle__press_letter_add_number_found
    inc hl
    ld a, (hl)
catabumbas_final_puzzle__press_letter_add_number_found:
    ; add number "a":
    push af
        ld a, (catabumbas_final_puzzle__n_selected_letters)
        dec a
        ld hl, catabumbas_final_puzzle__walked_numbers
        ADD_HL_A
    pop af
    ld (hl), a
    ld (catabumbas_final_puzzle__last_added_number), a
    ld c, a
    ld a, (catabumbas_final_puzzle__n_selected_letters)
    cp 12  ; if we have 12 letters, do not mark any letters
    ld a, c
    call nz, catabumbas_final_puzzle__mark_next_letters

catabumbas_final_puzzle__press_letter_skip_number_add:
    ; success sfx:
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority

    jp catabumbas_final_puzzle__redraw


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
; - a: number for which we want to mark all the surounding letters 
catabumbas_final_puzzle__mark_next_letters:
    ld hl, catabumbas_final_puzzle__letter_positions + 3
    ld c, 0
    ld b, CATACUMBAS_FINAL_PUZZLE__N_LETTERS
    ld de, 5 - 1  ; -1, since we do an "inc hl"
catabumbas_final_puzzle__mark_next_letters_loop:
    cp (hl)
    call z, catabumbas_final_puzzle__mark_letter_as_next
    inc hl
    cp (hl)
    call z, catabumbas_final_puzzle__mark_letter_as_next
    add hl, de
    inc c
    djnz catabumbas_final_puzzle__mark_next_letters_loop
    ret


;-----------------------------------------------
; input:
; - c: letter to mark as next
catabumbas_final_puzzle__mark_letter_as_next:
    push hl
    push bc
    push de
    push af
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
    ; purple -> 00000101b
    ; cyan   -> 00010101b
    ; yellow -> 01010001b
    ; white  -> 01010100b
catabumbas_final_puzzle__draw_letters_loop:
    push af
        push hl
            ld c, 01010001b  ; bright yellow -> 01010001b
            call catabumbas_final_puzzle__is_selected_letter
            jr z, catabumbas_final_puzzle__draw_letters_loop_continue
            ; ld c, 01010100b  ; white -> 01010100b
            ld c, 00010101b  ; cyan -> 00010101b
            call catabumbas_final_puzzle__is_next_letter
            jr z, catabumbas_final_puzzle__draw_letters_loop_continue
            ; ld c, 00010101b  ; cyan -> 00010101b
            ld c, 00000001b  ; middle blue -> 00000001b
catabumbas_final_puzzle__draw_letters_loop_continue:
        pop hl
        call catabumbas_final_puzzle__draw_letter  ; increments hl by 5
    pop af
    inc a
    cp CATACUMBAS_FINAL_PUZZLE__N_LETTERS
    jr nz, catabumbas_final_puzzle__draw_letters_loop


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
; catabumbas_final_puzzle__is_walked_number:
;     ld hl, catabumbas_final_puzzle__walked_numbers
;     ld b, 12
;     jr catabumbas_final_puzzle__is_selected_letter_loop


;-----------------------------------------------
catabumbas_final_puzzle__draw_numbers:
    ld hl, catabumbas_final_puzzle__number_positions
    xor a
catabumbas_final_puzzle__draw_numbers_loop:
    push af
        ld b, a
        ld c, 01000101b  ; dark yellow ->  01000101b
        ; Check if it's the last number we added, and make it "red":
        ld a, (catabumbas_final_puzzle__last_added_number)
        cp b
        jr nz, catabumbas_final_puzzle__draw_numbers_loop_continue
        ld c, 00010000b  ; red ->  00010000b
catabumbas_final_puzzle__draw_numbers_loop_continue:
        call catabumbas_final_puzzle__draw_number  ; increments hl by 3
    pop af
    inc a
    cp 14  ; 14 numbers
    jr nz, catabumbas_final_puzzle__draw_numbers_loop
    ret


;-----------------------------------------------
catacumbas_final_puzzle__draw_walked_numbers:
    ld hl, catabumbas_final_puzzle__walked_numbers
    ld iy, catabumbas_final_puzzle__walked_numbers_positions
    ld b, 12
    ld c, 01000101b  ; dark yellow ->  01000101b
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
    inc hl  ; number1
    inc hl  ; number2
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
