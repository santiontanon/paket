;-----------------------------------------------
catacumbas_apagadas_current_symbols:
    db 0


catacumbas_apagadas_init_room_symbols:
    ld a, (game_state_variable_catacumbas_state)
    cp 4
    jr z, catacumbas_apagadas_random_symbols
    ld (catacumbas_apagadas_current_symbols), a
    ret nz
catacumbas_apagadas_random_symbols:
    ; If we are in room 4 (we are lost), set some random symbols
    ld a, (catacumbas_apagadas_current_symbols)  ; increment it by 1
    inc a
    and #03
    ld (catacumbas_apagadas_current_symbols), a
    ret


catacumbas_apagadas_show_symbols:
    ld a, 3
    call find_room_object_ptr_by_id
    ld a, (catacumbas_apagadas_current_symbols)
    and #03  ; should not be needed, but just in case
    inc a  ; a random value between 1, 2, 3, 4
    add a, a
    add a, a
    add a, a
    add a, a
    add a, OBJECT_STATE_NUMBER
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    ret
