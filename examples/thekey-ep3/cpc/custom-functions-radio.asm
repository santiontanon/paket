;-----------------------------------------------
custom_radio_arrow_right:
    ld c, 1
    ld a, (game_state_variable_radio_current_dial)
    or a
    jr z, custom_radio_arrow_dial1
custom_radio_arrow_dial2:
    ld a, (game_state_variable_radio_dial2_position)
    add a, c
    cp 24
    jr nc, custom_radio_arrow_error
    ld (game_state_variable_radio_dial2_position), a
    jr custom_radio_arrow_update_dial_positions_and_draw

custom_radio_arrow_dial1:
    ld a, (game_state_variable_radio_dial1_position)
    add a, c
    cp 38
    jr nc, custom_radio_arrow_error
    ld (game_state_variable_radio_dial1_position), a
    jr custom_radio_arrow_update_dial_positions_and_draw


;-----------------------------------------------
custom_radio_arrow_left:
    ld c, -1
    ld a, (game_state_variable_radio_current_dial)
    or a
    jr z, custom_radio_arrow_dial1
    jr custom_radio_arrow_dial2


custom_radio_arrow_error:
    ld hl, sfx_sfx_error_afx
    jp play_SFX_with_high_priority


;-----------------------------------------------
custom_radio_arrow_update_dial_positions_and_draw:
    ; sfx:
    ld hl, sfx_sfx_btn_afx
    call play_SFX_with_high_priority
    
    call custom_radio_arrow_update_dial_positions
    jp draw_room


;-----------------------------------------------
DIAL1_OBJECT_ID: equ 15
DIAL2_OBJECT_ID: equ 16


custom_radio_arrow_update_dial_positions:
    ; dial positions can vary from 26 -> 60
    ; dial 1: goes from 0 - 19: 26 + (dial1)*2, but if (dial1)>=10, -= 2
    ; dial 2: goes from 0 - 23: 32 + (dial2)/2

    ld a, DIAL1_OBJECT_ID
    call find_room_object_ptr_by_id
    ld a, (game_state_variable_radio_dial1_position)
    ; add a, a
    add a, 26
    cp 44
    jr c, custom_radio_arrow_update_dial_positions_continue1
    add a, -2
custom_radio_arrow_update_dial_positions_continue1:
    ld (ix + OBJECT_STRUCT_X), a
    add a, 2
    ld (ix + OBJECT_STRUCT_X2), a

    ld a, DIAL2_OBJECT_ID
    call find_room_object_ptr_by_id
    ld a, (game_state_variable_radio_dial2_position)
    and #fe
    add a, 32
    ld (ix + OBJECT_STRUCT_X), a
    add a, 2
    ld (ix + OBJECT_STRUCT_X2), a
    ret


;-----------------------------------------------
RADIO_COLOR_WHITE_MASK: equ #54
custom_radio_draw_numbers:
    ld a, (game_state_variable_radio_dial1_position)
    ld c, a
    add a, a
    add a, a
    add a, c  ; a *= 5
    sub 90
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*12 + 10*4 + 1 + #1800
    ld (hl), 0  ; remove the negative sign
    jr nc, custom_radio_draw_numbers_positive
    ; dial 1 is negative:
    ld (hl), RADIO_COLOR_WHITE_MASK + RADIO_COLOR_WHITE_MASK*2  ; add the negative sign
    neg
custom_radio_draw_numbers_positive:
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*12 + 10*4 + 2 + #0800
    call custom_radio_draw_2_digit_number

    ld a, (game_state_variable_radio_dial2_position)
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*13 + 10*4 + 2 + #1800
    ; jr custom_radio_draw_2_digit_number


;-----------------------------------------------
; input:
; - a: number to draw
; - hl: ptr
custom_radio_draw_2_digit_number:
    ld (hl), a
    ; get tenths:
    ld b, 0
custom_radio_draw_2_digit_number_tenths_loop:
    cp 10
    jr c, custom_radio_draw_2_digit_number_tenths_loop_done
    sub 10
    inc b
    jr custom_radio_draw_2_digit_number_tenths_loop
custom_radio_draw_2_digit_number_tenths_loop_done:
    ld c, a  ; b = tenths, c = units
    ld a, b
    push bc
    push hl
        call custom_radio_draw_digit
    pop hl
    pop bc
    inc hl
    inc hl
    ld a, c
    jp custom_radio_draw_digit


;-----------------------------------------------
; input:
; - a: digit to draw
; - hl: ptr to draw
custom_radio_draw_digit:
    ld c, a
    add a, a
    add a, a
    add a, c  ; a = a * 5
    ld de, custom_radio_numbers_table
    ADD_DE_A
    ld b, 5
custom_radio_draw_digit_loop:
    ld a, (de)
    ld c, a
    ld a, 0  ; byte to draw
    rl c
    jr nc, custom_radio_draw_digit_loop_continue1
    or RADIO_COLOR_WHITE_MASK * 2
custom_radio_draw_digit_loop_continue1:
    rl c
    jr nc, custom_radio_draw_digit_loop_continue2
    or RADIO_COLOR_WHITE_MASK
custom_radio_draw_digit_loop_continue2:
    ld (hl), a

    inc hl
    ld a, 0  ; byte to draw
    rl c
    jr nc, custom_radio_draw_digit_loop_continue3
    or RADIO_COLOR_WHITE_MASK * 2
custom_radio_draw_digit_loop_continue3:
    rl c
    jr nc, custom_radio_draw_digit_loop_continue4
    or RADIO_COLOR_WHITE_MASK
custom_radio_draw_digit_loop_continue4:
    ld (hl), a
    push bc
        ld bc, #0800 - 1
        add hl, bc
    pop bc
    inc de
    djnz custom_radio_draw_digit_loop
    ret


;-----------------------------------------------
custom_radio_numbers_table:
    db #70, #50, #50, #50, #70  ; 0
    db #10, #10, #10, #10, #10  ; 1
    db #70, #10, #70, #40, #70  ; 2
    db #70, #10, #30, #10, #70  ; 3
    db #50, #50, #70, #10, #10  ; 4
    db #70, #40, #70, #10, #70  ; 5
    db #60, #40, #70, #50, #70  ; 6
    db #70, #10, #10, #10, #10  ; 7
    db #70, #50, #70, #50, #70  ; 8
    db #70, #50, #70, #10, #10  ; 9





