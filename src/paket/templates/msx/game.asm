;-----------------------------------------------
game_start:
    ld sp, #f380

    ; intialize variables:
    ld hl, data_to_zero_on_game_start_start
    ld bc, data_to_zero_on_game_start_end - data_to_zero_on_game_start_start
    call clear_memory

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
    ld hl, 0
    ld (custom_assembler_on_update), hl
ENDIF

    call initialize_game_state_variables

    ; initialize the inventory:
    ld hl, inventory_name_buffer
    ld (inventory_name_next_ptr), hl

    call update_keyboard_buffers

    call setup_pointer_sprites
    call setup_VDP_tables_for_game

    ; (current_room) == 0 at this point
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 ROOMBANK_PTRS_PAGE
    ld hl, (roomBankPointers)
    ld a, (roomBankPointers + 2)  ; MEGAROM page of room 0
    SETMEGAROMPAGE_8000_A
ELSE
    ld hl, roomBank0
ENDIF
    call load_room

    call upload_gui_tiles_to_VDP
    call draw_gui

game_loop_entry_point_on_load_new_room:
    xor a
    ld (currently_selected_verb), a
    ld (current_pointer_type), a

    call draw_room
IF N_PLAYER_SPRITES > 0
    call draw_player
ENDIF

    ; check if we need to draw a frame around the room:
    ld a, (room_buffer + ROOM_STRUCT_ROOM_FLAGS)
    and ROOM_FLAG_ISSUBROOM
    call nz, draw_frame_around_room

    call upload_player_sprites
    call adjust_room_using_persistent_state_on_room_start_rules

;-----------------------------------------------
game_loop:
    call wait_for_next_frame

    ; upload the sprites to the VDP (pointer and player):
    push af
        call upload_player_and_pointer_sprites
    pop af

    ; we update as many times as necessary to cupdate at constant 50Hz:
game_loop_update_loop:
    push af
        call update_keyboard_buffers
        call update_pointer
IF N_PLAYER_SPRITES > 0
        call update_player
ENDIF
        call update_object_animations

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
        ld hl, (custom_assembler_on_update)
        ld a, h
        or l
        call nz, call_hl
ENDIF

    pop af
    dec a
    jr nz, game_loop_update_loop

    ; we only redraw as fast as we can, but not necessarily at 50Hz:
    ld hl, redraw_whole_room_signal
    ld a, (hl)
    or a
    jr z, game_loop_update_loop_only_partial_redraw
    ld (hl), 0
    call draw_room
    call update_command_preview_text_after_pointer_movement
    jp game_loop_update_loop_room_redrawn
game_loop_update_loop_only_partial_redraw:
    call draw_room_dirty_columns
game_loop_update_loop_room_redrawn:

    ; update pointer, player and inventory:
    ld hl, redraw_inventory_signal
    ld a, (hl)
    ld (hl), 0
    or a
    call nz, draw_gui
    call draw_pointer
IF N_PLAYER_SPRITES > 0
    call draw_player
ENDIF
    jr game_loop

IF SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED == 1
call_hl:
    jp (hl)
ENDIF


;-----------------------------------------------
upload_player_sprites:
IF N_PLAYER_SPRITES > 0
    ld hl, pointer_sprites_attributes + 2 * 4
    ld de, SPRATR2 + 2 * 4
    ld bc, N_PLAYER_SPRITES * 4
    call fast_LDIRVM
ENDIF
    jr upload_player_and_pointer_sprites_entry_point

upload_player_and_pointer_sprites:
    ld hl, pointer_sprites_attributes
    ld de, SPRATR2
    ld bc, (2 + N_PLAYER_SPRITES) * 4
    call fast_LDIRVM
upload_player_and_pointer_sprites_entry_point:
IF N_PLAYER_SPRITES > 0
    ld a, (player_in_room)
    or a
    ret z
    ld hl, player_sprites_patterns
    ld de, SPRTBL2 + 32 * 2 * N_POINTER_TYPES
    ld bc, N_PLAYER_SPRITES * 32
    jp fast_LDIRVM
ELSE
    ret
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

IF MUSIC_TYPE_TSV == 1
    ; See if we want to load a new song:
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