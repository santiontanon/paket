;-----------------------------------------------
custom_caldera_puzzle_redraw:
    ld a, (game_state_variable_caldera_puzzle_water1)
    ld b, a
    ld e, 0
    or a
    call nz, custom_caldera_puzzle_redraw_loop_start

    ld a, (game_state_variable_caldera_puzzle_water2)
    ld b, a
    ld e, 1
    or a
    call nz, custom_caldera_puzzle_redraw_loop_start

    ld a, (game_state_variable_caldera_puzzle_water3)
    ld b, a
    ld e, 2
    or a
    call nz, custom_caldera_puzzle_redraw_loop_start
    ret

custom_caldera_puzzle_redraw_loop_start:
    ld d, 14
custom_caldera_puzzle_redraw_loop:
    push bc
    push de
        call custom_caldera_puzzle_redraw_draw_tile
    pop de
    pop bc
    dec d
    djnz custom_caldera_puzzle_redraw_loop
    ret


;-----------------------------------------------
; input:
; - e: x coordinate (in tiles)
; - d: y coordinate (in 4 pixel increments)
custom_caldera_puzzle_redraw_draw_tile:
    ; get pointer to start drawing:
    ld c, e
    ld b, d
    push bc
        srl b  ; divide 'b' by 2 to get the number of tiles
        ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_Y)
        add a, b  ; 7 tiles down from the top
        ld de, SCREEN_WIDTH_IN_BYTES
        call mul8  ; hl = de * a
    pop bc
    ld d, 0
    ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_X)
    add a, c
    add a, c
    add a, c
    add a, c
    ld e, a
    add hl, de

    bit 0, b
    jr z, custom_caldera_puzzle_redraw_draw_tile_continue
    ld de, #2000  ; 4 pixels down
    add hl, de
custom_caldera_puzzle_redraw_draw_tile_continue:
    ld de, VIDEO_MEMORY
    add hl, de    ; video mem address to start drawing
    inc hl

    ; pixel 0 (bit 0)   pixel 1 (bit 0) pixel 0 (bit 2) pixel 1 (bit 2) pixel 0 (bit 1) pixel 1 (bit 1) pixel 0 (bit 3) pixel 1 (bit 3)
    ; draw: 14, 14, 14, 14  ->  1110 1110 1110 1110  ->  00111111 (#3f), 00111111 (#3f),
    ld (hl), #3f
    inc hl
    ld (hl), #3f
    dec hl
    ld a, h
    add a, 8
    ld h, a
    ; draw: 7, 7, 14, 14  ->  0111 0111 1110 1110  ->  11111100 (#fc), 00111111 (#3f),
    ld (hl), #fc
    inc hl
    ld (hl), #3f
    dec hl
    ld a, h
    add a, 8
    ld h, a
    ; draw: 14, 14, 14, 9  ->  0111 0111 1110 1001  ->  00111111 (#3f), 01101011 (#6b),
    ld (hl), #3f
    inc hl
    ld (hl), #6b
    ret


;-----------------------------------------------
; input:
; - de: tank to move from
; - hl: tank to move to
; - c: max capacity in hl
custom_caldera_puzzle_movement:
    ld a, (de)
    ld b, a
    ld a, (hl)
    add a, b
    ld b, 0
    cp c
    jr c, custom_caldera_puzzle_movement_ok
    ; overflow:
    sub a, c
    ld b, a
    ld a, c
custom_caldera_puzzle_movement_ok:
    ld (hl), a
    ld a, b
    ld (de), a
    ld a, 1
    ld (redraw_whole_room_signal), a
    ret


;-----------------------------------------------
; - tank 1 holds 10 units at most
custom_caldera_puzzle_2_to_1:
    ld de, game_state_variable_caldera_puzzle_water2
    ld hl, game_state_variable_caldera_puzzle_water1
    ld c, 10
    jp custom_caldera_puzzle_movement


;-----------------------------------------------
; - tank 2 holds 5 units at most
custom_caldera_puzzle_1_to_2:
    ld de, game_state_variable_caldera_puzzle_water1
    ld hl, game_state_variable_caldera_puzzle_water2
    ld c, 5
    jp custom_caldera_puzzle_movement


;-----------------------------------------------
; - tank 3 holds 4 units at most
custom_caldera_puzzle_2_to_3:
    ld de, game_state_variable_caldera_puzzle_water2
    ld hl, game_state_variable_caldera_puzzle_water3
    ld c, 4
    jp custom_caldera_puzzle_movement


;-----------------------------------------------
custom_caldera_puzzle_3_to_2:
    ld de, game_state_variable_caldera_puzzle_water3
    ld hl, game_state_variable_caldera_puzzle_water2
    ld c, 5
    jp custom_caldera_puzzle_movement


;-----------------------------------------------
custom_caldera_puzzle_3_to_1:
    ld de, game_state_variable_caldera_puzzle_water3
    ld hl, game_state_variable_caldera_puzzle_water1
    ld c, 10
    jp custom_caldera_puzzle_movement


;-----------------------------------------------
custom_caldera_puzzle_1_to_3:
    ld de, game_state_variable_caldera_puzzle_water1
    ld hl, game_state_variable_caldera_puzzle_water3
    ld c, 4
    jp custom_caldera_puzzle_movement


