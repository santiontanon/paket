;-----------------------------------------------
custom_tangram_mural_positions:
    db 32, 16
    db 28, 32
    db 28, 48
    db 48, 56
    db 56, 32
    db 60, 16
    db 72, 40
    db 40, 40
    db 48, 16


;-----------------------------------------------
custom_tangram_mural_target_positions:
    db 32, 16
    db 28, 16
    db 28, 24
    db 32, 40
    db 40, 24
    db 44, 32
    db 56, 32
    db 64, 32
    db 64, 32


;-----------------------------------------------
custom_tangram_mural_piece_x_anchors:
    db 4
    db 0
    db 0
    db 4
    db 0
    db 8
    db 0
    db 4
    db 0


;-----------------------------------------------
custom_tangram_restore_piece_positions:
    ld e, 12
    ld b, 9
    ld hl, custom_tangram_mural_positions
custom_tangram_restore_piece_positions_loop:
    push bc
    push hl
    push de 
        ld a, e
        call find_room_object_ptr_by_id
    pop de
    pop hl
    pop bc
    ret nz
    push bc
        ld c, (hl)
        inc hl
        ld b, (hl)
        inc hl
        ld a, (ix + OBJECT_STRUCT_X2)
        sub (ix + OBJECT_STRUCT_X)
        ld (ix + OBJECT_STRUCT_X), c
        add a, c
        ld (ix + OBJECT_STRUCT_X2), a
        ld a, (ix + OBJECT_STRUCT_Y2)
        sub (ix + OBJECT_STRUCT_Y)
        ld (ix + OBJECT_STRUCT_Y), b
        add a, b
        ld (ix + OBJECT_STRUCT_Y2), a
    pop bc
    inc e
    djnz custom_tangram_restore_piece_positions_loop
    ret


;-----------------------------------------------
custom_tangram_mural_click:
    ; find which piece is selected:
    ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
    or a
    ret z
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
    ld de, OBJECT_STRUCT_SIZE
    ld b, a
custom_tangram_mural_click_loop:
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    cp 5 + 2 * 16 ; open (5), front (2)
    jr z, custom_tangram_mural_click_found
    add ix, de
    djnz custom_tangram_mural_click_loop
    ; no object selected
    ret

custom_tangram_mural_click_found:
    ; see if we have clicked within the board, or outside:
    ld a, (pointer_x)
    sub 24 - 2
    ret c  ; clicked left of the board
    cp 80 - 24
    ret nc  ; clicked rigth of the board
    add 24
    and #fc
    push af
        ; subtract the anchor x:
        ld hl, custom_tangram_mural_piece_x_anchors
        ld a, (ix + OBJECT_STRUCT_ID)
        sub 12
        ADD_HL_A
    pop af
    sub (hl)  ; subtract anchor x
    ld c, a  ; x coordinate
    ld a, (pointer_y)
    add a, 3
    and #f8
    ld b, a  ; y coordinate

    ld a, (ix + OBJECT_STRUCT_X2)
    sub (ix + OBJECT_STRUCT_X)
    ld (ix + OBJECT_STRUCT_X), c
    add a, c
    ld (ix + OBJECT_STRUCT_X2), a
    ld a, (ix + OBJECT_STRUCT_Y2)
    sub (ix + OBJECT_STRUCT_Y)
    ld (ix + OBJECT_STRUCT_Y), b
    add a, b
    ld (ix + OBJECT_STRUCT_Y2), a

    ld hl, custom_tangram_mural_positions
    ld a, (ix + OBJECT_STRUCT_ID)
    sub 12
    add a, a
    ADD_HL_A
    ld (hl), c
    inc hl
    ld (hl), b

    ; trigger room redraw:
    ld hl, sfx_sfx_btn_afx
    call play_SFX_with_high_priority
    call custom_tangram_solution_check
    jp draw_room


;-----------------------------------------------
custom_tangram_solution_check:
    ld b, 9*2
    ld hl, custom_tangram_mural_positions
    ld de, custom_tangram_mural_target_positions
custom_tangram_solution_check_loop:
    ld a, (de)
    cp (hl)
    ret nz
    inc de
    inc hl
    djnz custom_tangram_solution_check_loop
    ; puzzle solved!
    ld a, 1
    ld (game_state_variable_tangram_solved), a
    ret


