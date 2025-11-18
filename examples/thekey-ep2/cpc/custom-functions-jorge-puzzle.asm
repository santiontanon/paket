;-----------------------------------------------
; jorge_puzzle_symbol_names:
;     db 5, 167, 142, 159, 138, 153
;     db 3, 147, 134, 153
;     db 6, 130, 183, 151, 130, 134, 159
;     db 7, 138, 54, 148, 142, 151, 142, 161
;     db 5, 163, 126, 165, 159, 153
;     db 5, 126, 159, 142, 134, 161
;     db 7, 126, 130, 165, 126, 159, 142, 153
;     db 11, 130, 126, 155, 159, 142, 130, 153, 159, 151, 142, 153
;     db 9, 161, 126, 138, 142, 163, 126, 159, 142, 153
;     db 8, 134, 161, 130, 153, 159, 155, 142, 153
;     db 5, 147, 142, 128, 159, 126
;     db 6, 155, 142, 161, 130, 142, 161
jorge_puzzle_n_symbols_added:
    db 0
jorge_puzzle_position:
    db 0, 2



;-----------------------------------------------
jorge_puzzle_reset:
    xor a
    ld (jorge_puzzle_n_symbols_added), a

    ; - move 16 to 0, 44
    ld bc, #0200
    call jorge_puzzle_teleport_piece

    ; - make objects 17, 18, 19, 20 "hidden"
    ld a, 17
    ld b, 4
jorge_puzzle_reset_loop1:
    push af
    push bc
        call find_room_object_ptr_by_id
        ld (ix + OBJECT_STRUCT_STATE_DIRECTION), OBJECT_STATE_HIDDEN + OBJECT_DIRECTION_DOWN * 16
    pop bc
    pop af
    inc a
    djnz jorge_puzzle_reset_loop1

    ; - redraw room
    ld a, 1
    ld (redraw_whole_room_signal),a    
    ret

    ; - rename symbols
    ; jp jorge_puzzle_rename_symbols:


; ;-----------------------------------------------
; jorge_puzzle_rename_symbols:
;     ld a, 5  ; first object ID
;     ld b, 11 + 1 + 4  ; number of symbols
; jorge_puzzle_rename_symbols_loop:
;     push af
;     push bc
;         call find_room_object_ptr_by_id
;         ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
;         rrca
;         rrca
;         rrca
;         rrca
;         and #0f  ; a = symbol number
;         ld hl, jorge_puzzle_symbol_names
;         jr z, jorge_puzzle_rename_symbols_loop2_done
;         ld b, a
;         ld d, 0
; jorge_puzzle_rename_symbols_loop2:
;         ld e, (hl)
;         inc e  ; to account for the length byte
;         add hl, de
;         djnz jorge_puzzle_rename_symbols_loop2        
; jorge_puzzle_rename_symbols_loop2_done:
;         ; Here "hl" has the name we want to give to this object:
;         ld a, e
;         ld e, (ix + OBJECT_STRUCT_NAME_PTR)
;         ld d, (ix + OBJECT_STRUCT_NAME_PTR + 1)
;         ld c, a
;         ld b, 0
;         ldir
;     pop bc
;     pop af
;     inc a
;     djnz jorge_puzzle_rename_symbols_loop
;     ret


;-----------------------------------------------
jorge_puzzle_teleport_piece:
    ld (jorge_puzzle_position), bc
    ld a, 16
    call find_room_object_ptr_by_id    
    ld a, (jorge_puzzle_position)
    add a, a
    add a, a
    add a, a
    ld (ix + OBJECT_STRUCT_X), a
    add a, 8
    ld (ix + OBJECT_STRUCT_X2), a
    ld a, (jorge_puzzle_position + 1)
    add a, a
    add a, a
    add a, a
    add a, a
    add a, 12
    ld (ix + OBJECT_STRUCT_Y), a
    add a, 8
    ld (ix + OBJECT_STRUCT_Y2), a
    ret


