;-----------------------------------------------
custom_electrico_pieces:
    db 0, 0  ; straight H
    db 0, 0  ; straight V
    db 0, 0  ; corner NW
    db 0, 0  ; corner NE
    db 0, 0  ; corner SE
    db 0, 0  ; corner SW


;-----------------------------------------------
; stored by columns, to be easier to map to tiles
; - 0: do not touch
; - 1, 2: straight H, V
; - 3, 4, 5, 6: corner NW, NE, SE, SW
custom_electrico_state:
    db 0, 2, 3, 3, 2
    db 0, 6, 0, 2, 3
    db 1, 2, 1, 6, 5
    db 3, 4, 2, 6, 2
    db 6, 1, 0, 1, 2
    db 3, 2, 1, 5, 2
    db 0, 1, 2, 1, 5


;-----------------------------------------------
custom_electrico_fix_cable:
    ; get the piece types from the room tiles:
    ld de, custom_electrico_pieces
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 20
    ldi  ; straight H
    ldi
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 2
    ldi  ; straight V
    ldi
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 4
    ldi  ; corner NW
    ldi
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 32
    ldi  ; corner NE
    ldi
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 28
    ldi  ; corner SE
    ldi
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND + 26
    ldi  ; corner SW
    ldi

    ; change the cable to fixed:
    ld hl, custom_electrico_state+5
    ld (hl), 6

    call custom_electrico_init

    ; redraw:
    jp draw_room


;-----------------------------------------------
custom_electrico_init:
    ld hl, custom_electrico_state
    ld de, room_buffer + ROOM_STRUCT_BACKGROUND
    ld b, 5 * 7
custom_electrico_init_loop:
    ld a, (hl)
    inc hl
    or a
    jr z, custom_electrico_init_loop_skip
    push hl
        add a, a
        ld hl, custom_electrico_pieces - 2
        ADD_HL_A
        ld a, (hl)
        ld (de), a
        inc hl
        inc de
        ld a, (hl)
        ld (de), a
        dec de
    pop hl
custom_electrico_init_loop_skip:
    inc de
    inc de
    djnz custom_electrico_init_loop
    ret


;-----------------------------------------------
custom_electrico_click:
    ; get coordinate:
    ; - ((pointer_x) - 32) / 8
    ; - (pointer_y) / 16
    ld a, (pointer_x)
    sub 30
    ret c
    rrca
    rrca
    rrca
    and #1f
    cp 7
    ret nc
    ld c, a
    add a, a
    add a, a
    add a, c
    ld c, a  ; c = x * 5
    ld a, (pointer_y)
    add a, 3
    rrca
    rrca
    rrca
    rrca
    and #0f
    cp 5
    ret nc
    add a, c  ; a = y + x * 5
    ld hl, custom_electrico_state
    ADD_HL_A
    ld a, (hl)

    ; rotate the piece
    or a
    ret z  ; it's not a rotatable piece

    cp 3
    jr c, custom_electrico_click_straight
custom_electrico_click_corner:
    ; rotate: 3, 4, 5, 6
    inc a
    cp 7
    jr nz, custom_electrico_click_corner_continue
    ld a, 3
    jr custom_electrico_click_corner_continue
custom_electrico_click_straight:
    ; swap 1 <--> 2
    xor #03
custom_electrico_click_corner_continue:
    ld (hl), a

    call custom_electrico_init

    ; sfx:
    ld hl, sfx_sfx_btn_afx
    call play_SFX_with_high_priority

    ; redraw the corresponding columns:
    call draw_room

    ; check solution:
    call puzzle_electrico_check_solution
    ret nz

    ; puzzle solved!
    ld a, 2
    ld (game_state_variable_puzzle_electrico), a
    ret


;-----------------------------------------------
; output:
; - z: solved
; - nz: not solved
puzzle_electrico_check_solution:
    ld hl, custom_electrico_state + 5
    ld de, #0001
    ld c, OBJECT_DIRECTION_RIGHT
    call puzzle_electrico_check_solution_path
    ret nz
    ld hl, custom_electrico_state + 31
    ld de, #0106
    ld c, OBJECT_DIRECTION_DOWN
    jp puzzle_electrico_check_solution_path


;-----------------------------------------------
; Checks if the path that starts at 'hl' reaches a negative pole:
; - custom_electrico_state + 7 or
; - custom_electrico_state + 22
; input:
; - hl: ptr to 'custom_electrico_state' to a positive position
; - c: direction
; - de: coordinates (to check out of bounds)
; output:
; - z: solved
; - nz: not solved
puzzle_electrico_check_solution_path:
    ld a, e
    cp 7
    jr nc, puzzle_electrico_check_solution_path_out_of_bounds
    ld a, d
    cp 5
    jr c, puzzle_electrico_check_solution_path_continue
