;-----------------------------------------------
; when decompressed, this results in:
; - 16 number sprites of 6x8 pixels in size (24 bytes each)
; - 4 letter sprites for L, U, N, A of 6x12 pixels in size (36 bytes each)
pozo_puzzle_data_zx0:
    db #29, #48, #c0, #ea, #48, #d4, #84, #fa, #c0, #0f, #e7, #ff, #f4, #d9, #dc, #e2
    db #d1, #e8, #ac, #8b, #c0, #ac, #fa, #fb, #d0, #e8, #ea, #fb, #e8, #3e, #fc, #ac
    db #a0, #8f, #fc, #d7, #cf, #a0, #be, #e8, #d0, #8f, #84, #5c, #fb, #e9, #f5, #71
    db #d4, #a1, #fc, #62, #fc, #e8, #7f, #fb, #40, #ff, #ac, #a0, #7c, #10, #ff, #52
    db #a0, #e8, #41, #ac, #7f, #a0, #e2, #71, #10, #86, #d4, #9e, #d4, #e4, #fe, #a0
    db #cb, #9a, #84, #d4, #7e, #a1, #f4, #f9, #fb, #a0, #a7, #c0, #be, #fa, #e9, #4c
    db #38, #d0, #28, #c0, #6e, #d4, #fe, #5c, #bc, #c4, #4d, #a9, #b0, #d4, #7f, #fb
    db #71, #e9, #f4, #48, #ff, #d1, #11, #f3, #e3, #9b, #d8, #ef, #e9, #dc, #a0, #88
    db #d4, #fc, #7f, #71, #d6, #fe, #4c, #c4, #3b, #d0, #a4, #71, #d4, #fe, #fe, #d1
    db #97, #fc, #eb, #97, #d1, #fc, #8e, #2c, #a0, #28, #d4, #1c, #9f, #e0, #10, #7f
    db #71, #fe, #fb, #d0, #0c, #8a, #cf, #59, #f3, #36, #48, #e2, #e5, #97, #e2, #3f
    db #76, #e9, #e1, #f5, #db, #af, #a6, #a6, #d1, #f3, #48, #0c, #fd, #ba, #c9, #59
    db #fa, #9f, #c0, #7d, #fe, #52, #d1, #c3, #fa, #d1, #f4, #e2, #8f, #d1, #84, #f7
    db #9a, #b8, #c0, #28, #c0, #22, #e2, #a6, #be, #d1, #cb, #b2, #f2, #e9, #98, #7e
    db #bb, #ad, #b9, #d1, #b8, #88, #0c, #e2, #fd, #c9, #fb, #c8, #fc, #8a, #f6, #fc
    db #d1, #a3, #d1, #b8, #70, #b9, #8c, #48, #a6, #00, #02


pozo_puzzle_background:
    dw general_buffer + 15*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 1) * SCREEN_WIDTH_IN_BYTES + 4 * 4 + #800 * 4, #0803  ; 16
    dw general_buffer + 2*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 1) * SCREEN_WIDTH_IN_BYTES + 5 * 4 + #800 * 4, #0803  ; 3
    dw general_buffer + 16*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 1) * SCREEN_WIDTH_IN_BYTES + 6 * 4 + #800 * 2, #0c03  ; L
    dw general_buffer + 12*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 1) * SCREEN_WIDTH_IN_BYTES + 7 * 4 + #800 * 4, #0803  ; 13

    dw general_buffer + 4*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 3) * SCREEN_WIDTH_IN_BYTES + 4 * 4 + #800 * 4, #0803  ; 5
    dw general_buffer + 16*24 + 36*2, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 3) * SCREEN_WIDTH_IN_BYTES + 5 * 4 + #800 * 2, #0c03  ; N
    dw general_buffer + 10*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 3) * SCREEN_WIDTH_IN_BYTES + 6 * 4 + #800 * 4, #0803  ; 11
    dw general_buffer + 7*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 3) * SCREEN_WIDTH_IN_BYTES + 7 * 4 + #800 * 4, #0803  ; 8

    dw general_buffer + 8*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 5) * SCREEN_WIDTH_IN_BYTES + 4 * 4 + #800 * 4, #0803  ; 9
    dw general_buffer + 5*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 5) * SCREEN_WIDTH_IN_BYTES + 5 * 4 + #800 * 4, #0803  ; 6
    dw general_buffer + 6*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 5) * SCREEN_WIDTH_IN_BYTES + 6 * 4 + #800 * 4, #0803  ; 7
    dw general_buffer + 11*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 5) * SCREEN_WIDTH_IN_BYTES + 7 * 4 + #800 * 4, #0803  ; 12

    dw general_buffer + 3*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 7) * SCREEN_WIDTH_IN_BYTES + 4 * 4 + #800 * 4, #0803  ; 4
    dw general_buffer + 16*24 + 36*3, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 7) * SCREEN_WIDTH_IN_BYTES + 5 * 4 + #800 * 2, #0c03  ; A
    dw general_buffer + 13*24, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 7) * SCREEN_WIDTH_IN_BYTES + 6 * 4 + #800 * 4, #0803  ; 14
    dw general_buffer + 16*24 + 36, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 7) * SCREEN_WIDTH_IN_BYTES + 7 * 4 + #800 * 2, #0c03  ; U


