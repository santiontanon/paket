
custom_clock_puzzle_hour_ids:
    db 29, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30
custom_clock_puzzle_minute_ids:
    db 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28
custom_clock_puzzle_current_hour:
    db 12 - 0
custom_clock_puzzle_current_minute:
    db 12 - 3
custom_clock_puzzle_current_gear:
    db 1
custom_clock_puzzle_current_gear_used:
    db 0


;-----------------------------------------------
custom_clock_puzzle_init:
    ; set time at 12:15
    ; - turn white objects 29 (12:--) and 20 (--:15)
    ld hl,custom_clock_puzzle_current_hour
    ld (hl), 12 - 0
    inc hl
    ld (hl), 12 - 3  ; custom_clock_puzzle_current_minute
    inc hl
    ld (hl), 1  ; custom_clock_puzzle_current_gear
    inc hl
    ld (hl), 0  ; custom_clock_puzzle_current_gear_used

    call custom_clock_puzzle_set_number_colors
    ld a, 43
    call find_room_object_ptr_by_id
    ld (ix+OBJECT_STRUCT_STATE_DIRECTION), #26 ; 2 (front), 6 (closed)    
    ld a, 46
    call custom_set_block_puzzle_number_to_blue
    ld a, 44
    call find_room_object_ptr_by_id
    ld (ix+OBJECT_STRUCT_STATE_DIRECTION), #26 ; 2 (front), 6 (closed)    
    ld a, 47
    call custom_set_block_puzzle_number_to_blue
    jr custom_clock_puzzle_set_gear_1_no_disabling


;-----------------------------------------------
custom_clock_puzzle_set_number_colors:
    ld b,12
    ld a,(custom_clock_puzzle_current_hour)
    ld c,a
    ld hl,custom_clock_puzzle_hour_ids
    call custom_clock_puzzle_set_number_colors_one_circle
    ld b,12
    ld a,(custom_clock_puzzle_current_minute)
    ld c,a
    ld hl,custom_clock_puzzle_minute_ids
custom_clock_puzzle_set_number_colors_one_circle:
    ld a,c
    cp b
    ld a,(hl)
    push bc
    push hl
        push af
            call z,custom_set_block_puzzle_number_to_white
        pop af
        call nz,custom_set_block_puzzle_number_to_blue
    pop hl
    pop bc
    inc hl
    djnz custom_clock_puzzle_set_number_colors_one_circle
    ret


;-----------------------------------------------
custom_disable_current_gear:
    ld a, 1
    ld (redraw_whole_room_signal), a
    ld a, (custom_clock_puzzle_current_gear)
    cp 6
    jr z, custom_disable_current_gear_6
    cp 2
    jr z, custom_disable_current_gear_2
custom_disable_current_gear_1:
    ld a, 42
custom_disable_current_gear_entry_point:
    push af
        call find_room_object_ptr_by_id
        ld a, (custom_clock_puzzle_current_gear_used)
        or a
        jr nz, custom_disable_current_gear_disable
        ld (ix+OBJECT_STRUCT_STATE_DIRECTION), #26 ; 2 (front), 6 (closed)    
        jr custom_disable_current_gear_continue
custom_disable_current_gear_disable:
        ld (ix+OBJECT_STRUCT_STATE_DIRECTION), #24 ; 2 (front), 4 (idle)    
custom_disable_current_gear_continue:
    pop af
    add a, 3
    jp custom_set_block_puzzle_number_to_blue
custom_disable_current_gear_2:
    ld a, 43
    jr custom_disable_current_gear_entry_point
custom_disable_current_gear_6:
    ld a, 44
    jr custom_disable_current_gear_entry_point


;-----------------------------------------------
custom_clock_puzzle_set_gear_1:
    ld a, (custom_clock_puzzle_current_gear)
    cp 1
    ret z
    call custom_disable_current_gear
custom_clock_puzzle_set_gear_1_no_disabling:
    ld a, 1
    ld (custom_clock_puzzle_current_gear), a
    ld a, 42
custom_clock_puzzle_set_gear_entry_point:
    push af
        call find_room_object_ptr_by_id
        ld (ix+OBJECT_STRUCT_STATE_DIRECTION), #25 ; 2 (front), 5 (open)    
    pop af
    add a, 3
    call custom_set_block_puzzle_number_to_white
    xor a
    ld (custom_clock_puzzle_current_gear_used), a
    ret


custom_clock_puzzle_set_gear_2:
    ld a, (custom_clock_puzzle_current_gear)
    cp 2
    ret z
    call custom_disable_current_gear
    ld a, 2
    ld (custom_clock_puzzle_current_gear),a
    ld a, 43
    jr custom_clock_puzzle_set_gear_entry_point


custom_clock_puzzle_set_gear_6:
    ld a, (custom_clock_puzzle_current_gear)
    cp 6
    ret z
    call custom_disable_current_gear
    ld a, 6
    ld (custom_clock_puzzle_current_gear),a
    ld a, 44
    jr custom_clock_puzzle_set_gear_entry_point