puzzle_electrico_check_solution_path_out_of_bounds:
    or 1  ; out of bounds
    ret
puzzle_electrico_check_solution_path_continue:
    ld a, (hl)
    or a
    jr z, puzzle_electrico_check_solution_path_pole
    dec a
    jr z, puzzle_electrico_check_solution_path_straight_h
    dec a
    jr z, puzzle_electrico_check_solution_path_straight_v
    dec a
    jr z, puzzle_electrico_check_solution_path_corner_nw
    dec a
    jr z, puzzle_electrico_check_solution_path_corner_ne
    dec a
    jr z, puzzle_electrico_check_solution_path_corner_se
puzzle_electrico_check_solution_path_corner_sw:
    ld a, c
    cp OBJECT_DIRECTION_RIGHT
    jr z, puzzle_electrico_check_solution_path_continue_s
    cp OBJECT_DIRECTION_UP
    jr z, puzzle_electrico_check_solution_path_continue_w
    ret  ; deadend
puzzle_electrico_check_solution_path_corner_nw:
    ld a, c
    cp OBJECT_DIRECTION_RIGHT
    jr z, puzzle_electrico_check_solution_path_continue_n
    cp OBJECT_DIRECTION_DOWN
    jr z, puzzle_electrico_check_solution_path_continue_w
    ret  ; deadend
puzzle_electrico_check_solution_path_corner_ne:
    ld a, c
    cp OBJECT_DIRECTION_LEFT
    jr z, puzzle_electrico_check_solution_path_continue_n
    cp OBJECT_DIRECTION_DOWN
    jr z, puzzle_electrico_check_solution_path_continue_e
    ret  ; deadend
puzzle_electrico_check_solution_path_corner_se:
    ld a, c
    cp OBJECT_DIRECTION_LEFT
    jr z, puzzle_electrico_check_solution_path_continue_s
    cp OBJECT_DIRECTION_UP
    jr z, puzzle_electrico_check_solution_path_continue_e
    ret  ; deadend
puzzle_electrico_check_solution_path_straight_h:
    ld a, c
    cp OBJECT_DIRECTION_RIGHT
    jr z, puzzle_electrico_check_solution_path_continue_e
    cp OBJECT_DIRECTION_LEFT
    jr z, puzzle_electrico_check_solution_path_continue_w
    ret  ; deadend
puzzle_electrico_check_solution_path_straight_v:
    ld a, c
    cp OBJECT_DIRECTION_DOWN
    jr z, puzzle_electrico_check_solution_path_continue_s
    cp OBJECT_DIRECTION_UP
    jr z, puzzle_electrico_check_solution_path_continue_n
    ret  ; deadend

puzzle_electrico_check_solution_path_continue_e:
    ld bc, 5
    add hl, bc
    inc e
    ld c, OBJECT_DIRECTION_RIGHT
    jr puzzle_electrico_check_solution_path
puzzle_electrico_check_solution_path_continue_w:
    ld bc, -5
    add hl, bc
    dec e
    ld c, OBJECT_DIRECTION_LEFT
    jr puzzle_electrico_check_solution_path
puzzle_electrico_check_solution_path_continue_n:
    dec hl
    ld c, OBJECT_DIRECTION_UP
    dec d
    jr puzzle_electrico_check_solution_path
puzzle_electrico_check_solution_path_continue_s:
    inc hl
    ld c, OBJECT_DIRECTION_DOWN
    inc d
    jr puzzle_electrico_check_solution_path

puzzle_electrico_check_solution_path_pole:
    ; check first pole (need to come from the left)
    ld a, c
    cp OBJECT_DIRECTION_RIGHT
    jr nz, puzzle_electrico_check_solution_path_pole_2
    or a
    push bc
    push hl
        ld bc, custom_electrico_state + 7  ; first negative pole
        sbc hl, bc
    pop hl
    pop bc
    ret z
puzzle_electrico_check_solution_path_pole_2:
    ; check first pole (need to come from above)
    ld a, c
    cp OBJECT_DIRECTION_DOWN
    ret nz
    or a
    push bc
    push hl
        ld bc, custom_electrico_state + 22  ; second negative pole
        sbc hl, bc
    pop hl
    pop bc
    ret