;-----------------------------------------------
pozo_puzzle_draw_state:
    call pozo_puzzle_decompress_data

    call clear_pointer

    ; draw the board:
    ld ix, pozo_puzzle_background
    ld b, 16
pozo_puzzle_draw_board_loop:
    push bc
        ld l, (ix)
        ld h, (ix + 1)
        ld e, (ix + 2)
        ld d, (ix + 3)
        ld c, (ix + 4)
        ld b, (ix + 5)
        push ix
            call draw_sprite_variable_size_to_screen
        pop ix
        ld bc, 6
        add ix, bc
    pop bc
    djnz pozo_puzzle_draw_board_loop

    ; draw the selected numbers:
    ld ix, game_state_variable_puzzle_pozo_1
    ld b, 4
    ld de, VIDEO_MEMORY + (FIRST_SCREEN_ROOM_ROW + 1) * SCREEN_WIDTH_IN_BYTES + 9 * 4 + #800 * 4
pozo_puzzle_draw_board_loop_2:
    ld a, (ix)
    inc ix
    push ix
    push bc
        push de
            add a, a  ; * 2
            add a, a  ; * 4
            add a, a  ; * 8
            ld l, a
            ld e, a
            ld h, 0
            ld d, h
            add hl, hl  ; * 16
            add hl, de  ; * 24
            ld de, general_buffer
            add hl, de
            ld bc, #0803
        pop de
        push de
            call draw_sprite_variable_size_to_screen
        pop hl
        ld de, SCREEN_WIDTH_IN_BYTES * 2
        add hl, de
        ex de, hl
    pop bc
    pop ix
    djnz pozo_puzzle_draw_board_loop_2

    call save_pointer_background
;     jp draw_pointer
    ret


;-----------------------------------------------
pozo_puzzle_decompress_data
    ld hl, pozo_puzzle_data_zx0
    ld de, general_buffer
    jp PAKET_UNPACK


;-----------------------------------------------
pozo_puzzle_button_left1:
    ld hl, game_state_variable_puzzle_pozo_1
pozo_puzzle_button_left_generic:
    ld a, (hl)
    dec a
    ld (hl), a
    cp #ff
    jp nz, pozo_puzzle_draw_state
    ld a, 15
    ld (hl), a
    jp pozo_puzzle_draw_state

pozo_puzzle_button_left2:
    ld hl, game_state_variable_puzzle_pozo_2
    jr pozo_puzzle_button_left_generic

pozo_puzzle_button_left3:
    ld hl, game_state_variable_puzzle_pozo_3
    jr pozo_puzzle_button_left_generic

pozo_puzzle_button_left4:
    ld hl, game_state_variable_puzzle_pozo_4
    jr pozo_puzzle_button_left_generic


pozo_puzzle_button_right1:
    ld hl, game_state_variable_puzzle_pozo_1
pozo_puzzle_button_right_generic:
    ld a, (hl)
    inc a
    ld (hl), a
    cp 16
    jp nz, pozo_puzzle_draw_state
    xor a
    ld (hl), a
    jp pozo_puzzle_draw_state

pozo_puzzle_button_right2:
    ld hl, game_state_variable_puzzle_pozo_2
    jr pozo_puzzle_button_right_generic

pozo_puzzle_button_right3:
    ld hl, game_state_variable_puzzle_pozo_3
    jr pozo_puzzle_button_right_generic

pozo_puzzle_button_right4:
    ld hl, game_state_variable_puzzle_pozo_4
    jr pozo_puzzle_button_right_generic

