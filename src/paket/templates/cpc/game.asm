;-----------------------------------------------
game_start:
    ld sp, STACK_ADDRESS

    ; intialize variables:
    ld hl, data_to_zero_on_game_start_start
    ld bc, (data_to_zero_on_game_start_end - data_to_zero_on_game_start_start) - 1
    call clear_memory

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
    ld hl, 0
    ld (custom_assembler_on_update), hl
ENDIF

    ld hl, #c000
    ld (pointer_background_buffer_video_mem_ptr), hl  ; we initialize this in case clear_pointer is called before we have ever drawn the pointer

    call initialize_game_state_variables

    ; initialize the inventory:
    ld hl, inventory_name_buffer
    ld (inventory_name_next_ptr),hl

    call update_keyboard_buffers

    ; (current_room) == 0 at this point
IF IS_6128 == 1
    SET_128K_PAGE_4000 ROOMBANK_PTRS_PAGE
    ld hl, (roomBankPointers)
    ld a, (roomBankPointers + 2)  ; RAM page of room 0
    SET_128K_PAGE_4000_A
ELSE
    ld hl, roomBank0
ENDIF
    call load_room

    ; ld hl,(current_palette_ptr)
    ; call set_palette_16
    call draw_gui

game_loop_entry_point_on_load_new_room:
IF CAN_TURN_GUI_ON_OFF = 1
    ld a, (gui_on)
    or a
    call nz, reset_pointer
ELSE
    call reset_pointer
ENDIF
    xor a
    ld (player_current_action),a  ; PLAYER_ACTION_IDLE
    inc a
    ld (force_update_pointer),a  ; ensure we update the pointer type when
                                 ; starting a new room.

    call draw_room
IF FRAME_AROUND_SUBROOMS == 1
    ; check if we need to draw a frame around the room:
    ld a, (room_buffer + ROOM_STRUCT_ROOM_FLAGS)
    and ROOM_FLAG_ISSUBROOM
    call nz, draw_frame_around_room
ENDIF
IF CAN_TURN_GUI_ON_OFF = 1
    ld a, (gui_on)
    or a
    call nz, draw_pointer   ; pointer needs to be drawn first, so that the
                            ; background is saved
ELSE
    call draw_pointer   ; pointer needs to be drawn first, so that the
                        ; background is saved
ENDIF

    call adjust_room_using_persistent_state_on_room_start_rules

;-----------------------------------------------
game_loop:
    call wait_for_next_frame

    ; update pointer and inventory the very first thing to avoid flickering:
    push af
IF CAN_TURN_GUI_ON_OFF = 1
        ld a, (gui_on)
        or a
        jr z, game_loop_skip_gui
ENDIF
        call clear_pointer
        ld hl, redraw_inventory_signal
        ld a, (hl)
        ld (hl), 0
        or a
        call nz,draw_gui
        call draw_pointer
game_loop_skip_gui:
    pop af

    ; we update as many times as necessary to update at constant 50Hz:
game_loop_update_loop:
    push af
        call update_keyboard_buffers
IF CAN_TURN_GUI_ON_OFF = 1
        ld a, (gui_on)
        or a
        call nz, update_pointer
ELSE
        call update_pointer
ENDIF
        call update_player
        call update_object_animations

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
        ld hl, (custom_assembler_on_update)
        ld a, h
        or l
        call nz, call_hl
ENDIF

    pop af
    or a
    jr z, game_loop_update_loop_done
    dec a
    jr nz, game_loop_update_loop
game_loop_update_loop_done:

    ; we only redraw as fast as we can, but not necessarily at 50Hz:
    ld hl, redraw_whole_room_signal
    ld a, (hl)
    or a
    jr nz, game_loop_update_loop_redraw_room
    call draw_room_dirty_columns_player_height
    jr game_loop_update_loop_room_redrawn
game_loop_update_loop_redraw_room:
    ld (hl), 0
    call draw_room
    call update_command_preview_text_after_pointer_movement
game_loop_update_loop_room_redrawn:

    jr game_loop

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
call_hl:
    jp (hl)
ENDIF


;-----------------------------------------------
; output:
; - a: number of cycles we need to update
wait_for_next_frame:
    ld hl, vsyncs_since_last_frame
    ld a, (hl)
    or a
    jr z, wait_for_next_frame
    ld (hl), 0
    ; See if we want to load a new song:

IF MUSIC_TYPE_TSV == 1
    push af
        call song_load_request_check
    pop af
    ret
    

;-----------------------------------------------
song_load_request_check:
    ld hl, (load_new_song_request)
    ld a, h
    or l
    jp nz, play_song_no_stop
ENDIF

    ret