;-----------------------------------------------
jorge_puzzle_add_symbol:
    ld hl, jorge_puzzle_n_symbols_added
    ld a, (hl)
    cp 4
    jr z, jorge_puzzle_error
    inc (hl)
    ld a, (iy + OBJECT_STRUCT_STATE_DIRECTION)
    push af
        ld a, (hl)
        add a, 17 - 1  ; first tablero object
        call find_room_object_ptr_by_id
    pop af
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a

    ld a, 1
    ld (redraw_whole_room_signal),a

    ld hl, sfx_sfx_button_press_afx
    jp play_SFX_with_high_priority

jorge_puzzle_error:
    ld hl, sfx_sfx_error_afx
    jp play_SFX_with_high_priority


;-----------------------------------------------
; - tauro (#40): north
; - Leo (#10): east
; - Escorpio (#90): south
; - Aries (#50): west
; solution: escorpio, leo, tauro, leo
jorge_puzzle_play:
    ld a, (jorge_puzzle_n_symbols_added)
    cp 4
    jr nz, jorge_puzzle_error
    ld a, 17
    ld b, 4
jorge_puzzle_play_loop:
    push af
    push bc
        call find_room_object_ptr_by_id
        ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
        and #f0
        cp #40  ; go north
        jr z, jorge_puzzle_play_north
        cp #10  ; go east
        jr z, jorge_puzzle_play_east
        cp #90
        jr z, jorge_puzzle_play_south
        cp #50
        jr z, jorge_puzzle_play_west
        ; failure:

jorge_puzzle_play_loop_continue:
    pop bc
    pop af
    inc a
    djnz jorge_puzzle_play_loop

    ; check puzzle solved:
    ld bc, (jorge_puzzle_position)
    ld hl, #0005
    or a
    sbc hl, bc
    jr z, jorge_puzzle_play_success
    ; failed:
    ld hl, sfx_sfx_error_afx
    call play_SFX_with_high_priority
    call wait_for_space
    jp jorge_puzzle_reset
jorge_puzzle_play_success:
    ld a, 2
    ld (game_state_variable_puzzle_jorge), a
    ret


jorge_puzzle_play_north:
    ld bc, (jorge_puzzle_position)
    dec b
    call jorge_puzzle_attempt_move
    jr z, jorge_puzzle_play_north
    jr jorge_puzzle_play_loop_continue

jorge_puzzle_play_east:
    ld bc, (jorge_puzzle_position)
    inc c
    call jorge_puzzle_attempt_move
    jr z, jorge_puzzle_play_east
    jr jorge_puzzle_play_loop_continue

jorge_puzzle_play_south:
    ld bc, (jorge_puzzle_position)
    inc b
    call jorge_puzzle_attempt_move
    jr z, jorge_puzzle_play_south
    jr jorge_puzzle_play_loop_continue

jorge_puzzle_play_west:
    ld bc, (jorge_puzzle_position)
    dec c
    call jorge_puzzle_attempt_move
    jr z, jorge_puzzle_play_west
    jr jorge_puzzle_play_loop_continue


;-----------------------------------------------
; input:
; - c, b: target position
; output:
; - z: success
; - nz: failure
jorge_puzzle_attempt_move:
    ld a, c
    cp #ff
    jr z, jorge_puzzle_attempt_move_fail
    cp 6
    jr z, jorge_puzzle_attempt_move_fail
    ld a, b
    cp #ff
    jr z, jorge_puzzle_attempt_move_fail
    cp 5
    jr z, jorge_puzzle_attempt_move_fail
    ; check walls: 
    ;   (2, 0)
    ;   (4, 1), (5, 1)
    ;   (5, 2)
    ;   (1, 3)
    ;   (4, 4)
    or a
    ld hl, #0002
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail
    or a
    ld hl, #0104
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail
    or a
    ld hl, #0105
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail
    or a
    ld hl, #0205
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail
    or a
    ld hl, #0301
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail
    or a
    ld hl, #0404
    sbc hl, bc
    jr z, jorge_puzzle_attempt_move_fail

    ; success:
    call jorge_puzzle_teleport_piece
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority
    call draw_room
    xor a
    ret

jorge_puzzle_attempt_move_fail:
    or 1
    ret
