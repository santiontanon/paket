custom_current_room_store:
    db 0, 0, 0  ; room ID, player x, y

;-----------------------------------------------
custom_push_room_and_position:
    ld a, (current_room)
    ld (custom_current_room_store), a
    ld ix, (player_object_ptr)
    ld a, (ix + OBJECT_STRUCT_X)
    ld (custom_current_room_store + 1), a
    ld a, (ix + OBJECT_STRUCT_Y)
    ld (custom_current_room_store + 2), a
    ret


;-----------------------------------------------
custom_pop_room_and_position:
    ld hl, custom_current_room_store
    jp executeRuleScript_go_to_room

