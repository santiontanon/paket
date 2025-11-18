;-----------------------------------------------
screen_shake:
    ld b, 5
screen_shake_loop:
    push bc
        call offset_screen_up_4pixels
        ld bc, 20
        call wait_bc_halts
        call offset_screen_up_0pixels
        ld bc, 20
        call wait_bc_halts
    pop bc
    djnz screen_shake_loop
    ret


;-----------------------------------------------
offset_screen_up_4pixels:
    di
    ld bc, #bc05
    out (c), c
    ld bc, #bd00 + 4
    out (c), c
    ld bc, #bc04
    out (c), c
    ld bc, #bd00 + 37
    out (c), c
    ei
    ret


;-----------------------------------------------
offset_screen_up_0pixels:
    di
    ld bc, #bc05
    out (c), c
    ld bc, #bd00 + 0
    out (c), c
    ld bc, #bc04
    out (c), c
    ld bc, #bd00 + 38
    out (c), c
    ei
    ret


LUNA_GHOST_OBJECT_ID: equ 19

;-----------------------------------------------
custom_luna_leaves1:
    ld a, LUNA_GHOST_OBJECT_ID
    call find_room_object_ptr_by_id
    ret nz  ; luna not in this room

    ld b, 24
;     ld e, (ix + OBJECT_STRUCT_X)
;     ld d, (ix + OBJECT_STRUCT_X2)

custom_luna_leaves1_loop:
    push bc
    push ix
        dec (ix + OBJECT_STRUCT_Y)
        dec (ix + OBJECT_STRUCT_Y2)
        call custom_loro_vuela_set_dirty_columns
        ld b, 5
custom_luna_leaves1_loop2:
        push bc
            call update_object_animations
            call wait_for_next_frame
        pop bc
        djnz custom_luna_leaves1_loop2
        call draw_room_dirty_columns
    pop ix
    pop bc
    djnz custom_luna_leaves1_loop
    ret

;     ld a, 1
;     ld (force_update_pointer),a
;     jp draw_room


;-----------------------------------------------
custom_periodic_screen_shake:
    ld a, (game_cycle)
    and #7f
    ret nz
    ld hl, sfx_sfx_door_close_afx
    call play_SFX_with_high_priority
    jp screen_shake


;-----------------------------------------------
LUNA_GHOST_OBJECT_ID2: equ 38
ALAN_GHOST_OBJECT_ID2: equ 39
LISA_GHOST_OBJECT_ID2: equ 40

custom_all_ghost_leave:
    ld b, 24
custom_all_ghost_leave_loop:
    push bc
    push ix
        ld a, LUNA_GHOST_OBJECT_ID2
        call find_room_object_ptr_by_id    
        dec (ix + OBJECT_STRUCT_Y)
        dec (ix + OBJECT_STRUCT_Y2)
        ld a, ALAN_GHOST_OBJECT_ID2
        call find_room_object_ptr_by_id    
        dec (ix + OBJECT_STRUCT_Y)
        dec (ix + OBJECT_STRUCT_Y2)
        ld a, LISA_GHOST_OBJECT_ID2
        call find_room_object_ptr_by_id    
        dec (ix + OBJECT_STRUCT_Y)
        dec (ix + OBJECT_STRUCT_Y2)

        ld hl, dirty_column_buffer
        ld b, 11
custom_all_ghost_leave_loop2:
        ld (hl), 1
        inc hl
        djnz custom_all_ghost_leave_loop2

        ld b, 5
custom_all_ghost_leave_loop3:
        push bc
            call update_object_animations
            call wait_for_next_frame
        pop bc
        djnz custom_all_ghost_leave_loop3
        call draw_room_dirty_columns
    pop ix
    pop bc
    djnz custom_all_ghost_leave_loop
    ret

;     ld a, 1
;     ld (force_update_pointer),a
;     jp draw_room
