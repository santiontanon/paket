libreria_target:
    db 5, 6, 5, 6, 5, 5, 6, 5

;-----------------------------------------------
; input:
; - iy: object ptr
toggle_libreria_libro:
    ld a, (iy + OBJECT_STRUCT_STATE_DIRECTION)
    ; STATE_OPEN = 5    00000101
    ; STATE_CLOSED = 6  00000110
    xor #03  ; alternate between open/closed
    ld (iy + OBJECT_STRUCT_STATE_DIRECTION), a

    ; check solution:
    ld a, 10
    ld b, 8
    ld hl, libreria_target
toggle_libreria_libro_loop:
    push af
    push bc
        push hl
            call find_room_object_ptr_by_id
            ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
            and #0f
        pop hl
        cp (hl)
        jr nz, toggle_libreria_libro_fail
        inc hl
    pop bc
    pop af
    inc a
    djnz toggle_libreria_libro_loop
    ld a, 1
    ld (game_state_variable_libreria_solucionado), a
    jp draw_room

toggle_libreria_libro_fail:
    pop bc
    pop af
    jp draw_room
