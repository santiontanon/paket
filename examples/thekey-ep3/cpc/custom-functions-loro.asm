LORO_OBJECT_ID: equ 62

;-----------------------------------------------
custom_loro_vuela:
    ; find loro object ptr:
    ld a, LORO_OBJECT_ID
    call find_room_object_ptr_by_id
    ret nz  ; loro not in this room

    ld b, 42
    ld e, (ix + OBJECT_STRUCT_X)
    ld d, (ix + OBJECT_STRUCT_X2)
custom_loro_vuela_and_return_to_de:
    push de
        call custom_loro_vuela_internal
    pop bc

    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), 9 + 2*16  ; hidden
    call custom_loro_vuela_set_dirty_columns
    push ix
        call draw_room_dirty_columns
    pop ix

    ld (ix + OBJECT_STRUCT_X), c
    ld (ix + OBJECT_STRUCT_X2), b
    ld a, 1
    ld (force_update_pointer),a
    jp draw_room


custom_loro_vuela_internal:
custom_loro_vuela_loop:
    push bc
    push ix
        inc (ix + OBJECT_STRUCT_X)
        inc (ix + OBJECT_STRUCT_X)
        inc (ix + OBJECT_STRUCT_X2)
        inc (ix + OBJECT_STRUCT_X2)
        ld a, b
        rrca
        rrca
        and #01
        add a, 5 + 2*16  ; alternate between the two animation frames (open / close)
        ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a  ; state + direction*16

        call custom_loro_vuela_set_dirty_columns
        call wait_for_next_frame
        call wait_for_next_frame
        call draw_room_dirty_columns
    pop ix
    pop bc
    djnz custom_loro_vuela_loop
    ret


custom_loro_vuela_set_dirty_columns:
    ld hl, dirty_column_buffer
    ld a, (ix + OBJECT_STRUCT_X)
    srl a
    dec a  ; do telete the previous frame
    ADD_HL_A
    ld (hl), 1
    inc hl
    ld (hl), 1
    inc hl
    ld (hl), 1
    inc hl
    ld (hl), 1
    ret


;-----------------------------------------------
custom_loro_vuela_otra_pantalla:
    ; find loro object ptr:
    ld a, LORO_OBJECT_ID
    call find_room_object_ptr_by_id
    ret nz  ; loro not in this room

    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    add a, a
    add a, a
    sub 2
    ld b, a  ; room_width -> steps: 12, 46, 13 -> 50, 14 -> 54
    ld e, (ix + OBJECT_STRUCT_X)
    ld d, (ix + OBJECT_STRUCT_X2)
    jp custom_loro_vuela_and_return_to_de


;-----------------------------------------------
custom_loro_vuelve:
    ld a, LORO_OBJECT_ID
    call find_room_object_ptr_by_id
    ret nz  ; loro not in this room

    ld (ix + OBJECT_STRUCT_X), 0
    ld (ix + OBJECT_STRUCT_X2), 8
    ld e, 16
    ld d, 24  ; return-to position
    ld b, 8
    call custom_loro_vuela_internal

    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), 4 + 2*16  ; state + direction*16
    ld a, 1
    ld (force_update_pointer),a
    jp draw_room