;-----------------------------------------------
custom_clock_puzzle_move_right:
    ld a, 1
    ld (custom_clock_puzzle_current_gear_used), a
    ld a, (custom_clock_puzzle_current_gear)
    ld c, a
    ld hl, custom_clock_puzzle_current_hour
    ld a, (hl)
    sub c
    jr z, custom_clock_puzzle_move_right_no_continue
    jr nc, custom_clock_puzzle_move_right_continue
custom_clock_puzzle_move_right_no_continue:
    add a, 12
custom_clock_puzzle_move_right_continue:
    ld (hl), a
    inc hl
    ld a, (hl)
    sub c
    jr z, custom_clock_puzzle_move_right_no_continue2
    jr nc, custom_clock_puzzle_move_right_continue2
custom_clock_puzzle_move_right_no_continue2:
    add a, 12
custom_clock_puzzle_move_right_continue2:
    ld (hl), a
    call custom_clock_puzzle_set_number_colors
    ret


;-----------------------------------------------
custom_clock_puzzle_move_left:
    ld a, 1
    ld (custom_clock_puzzle_current_gear_used), a
    ld a, (custom_clock_puzzle_current_gear)
    ld c, a
    ld hl, custom_clock_puzzle_current_hour
    ld a, (hl)
    add a, c
    cp 12
    jr z, custom_clock_puzzle_move_left_continue
    jr c, custom_clock_puzzle_move_left_continue
    sub 12
custom_clock_puzzle_move_left_continue:
    ld (hl), a
    inc hl
    ld a, (hl)
    add a, c
    cp 12
    jr z, custom_clock_puzzle_move_left_continue2
    jr c, custom_clock_puzzle_move_left_continue2
    sub 12
custom_clock_puzzle_move_left_continue2:
    ld (hl), a
    call custom_clock_puzzle_set_number_colors
    ; If "(game_state_variable_clock_puzzle_solved) == 1", it means we just pressed the last button, and it's
    ; time to check if we solved the puzzle:
    ld a, (game_state_variable_clock_puzzle_solved)
    or a
    ret z
    ; jp custom_clock_puzzle_check_solution


;-----------------------------------------------
custom_clock_puzzle_check_solution:
    ; (9:00)
    ld a, (custom_clock_puzzle_current_hour)
    cp 12 - 9
    ret nz
    ld a, (custom_clock_puzzle_current_minute)
    cp 12 - 0
    ret nz
    ld a, 2
    ld (game_state_variable_clock_puzzle_solved), a
    ret


;-----------------------------------------------
; input:
; - ix: object ptr
; output:
; - hl: pointer to the current animation frame
custom_get_object_current_frame_ptr:
    ld l, (ix + OBJECT_STRUCT_TYPE_PTR)
    ld h, (ix + OBJECT_STRUCT_TYPE_PTR + 1)
custom_get_object_current_frame_ptr_loop:
    ld a, (hl)  ; state-direction
    inc hl
    cp (ix + OBJECT_STRUCT_STATE_DIRECTION)
    jr nz, custom_get_object_current_frame_ptr_next_state
    ; we found the state!
    jp object_skip_to_current_animation_frame
custom_get_object_current_frame_ptr_next_state:
    ld c, (hl)
    inc hl
    ld b, (hl)
    inc hl
    add hl, bc
    jr custom_get_object_current_frame_ptr_loop


;-----------------------------------------------
; input:
; - a: object ID
custom_set_block_puzzle_number_to_white:
    ld d, #54  ; white
    jr custom_set_block_puzzle_number_to_color
custom_set_block_puzzle_number_to_blue:
    ld d, #41  ; blue
;     jr custom_set_block_puzzle_number_to_color
; custom_set_block_puzzle_number_to_red:
;     ld d, #44  ; red
custom_set_block_puzzle_number_to_color:
    push de
        call find_room_object_ptr_by_id
        call custom_get_object_current_frame_ptr    
    pop de
    inc hl  ; skip selection
    ld c, (hl)
    inc hl  ; skip size
    ld b, (hl)
    inc hl
    ; convert b*c bytes:
custom_set_block_puzzle_number_to_white_loop1:
    push bc
        ld b, c
custom_set_block_puzzle_number_to_white_loop2:
        ld a, (hl)
        ld e, 0
        and #55
        jr z, custom_set_block_puzzle_number_to_white_skip1
        ld e, d
custom_set_block_puzzle_number_to_white_skip1
        ld a, (hl)
        and #aa
        jr z, custom_set_block_puzzle_number_to_white_skip2
        ld a, e
        rl d
        or d
        rr d
        ld e, a
custom_set_block_puzzle_number_to_white_skip2
        ld (hl), e
        inc hl
        djnz custom_set_block_puzzle_number_to_white_loop2
    pop bc
    djnz custom_set_block_puzzle_number_to_white_loop1
    ret


