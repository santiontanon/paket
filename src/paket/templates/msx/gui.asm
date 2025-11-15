;-----------------------------------------------
upload_gui_tiles_to_VDP:
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 GUI_DATA_PAGE
ENDIF
    ld hl, gui_data_compressed
    ld de, general_buffer
    call PAKET_UNPACK

    ld hl, general_buffer + (GUI_WIDTH * MSX_TILES_PER_ENGINE_TILE * GUI_HEIGHT) + 1
    ld de, CHRTBL2 + 256 * 8 * 2 + FIRST_GUI_VDP_TILE * 8
    ld bc, GUI_N_TILES * 8 * MSX_TILES_PER_ENGINE_TILE
    call fast_LDIRVM

    ld hl, general_buffer + (GUI_WIDTH * MSX_TILES_PER_ENGINE_TILE * GUI_HEIGHT) + 1 + GUI_N_TILES * 8 * MSX_TILES_PER_ENGINE_TILE
    ld de, CLRTBL2 + 256 * 8 * 2 + FIRST_GUI_VDP_TILE * 8
    ld bc, GUI_N_TILES * 8 * MSX_TILES_PER_ENGINE_TILE
    jp fast_LDIRVM


;-----------------------------------------------
; draws the complete gui in the video memory
draw_gui:
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 GUI_DATA_PAGE
ENDIF
    ld hl, gui_data_compressed
    ld de, general_buffer
    call PAKET_UNPACK

    ld b, GUI_HEIGHT
    ld hl, general_buffer
    ld de, GUI_VDP_PTR
draw_gui_loop:
    push bc
    push hl
        push de
            ld bc, GUI_WIDTH * MSX_TILES_PER_ENGINE_TILE
            call fast_LDIRVM
        pop hl
        ld bc, 32
        add hl, bc
        ex de, hl
    pop hl
    ld bc, GUI_WIDTH * MSX_TILES_PER_ENGINE_TILE
    add hl, bc
    pop bc
    djnz draw_gui_loop
    ; jp draw_gui_inventory


;-----------------------------------------------
draw_gui_inventory:
    ; draw the inventory:
    ld de, inventory_ids
    ld hl, inventory_gfx
    ld bc, 4  ; offset to add to "hl" after each iteration to get the next item gfx
    xor a  ; current item
draw_gui_inventory_loop:
    push af
    push hl
    push de
        push af
            ld a, (de)
            or a
            jr z, draw_gui_inventory_loop_no_item
        pop af
        ; draw item:
        push bc
            ex de, hl
            ld hl, INVENTORY_VIDEO_MEM_START
            ld bc, 32 * 2  ; bring it two lines down
draw_gui_inventory_loop_line_loop:
            cp INVENTORY_ITEMS_PER_LINE
            jp m, draw_gui_inventory_loop_found_line
            add hl, bc
            sub INVENTORY_ITEMS_PER_LINE
            jr draw_gui_inventory_loop_line_loop
draw_gui_inventory_loop_found_line:
            add a, a
            ld b, 0
            ld c, a
            add hl, bc
            ex de, hl
            ld bc, 2
            push hl
                push de
                    call fast_LDIRVM
                pop hl
                ld bc, 32
                add hl, bc
                ex de, hl
            pop hl
            inc hl
            inc hl
            ld bc, 2
            call fast_LDIRVM
        pop bc
        jr draw_gui_inventory_loop_no_item_no_pop_af
draw_gui_inventory_loop_no_item:
        pop af
draw_gui_inventory_loop_no_item_no_pop_af:
    pop de
    pop hl
    pop af
    inc de
    add hl, bc
    inc a
    cp INVENTORY_SIZE
    jr nz, draw_gui_inventory_loop
    ret


;-----------------------------------------------
; adds an item to the inventory.
; input:
; - a: ID of the item
add_item_to_inventory:
    ld c, a
    ld b, a
    dec c  ; the graphic to decompress is (ID - 1)
    ld de, inventory_ids
    ld hl, inventory_gfx
    ld ix, inventory_name_ptrs
    push bc
        ld bc, 4
add_item_to_inventory_loop:
        ld a, (de)
        or a
        jr z, add_item_to_inventory_slot_found
        inc de
        add hl, bc
        inc ix
        inc ix
        jr add_item_to_inventory_loop  ; no need to check if we reached the end, as there should always be space in the inventory
add_item_to_inventory_slot_found:
        ld bc, (inventory_name_next_ptr)
        ld (ix), c
        ld (ix + 1), b  ; we save the pointer to the item name (which we know is going to be (inventory_name_next_ptr))
    pop bc
    ld a, b
    ld (de), a  ; set the ID
    ld a, c

    ; Add the name of the item to (inventory_name_next_ptr)
    push hl
        push af
            ld hl, item_text_data
            add a, a
            ld b, a
            add a, a
            add a, b  ; a = a * 6
            ld b, 0
            ld c, a
            add hl, bc
            ld c, (hl)  ; bank #
            inc hl
            ld a, (hl)  ; text # within the bank
            ld de, (inventory_name_next_ptr)
            call get_text_from_bank
            ld (inventory_name_next_ptr), de

            ; decompresses the a-th item sprite on to "hl"
IF IS_MEGAROM == 1
            SETMEGAROMPAGE_8000 ITEM_DATA_PAGE
ENDIF
            ld hl, item_sprite_data
            ld de, general_buffer
            call PAKET_UNPACK
        pop af
        ld h, 0
        ld l, a
        add hl, hl
        add hl, hl
        ld bc, general_buffer
        add hl, bc  ; hl = pointer to the sprite to copy
    pop de  ; <- we set the target destination to "de"
    ldi
    ldi
    ldi
    ldi
    ret


;-----------------------------------------------
; removes an item with ID "a" from the inventory (if there are more than one,
; only the first one will be deleted).
remove_item_from_inventory:
    ; what we do is to clear the inventory, and re-add all the items one by one (so that the name buffers, etc. is rebuilt correctly):
    ; copy the current inventory (sans the item to remove) to a buffer:
    ld b, INVENTORY_SIZE
    ld c, a
    ld hl, inventory_ids
    ld de, small_general_buffer
remove_item_from_inventory_loop:
    ld a, (hl)
    cp c
    jr nz, remove_item_from_inventory_loop_do_not_remove
    xor a
    ld c, a  ; we only remove the first one that matches
remove_item_from_inventory_loop_do_not_remove:
    ld (de), a
    ld (hl), 0  ; we clear the inventory as we copy
    inc hl
    inc de
    djnz remove_item_from_inventory_loop

    ld hl, inventory_name_buffer
    ld (inventory_name_next_ptr), hl

    ; re-add all the items:
    ld b, INVENTORY_SIZE
    ld hl, small_general_buffer
remove_item_from_inventory_loop2:
    ld a, (hl)
    push hl
    push bc
        or a
        call nz, add_item_to_inventory
    pop bc
    pop hl
    inc hl
    djnz remove_item_from_inventory_loop2
    ret


;-----------------------------------------------
; Decompresses and uploads the pointer sprites to the VDP
setup_pointer_sprites:
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 POINTERS_DATA_PAGE
ENDIF
    ld hl, pointers_compressed
    ld de, general_buffer
    call PAKET_UNPACK

    ld hl, general_buffer
    ld de, SPRTBL2
    ld bc, 32 * 2 * N_POINTER_TYPES
    jp fast_LDIRVM


;-----------------------------------------------
clear_pointer:
    ld hl, pointer_sprites_attributes
    ld de, pointer_sprites_attributes+1
    ld bc, 8 - 1
    ld a, 200  ; some large y value
    ld (hl), a
    ldir
    ; upload to VDP:
    ld hl, pointer_sprites_attributes
    ld de, SPRATR2
    ld bc, (2 + N_PLAYER_SPRITES) * 4
    jp fast_LDIRVM


;-----------------------------------------------
; Updates the pointer sprites with the current pointer state
draw_pointer:
    ld de, pointer_sprites_attributes
    ld hl, pointer_y
    
    ld a, (hl)
    add a, FIRST_SCREEN_ROOM_ROW * 8 - 1  ; -1 since sprites in MSX are drawn one pixel lower than they should be
    ld (de), a
    inc hl
    inc de

    ld a, (hl)
    add a, ((32 - SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE) / 2) * 8
    ld (de), a
    inc hl
    inc de

    ld a, (current_pointer_type)
    add a, a
    add a, a
    add a, a
    push af
        ld (de), a
        inc de
        ld a, COLOR_WHITE
        ld (de), a
        inc de

        ld hl, pointer_sprites_attributes
        ldi
        ldi
    pop af
    add a, 4
    ld (de), a
    inc de
    ld a, COLOR_BLACK
    ld (de), a
    ret


;-----------------------------------------------
; moves the pointer with the keyboard
update_pointer:
    ld ixl, 0  ; to track if pointer is being moved or not
    ld a, (keyboard_line_state)
    bit 7, a
    call z, move_pointer_right
    bit 6, a
    call z, move_pointer_down
    bit 5, a
    call z, move_pointer_up
    bit 4, a
    call z, move_pointer_left

    ; press SPACE:
    ld a, (keyboard_line_clicks)
    bit 0, a
    jp nz, update_pointer_space_pressed
IF DOUBLE_CLICK_ON_EXIT == 1
    ld a, (time_since_last_space_click)
    inc a
    bit 7, a  ; prevent the timer from overflowing
    jr z, update_pointer_no_double_click_overflow
    ld a, 128
update_pointer_no_double_click_overflow:
    ld (time_since_last_space_click), a
ENDIF

    ; press I, U to navigate through the inventory:
    ld a, (keyboard_line_clicks + 2)
    bit 6, a
    jr nz, update_pointer_next_item
    bit 7, a
    jp nz, update_pointer_previous_item

    ; press TAB to change the pointer type
    ld a, (keyboard_line_clicks + 1)
    bit 3, a
    jr nz, change_pointer

    ; press ESC to reset pointer to cross
    bit 2, a
    jr nz, reset_pointer

    ; update for how long has the pointer been moving
    ld hl, pointer_move_time
    ld a, (hl)
    cp 16
    jr z, update_pointer_timer_maxed_out
    inc (hl)
update_pointer_timer_maxed_out:
    ld a, ixl
    or a
    jp nz, update_command_preview_text_after_pointer_movement
    xor a
    ld (pointer_move_time), a
    ret


change_pointer:
    xor a
    ld (currently_selected_item_to_use), a
    ld hl, current_pointer_type
    ld a, (hl)
    inc a
    cp 5
    jp m,change_pointer_no_loop_back
    ld a, 1  ; we skip the cross
change_pointer_no_loop_back:
    ld (hl), a
    ld (currently_selected_verb), a
    xor a
    ld (current_action_text_id), a
    jp update_command_preview_text_after_pointer_movement


reset_pointer:
    xor a
    ld (current_pointer_type), a
    ld (currently_selected_verb), a
    ld (current_action_text_id), a
    ld (currently_selected_item_to_use), a
    jp update_command_preview_text_after_pointer_movement


update_pointer_next_item:
    ld hl,inventory_ids
    ld a,(hl)
    or a
    ret z  ; inventory is empty!
    call item_under_pointer
    jr z, update_pointer_next_item_go_to_first_item
    ; go to the next item:
    ld b,a  ; we remember the item we are over of
    cp INVENTORY_SIZE-1
    jr z,update_pointer_next_item_go_to_first_item
    inc hl
    ld a,(hl)
    or a
    jr z,update_pointer_next_item_go_to_first_item
    inc b  ; item we want to go to

update_pointer_next_item_go_to_item_b:
    ld hl,pointer_y
    ld (hl),INVENTORY_START_Y+4
update_pointer_next_item_y_loop:
    ld a,b
    sub INVENTORY_ITEMS_PER_LINE
    jp m,update_pointer_next_item_y_loop_done
    ld b,a
    ld a,(hl)
    add a,16
    ld (hl),a
    jr update_pointer_next_item_y_loop
update_pointer_next_item_y_loop_done:
    inc hl
    ld (hl),INVENTORY_START_X+2
    ld a,b
    or a
update_pointer_next_item_x_loop:
    jr z,update_pointer_next_item_x_loop_done
    ld a,(hl)
    add a,16
    ld (hl),a
    dec b
    jr update_pointer_next_item_x_loop
update_pointer_next_item_x_loop_done:
    jp update_command_preview_text_after_pointer_movement

update_pointer_next_item_go_to_first_item:
    ld b,0
    jr update_pointer_next_item_go_to_item_b


update_pointer_previous_item:
    ld hl,inventory_ids
    ld a,(hl)
    or a
    ret z  ; inventory is empty!
    call item_under_pointer
    jr z,update_pointer_next_item_go_to_last_item
    ; go to the previous item:
    dec a
    ld b,a
    jp p,update_pointer_next_item_go_to_item_b
update_pointer_next_item_go_to_last_item:
    ld hl,inventory_ids+INVENTORY_SIZE-1
    ld b,INVENTORY_SIZE-1
update_pointer_next_item_go_to_last_item_loop:
    ld a,(hl)
    or a
    jr nz,update_pointer_next_item_go_to_item_b
    dec b
    dec hl
    jr update_pointer_next_item_go_to_last_item_loop


update_pointer_space_pressed:
IF DOUBLE_CLICK_ON_EXIT == 1
    ; Check if it's a double click:
    ld hl, double_click
    ld (hl), 0  ; mark as no double-click
    ld a, (time_since_last_space_click)
    cp DOUBLE_CLICK_INTERVAL
    jr nc, update_pointer_space_pressed_no_double_click
    ; It is a double click! (mark it as so)
    ld (hl), 1  ; mark as double-click

update_pointer_space_pressed_no_double_click:
    xor a
    ld (time_since_last_space_click), a
ENDIF    
    call clear_text_area

    call item_under_pointer
    jr nz, update_pointer_space_pressed_over_item
IF ALLOW_SAVING_GAME == 1
    ld e, 5
ELSE
    ld e, 4
ENDIF
    call verb_under_pointer
    jr nz, update_pointer_space_pressed_over_verb
    call object_under_pointer
    jr nz, update_pointer_space_pressed_over_object

    ; unless we have clicked on the GUI, trigger a request for walking:
    ld a, (pointer_y)
    add a, 3
    cp GUI_START_PIXEL_Y
    call m, player_request_movement_to_pointer

    ; check if we clicked outside of game area, to generate the appropriate trigger:
    call pointer_outside_of_room_area
    ret nz
    
    ; generate event, "click-outside-of-game-area":
    ld a, TRIGGER_CLICK_OUTSIDE_ROOM_AREA
    ld (current_event), a
    xor a
    ld (current_event + 1), a
    ld (current_event + 2), a
    call eventMatchesRule
    ret nz

    ; rule matched! execute rule effect:
    call executeRuleScript
    jp reset_pointer


update_pointer_space_pressed_over_verb:
IF ALLOW_SAVING_GAME == 1
    cp VERB_LOAD_SAVE - 2  ; "load/save" is in the location that the exit verb should be, so, we need to subtract one extra
    jr nz, update_pointer_space_pressed_over_verb_continue
    ; load/save game clicked!
    xor a
    ld (last_script_in_this_rule), a
    ld hl, LOAD_SAVE_GAME_SCRIPT
    jp executeRuleScript_loop
update_pointer_space_pressed_over_verb_continue:
ENDIF
    ; change the pointer type:
    inc a
    ld (current_pointer_type),a
    ld (currently_selected_verb),a
    xor a
    ld (currently_selected_item_to_use),a
    ret

update_pointer_space_pressed_over_item:
    ; if action is "use", select the current item:
    push af
        ld a,(current_pointer_type)
        cp VERB_USE
        jr nz,update_pointer_space_pressed_over_item_not_use
        ld a,(currently_selected_item_to_use)
        or a
        jr nz,update_pointer_space_pressed_over_item_item_already_selected
    pop af
    inc a
    ld (currently_selected_item_to_use),a
    xor a
    ld (current_action_text_id),a
    jp update_command_preview_text_after_pointer_movement
update_pointer_space_pressed_over_item_not_use:
    pop af

    ; otherwise, execute action:
    ld c, a  ; selected item
    ld a, (current_pointer_type)
    cp VERB_EXAMINE
    jp z, update_pointer_examine_item
    
    cp VERB_PICK_UP
    jp z, update_pointer_pickup_item

    cp VERB_TALK
    jp z, update_pointer_talk_to_item

    ; clear the current action:
    jp reset_pointer
update_pointer_space_pressed_over_item_item_already_selected:
    pop af
    jp update_pointer_use_item_on_item


;-----------------------------------------------
; iy: ptr to the object to examine
update_pointer_space_pressed_over_object:
    ; execute an action:
    ld a, (current_pointer_type)

    cp VERB_EXIT
    jp z, update_pointer_exit_through_object

IF DOUBLE_CLICK_ON_EXIT == 1
    ; Only the verb EXIT gets affected by double clicks
    ld hl, double_click
    ld (hl), 0
ENDIF

    cp VERB_EXAMINE
    jp z, update_pointer_examine_object

    cp VERB_PICK_UP
    jp z, update_pointer_pickup_object

    cp VERB_USE
    jp z, update_pointer_use_object

    cp VERB_TALK
    jp z, update_pointer_talk_to_object

    ; clear the current action:
    jp reset_pointer


;-----------------------------------------------
move_pointer_speed:
    ld b, 1
    ld a, (keyboard_line_state + 3)
    bit 0, a
    jr z, move_pointer_speed_slow

    ld a, (pointer_move_time)
    cp 4
    ret m
    inc b
    cp 6
    ret m
    inc b
    inc b
    cp 8
    ret m
    inc b
    inc b
    ret

move_pointer_speed_slow:
    ld a, (pointer_move_time)
    and #01
    ret z
    ld b, 0
    ret


;-----------------------------------------------
move_pointer_right:
    push af
        call move_pointer_speed
        ld a, b
        or a
        jr z, move_pointer_right_done
        ld ixl, 1
        ld hl, pointer_x
        ld a, (hl)
move_pointer_right_loop:
        cp MAX_POINTER_X
        jr z, move_pointer_right_limit
        inc a
        djnz move_pointer_right_loop
move_pointer_right_limit:
        ld (hl), a
move_pointer_right_done:
    pop af
    ret

move_pointer_left:
    push af
        call move_pointer_speed
        ld a, b
        or a
        jr z, move_pointer_left_done
        ld ixl, 1
        ld hl, pointer_x
        ld a, (hl)
move_pointer_left_loop:
        cp MIN_POINTER_X
        jr z, move_pointer_left_limit
        dec a
        djnz move_pointer_left_loop
move_pointer_left_limit:
        ld (hl), a
move_pointer_left_done:
    pop af
    ret

move_pointer_down:
    push af
        call move_pointer_speed
        ld a, b
        or a
        jr z, move_pointer_down_done
        ld ixl, 1
        ld hl, pointer_y
        ld a, (hl)
move_pointer_down_loop:
        cp MAX_POINTER_Y
        jr z, move_pointer_down_limit
        inc a
        djnz move_pointer_down_loop
move_pointer_down_limit:
        ld (hl), a
move_pointer_down_done:
    pop af
    ret

move_pointer_up:
    push af
        call move_pointer_speed
        ld a, b
        or a
        jr z, move_pointer_up_done
        ld ixl, 1
        ld hl, pointer_y
        ld a, (hl)
move_pointer_up_loop:
        cp MIN_POINTER_Y
        jr z, move_pointer_up_limit
        dec a
        djnz move_pointer_up_loop
move_pointer_up_limit:
        ld (hl), a
move_pointer_up_done:
    pop af
    ret


;-----------------------------------------------
; Functions to trigger the actions of the specific verbs:
; c: item to examine
update_pointer_examine_item:
    push bc
        call clear_text_area
    pop bc

    ; create current event:
    ld hl,inventory_ids
    ld b,0
    add hl,bc
    ld a,(hl)  ; a now has the ID of the item we selected
    ld (current_event+1),a
    xor a
    ld (current_event+2),a

    ld a,TRIGGER_EXAMINE_ITEM
    ld (current_event),a

    ; if there is a rule that matches "examine", then trigger it:
    push hl
        call eventMatchesItemRule
        jp z, update_pointer_examine_item_success
    pop hl

    ; 2) Print the item description:
    ld a, (hl)
    dec a  ; a = item ID - 1 (index in the item_text_data array)
    add a, a
    ld hl, item_text_data + 2  ; item_text_data has 2bytes name + 2bytes description for each item
    ld b, 0
    ld c, a
    add hl, bc
    add hl, bc
    add hl, bc  ; hl now points to the idx of the item description!
    ld de, current_action_text_buffer
    ld c, (hl)
    inc hl
    ld a, (hl)
    call get_text_from_bank
    ld hl, current_action_text_buffer
    ld de, CHRTBL2 + 8 * ((32 - SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE)/2) + REGULAR_TEXT_FIRST_ROW * 32 * 8
    ld iyl, COLOR_WHITE * 16
    call draw_multi_line_sentence
    jp reset_pointer


;-----------------------------------------------
; iy: ptr to the object to examine
update_pointer_examine_object:
    push iy
        ; Clear the text area:
        call clear_text_area

        ; player starts moving in the direction of the object:
        ld a, TRIGGER_EXAMINE_OBJECT
        ld (current_event), a
        ld a,(iy + OBJECT_STRUCT_ID)
        ld (current_event + 1), a
        xor a
        ld (current_event + 2), a
        call player_request_movement_to_object
    pop iy
    jp reset_pointer


update_pointer_pickup_item:
    call clear_text_area
    ; error message:
    ld c, TAKE_FROM_INVENTORY_ERROR_MESSAGE_IDX / 256
    ld a, TAKE_FROM_INVENTORY_ERROR_MESSAGE_IDX % 256
update_pointer_pickup_item_error_entry_point:
    ld de, current_action_text_buffer
    call get_text_from_bank
    ld hl, current_action_text_buffer
    ld de, CHRTBL2 + 8 * ((32 - SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE) / 2) + REGULAR_TEXT_FIRST_ROW * 32 * 8
    ld iyl, COLOR_WHITE * 16
    jp draw_multi_line_sentence


update_pointer_talk_to_item:
    call clear_text_area
    ; error message:
    ld c, UNTALKABLE_ERROR_MESSAGE_IDX / 256
    ld a, UNTALKABLE_ERROR_MESSAGE_IDX % 256
    jr update_pointer_pickup_item_error_entry_point


update_pointer_pickup_object:
    call clear_text_area

    ; create current event:
    ld a,TRIGGER_PICK_UP_OBJECT
    ld (current_event),a
    ld a,(iy+OBJECT_STRUCT_ID)
    ld (current_event+1),a
    xor a
    ld (current_event+2),a
    call eventMatchesRule
    ; if there is a rule that matches "pick-up", it means it's a takeable object:
    jr z,update_pointer_pickup_object_success

    ; error message:
    ld c,UNTAKEABLE_ERROR_MESSAGE_IDX/256
    ld a,UNTAKEABLE_ERROR_MESSAGE_IDX%256
    jr update_pointer_pickup_item_error_entry_point
update_pointer_pickup_object_success:
    call player_request_movement_to_object
    jp reset_pointer


update_pointer_use_object:
    call clear_text_area

    ld a, (iy + OBJECT_STRUCT_ID)
    ld (current_event + 1), a

    ; check if we are using an item:
    ld a, (currently_selected_item_to_use)
    or a
    jr z, update_pointer_use_object_on_item
    ; create current event:
    dec a
    ld hl, inventory_ids
    ADD_HL_A_VIA_BC
    ld a, (hl)  ; a now has the ID of the item we selected
    ld (current_event + 2), a
    ld a, TRIGGER_USE_ITEM_WITH_OBJECT
    jr update_pointer_use_object_event_set
update_pointer_use_object_on_item:
    ; create current event:
    xor a
    ld (current_event + 2), a
    ld a, TRIGGER_USE_OBJECT
update_pointer_use_object_event_set:
    ld (current_event), a

    call eventMatchesRule
    ; if there is a rule that matches "use", then make the player move to the spot
    jr z, update_pointer_use_object_success

    ; error message:
    ld a, (current_event)
    cp TRIGGER_USE_ITEM_WITH_OBJECT
    jr z, update_pointer_use_item_failure    
    ld c, UNUSEABLE_ERROR_MESSAGE_IDX / 256
    ld a, UNUSEABLE_ERROR_MESSAGE_IDX % 256
    jr update_pointer_pickup_item_error_entry_point
update_pointer_use_object_success:
    call player_request_movement_to_object
    jp reset_pointer

update_pointer_use_item_on_item:
    push af
        call clear_text_area
    pop af

    ; create current event:
    ld hl,inventory_ids
    ADD_HL_A_VIA_BC
    ld a,(hl)  ; a now has the ID of the item we selected
    ld (current_event+1),a

    ld a,(currently_selected_item_to_use)
    dec a
    ld hl,inventory_ids
    ADD_HL_A_VIA_BC
    ld a,(hl)  ; a now has the ID of the item we selected
    ld (current_event+2),a

    ld a,TRIGGER_USE_ITEM_WITH_ITEM
    ld (current_event),a

    call eventMatchesItemRule
    ; if there is a rule that matches "use", then make the player move to the spot
    jr z,update_pointer_use_item_on_item_success

update_pointer_use_item_failure:
    ; Add the name of the item to (inventory_name_next_ptr)
    ld a, (current_event + 2)
    dec a  ; the item index is ID-1
    ld hl, item_text_data + 4
    add a, a
    ld b, a
    add a, a
    add a, b  ; a = a * 6
    ld b, 0
    ld c, a
    add hl, bc
    ; item-specific error message:
    ld c, (hl)  ; bank #
    inc hl
    ld a, (hl)  ; text # within the bank
    jp update_pointer_pickup_item_error_entry_point

update_pointer_examine_item_success:
    pop af  ; restore the stack (it had an "hl" pushed on to it)
update_pointer_use_item_on_item_success:
    call executeGlobalRuleScript
    jp reset_pointer

update_pointer_talk_to_object:
    call clear_text_area
    ; create current event:
    ld a,TRIGGER_TALK_TO_OBJECT
    ld (current_event),a
    ld a,(iy+OBJECT_STRUCT_ID)
    ld (current_event+1),a
    xor a
    ld (current_event+2),a
    call eventMatchesRule
    ; if there is a rule that matches "use", it means it's a takeable object:
    jr z,update_pointer_talk_to_object_success

    ; error message:
    ld c,UNTALKABLE_ERROR_MESSAGE_IDX/256
    ld a,UNTALKABLE_ERROR_MESSAGE_IDX%256
    jp update_pointer_pickup_item_error_entry_point
update_pointer_talk_to_object_success:
    call player_request_movement_to_object
    jp reset_pointer


update_pointer_exit_through_object:
    call clear_text_area
    push iy
        call reset_pointer
    pop iy
    ; create current event:
    ld a,TRIGGER_EXIT_THROUGH_OBJECT
    ld (current_event),a
    ld a,(iy+OBJECT_STRUCT_ID)
    ld (current_event+1),a
    xor a
    ld (current_event+2),a
    jp player_request_movement_to_object



;-----------------------------------------------
; Checks if the pointer is outside of the current room area
; returns:
; - z: if outside room area
; - nz: if inside (or in the GUI)
pointer_outside_of_room_area:
    ld a,(pointer_y)
    add a,3
    cp GUI_START_PIXEL_Y
    jp p,pointer_outside_of_room_area_gui

    ld ix,room_buffer

    ld a,(pointer_x)
    add a,4
    cp (ix+ROOM_STRUCT_START_X)
    jr c,pointer_outside_of_room_area_outside
    sub (ix+ROOM_STRUCT_START_X)
    srl a
    srl a
    srl a
    srl a
    cp (ix+ROOM_STRUCT_WIDTH)
    jr nc,pointer_outside_of_room_area_outside

    ld a,(pointer_y)
    add a,3
    cp (ix+ROOM_STRUCT_START_Y)
    jr c,pointer_outside_of_room_area_outside
    sub (ix+ROOM_STRUCT_START_Y)
    srl a
    srl a
    srl a
    cp (ix+ROOM_STRUCT_HEIGHT)
    jr nc,pointer_outside_of_room_area_outside

pointer_outside_of_room_area_gui:
    or 1
    ret
pointer_outside_of_room_area_outside:
    xor a
    ret


;-----------------------------------------------
; returns the index of the inventory item under the pointer
; return:
; - z: if the pointer is not over any inventory item
; - nz: if the pointer is over any inventory item ("a" will contain the index, and "hl" a pointer to the inventory index)
item_under_pointer:
    ld a, (pointer_x)
    add a, 4
    sub INVENTORY_START_X
    jr c, item_under_pointer_no_item
    cp INVENTORY_ITEMS_PER_LINE * 16  ; 16 pixels per item
    jr nc, item_under_pointer_no_item
    srl a
    srl a
    srl a
    srl a  ; a = item column
    ld c, a
    ld a, (pointer_y)
    add a, 4
    sub INVENTORY_START_Y
    jr c, item_under_pointer_no_item
    cp INVENTORY_ROWS * 16
    jr nc, item_under_pointer_no_item
    srl a
IF INVENTORY_ITEMS_PER_LINE = 8
    and #f8  ; A = ((pointer_y - INVENTORY_START_Y)/16) * INVENTORY_ITEMS_PER_LINE
ENDIF
IF INVENTORY_ITEMS_PER_LINE != 8
    srl a
    srl a
    srl a
    srl a
    jr z, item_under_pointer_first_row
    ld b, a
    xor a
item_under_pointer_row_loop:
    add a, INVENTORY_ITEMS_PER_LINE
    djnz item_under_pointer_row_loop
item_under_pointer_first_row:
ENDIF
    add a, c
    cp INVENTORY_SIZE
    jp p, item_under_pointer_no_item
    ; 'a' has the inventory position we just clicked:
    ld hl, inventory_ids
    ld b, 0
    ld c, a
    add hl, bc
    ld a, (hl)
    or a
    jr z, item_under_pointer_no_item
    or 1  ; we make sure to return "nz"
    ld a, c  ; we recover the inventory index
    ret
item_under_pointer_no_item:
verb_under_pointer_no_verb:
object_under_pointer_no_object:
    xor a
    ret


;-----------------------------------------------
; returns the verb the pointer is over
; input:
; - e: number of verbs to consider
; return:
; - z: if the pointer is not over any verb
; - nz: if the pointer is over a verb ("a" will contain the index, and "hl" the pointer to its name)
verb_under_pointer:
    ld a, (pointer_y)
    add a, 4
    sub GUI_START_PIXEL_Y
    jr c, verb_under_pointer_no_verb
    cp 2 * 16
    jr nc,verb_under_pointer_no_verb
    srl a
    srl a
    srl a
    srl a
    ld b, a
    ld a, (pointer_x)
    add a, 4
IF GUI_WIDTH < SCREEN_WIDTH_IN_TILES
    sub a, (SCREEN_WIDTH_IN_TILES - GUI_WIDTH) * 8
    jr c, verb_under_pointer_no_verb
ENDIF
    cp 3 * 16
    jr nc, verb_under_pointer_no_verb
    srl a
    srl a
    srl a
    srl a
    add a, b
    add a, b
    add a, b  ; we want to add "b*3"
    cp e  ; number of verbs to consider
    jr nc, verb_under_pointer_no_verb
    ld hl, action_name_ptrs
    ld b, 0
    ld c, a
    add hl, bc
    add hl, bc
    ld e, (hl)
    inc hl
    ld d, (hl)
    ex de, hl
    or 1  ; we make sure to return "nz"
    ld a,c
    ret


;-----------------------------------------------
; returns the object the pointer is over
; return:
; - z: if the pointer is not over any object
; - nz: if the pointer is over an object ("iy" the pointer to the object)
object_under_pointer:    
    ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
    or a
    jr z, object_under_pointer_no_object
    ld b, a
    ld c, -1  ; best "depth" (we want to keep the object with the highest depth)

    ld hl, room_buffer + ROOM_STRUCT_START_X
    ld e, (hl)
    inc hl
    ld d, (hl)

    ld a, (pointer_y)
    cp GAME_AREA_HEIGHT_IN_TILES * 8
    jr nc, object_under_pointer_no_object
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
object_under_pointer_loop:
    push bc
        ld a, (pointer_x)
        add a, 4
        sub e  ; room start x
        cp (ix + OBJECT_STRUCT_X)
        jp c, object_under_pointer_next_object
        cp (ix + OBJECT_STRUCT_X2)
        jp nc, object_under_pointer_next_object
        ld a, (pointer_y)
        add a, 3
        sub d  ; room start y
        cp (ix + OBJECT_STRUCT_Y)
        jr c, object_under_pointer_next_object
        cp (ix + OBJECT_STRUCT_Y2)
        jr nc, object_under_pointer_next_object

        ; check pixels, for pixel-perfect selection:
        ; get the object state pointer:
        call find_object_state_ptr
        inc hl
        call object_skip_to_current_animation_frame
        ld a, (hl)  ; which selection method should we use? none=0/pixel=1/box=2
        or a
        jr z, object_under_pointer_next_object  ; selection == none
        cp 2
        jr z, object_under_pointer_object_found_pop  ; selection == box

object_under_pointer_selection_mask:
        ; use the selection mask for pixel perfect selection:
        push de
            inc hl
            ld d, 0
            ld e, (hl)
            ex af, af'
                ld a, e  ; we save this for later
            ex af, af'
            inc hl
            ld a, (hl)
            inc hl
            push hl
                call mul8
                ld b, h
                ld c, l
            pop hl
        pop de
        add hl, bc    
        add hl, bc  ; hl now has the pointer to the selection mask
        
        ; now we need to check byte: 
        ; (((pointer_y)+3) - (ix+OBJECT_STRUCT_y)) * width_in_patterns + (((pointer_x)+4) - (ix+OBJECT_STRUCT_X))/8    
        ld a, (pointer_y)
        add a, 3
        sub d  ; room start y
        sub (ix + OBJECT_STRUCT_Y)
        push de
            ld d, 0
            ex af, af'
                ld e, a  ; we retrieve the width of the mask in tiles
            ex af, af'
            push hl
                call mul8
                ld b, h
                ld c, l
            pop hl
        pop de
        add hl, bc
        ld a, (pointer_x)
        add a, 4
        sub e  ; room start x
        ld c, a
        ld a, (ix + OBJECT_STRUCT_X)
        and #f8  ; we need to subtract the beginning of the first selection tile (which might be more to the left than the object)
        sub c
        neg

        ld b, 0
        ld c, a
        srl c
        srl c
        srl c  ; we divide the x offset by 8, since we move by tiles

        add hl, bc  ; hl now points to the proper byte in the collision mask to consider
        ld c, (hl)

        ; do pixel level collision:
        and #07  ; we keep with within tile offset
        jr z, object_under_pointer_selection_mask_loop_done
object_under_pointer_selection_mask_loop:
        sla c
        dec a
        jr nz, object_under_pointer_selection_mask_loop
object_under_pointer_selection_mask_loop_done:
        bit 7, c
        jr z, object_under_pointer_next_object


object_under_pointer_object_found_pop:
    pop bc
    ; clicked on object!
    ld a, (ix + OBJECT_STRUCT_DEPTH)
    cp c
    jp m, object_under_pointer_next_object_no_pop_bc
    ld c, a
    push ix
    pop iy  ; store the best object so far
    jr object_under_pointer_next_object_no_pop_bc

object_under_pointer_next_object:
    pop bc
object_under_pointer_next_object_no_pop_bc:
    push de
        ld de, OBJECT_STRUCT_SIZE
        add ix, de
    pop de
    dec b
    jp nz, object_under_pointer_loop
    ld a, c  ; if the best "depth" is -1, we have not selected any object!
    cp -1
    jp z, object_under_pointer_no_object

object_under_pointer_object_found:
    push iy
    pop ix
    or 1
    ret


;-----------------------------------------------
; Checks the content underneath the pointer in the screen, and updates
; the sentence that is being displayed on top of the playing area to show
; the player what is the action that will be performed if space is pressed
update_command_preview_text_after_pointer_movement:
    call item_under_pointer
    jr nz, update_command_preview_text_after_pointer_movement_item
    ld e, 4  ; ignore load/save
    call verb_under_pointer
    jp nz, update_command_preview_text_after_pointer_movement_verb
    call object_under_pointer
    jr nz, update_command_preview_text_after_pointer_movement_object
    ld a, (currently_selected_item_to_use)
    or a
    jr z, update_command_preview_text_after_pointer_movement_no_item_selected

    ; Reset action in case we were in an "exit" pointer state:
    ld a, (currently_selected_verb)
    ld (current_pointer_type), a

    ld a, #4f  ; special code for this action that is impossible otherwise
    ld hl, current_action_text_id
    cp (hl)
    ret z
    ld (hl), a

    ld hl, action_dotdotdot
    jp draw_preview_text

update_command_preview_text_after_pointer_movement_no_item_selected:
    call clear_command_preview_text
    jp change_pointer_back_to_cross_if_no_verb

update_command_preview_text_after_pointer_movement_item:
    push af
        or #80  ; inventory item code
        ld hl, current_action_text_id
        cp (hl)
        jr nz, update_command_preview_text_after_pointer_movement_item_new_text
    pop af
    ret
update_command_preview_text_after_pointer_movement_item_new_text:
        ld (hl), a
        ; if we have no selected verb, pointer changes to "examine":
        call change_pointer_to_default_verb_if_no_verb_force_examine
    pop af

    ; get the item name, and print it in the text area:
    call get_ptr_to_item_name
    jp draw_preview_text

update_command_preview_text_after_pointer_movement_verb:
    push af
    push hl
        or #40  ; verb code
        ld hl, current_action_text_id
        cp (hl)
        jr nz, update_command_preview_text_after_pointer_movement_verb_new_text
    pop hl
    pop af
    jr change_pointer_back_to_cross_if_no_verb
update_command_preview_text_after_pointer_movement_verb_new_text:
        ld (hl), a
    pop hl
    pop af
    jp draw_preview_text_directly

update_command_preview_text_after_pointer_movement_object:
    ld a, (iy + OBJECT_STRUCT_ID)
    or #c0  ; object code
    ld hl, current_action_text_id
    cp (hl)
    ret z
    ld (hl), a
    ; if we are over an object that is in an "exit" state, change to exit:
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    and #0f  ; get the state
    cp OBJECT_STATE_EXIT
    jr nz, update_command_preview_text_after_pointer_movement_object_no_exit
    ld hl, current_pointer_type
    ld (hl), VERB_EXIT
    jr update_command_preview_text_after_pointer_movement_object_after_pointer_set
update_command_preview_text_after_pointer_movement_object_no_exit:
    ; if we have no selected verb, pointer changes to the default verb ("examine" in normal rooms):
    call change_pointer_to_default_verb_if_no_verb
update_command_preview_text_after_pointer_movement_object_after_pointer_set:
    ld l, (iy + OBJECT_STRUCT_NAME_PTR)
    ld h, (iy + OBJECT_STRUCT_NAME_PTR + 1)
    jp draw_preview_text


change_pointer_to_default_verb_if_no_verb:
    ld a, (room_buffer + ROOM_STRUCT_ROOM_FLAGS)
    and ROOM_FLAG_DEFAULT_VERB_USE
    jr z, change_pointer_to_default_verb_if_no_verb_examine
    ld c, VERB_USE
    jr change_pointer_to_default_verb_if_no_verb_continue
change_pointer_to_default_verb_if_no_verb_force_examine:
change_pointer_to_default_verb_if_no_verb_examine:
    ld c, VERB_EXAMINE
change_pointer_to_default_verb_if_no_verb_continue:
    ld a, (currently_selected_verb)
    or a
    ld hl, current_pointer_type
    jr nz, change_pointer_to_default_verb_if_no_verb_there_is_a_verb
    ld (hl), c
    ret
change_pointer_to_default_verb_if_no_verb_there_is_a_verb:
    ld (hl), a
    ret


change_pointer_back_to_cross_if_no_verb:
    ld a, (exit_upon_clicking_outside)
    or a
    jr z, change_pointer_back_to_cross_if_no_verb_no_need_for_exit_pointer
    call pointer_outside_of_room_area
    jr nz, change_pointer_back_to_cross_if_no_verb_no_need_for_exit_pointer
    ld a, VERB_EXIT
    jr change_pointer_back_to_cross_if_no_verb_assign_pointer_type

change_pointer_back_to_cross_if_no_verb_no_need_for_exit_pointer:
    ld a, (currently_selected_verb)
change_pointer_back_to_cross_if_no_verb_assign_pointer_type:
    ld hl, current_pointer_type
    ld (hl), a
    ret


;-----------------------------------------------
; input:
; - a: item index
; output:
; - hl: pointer to the item name
get_ptr_to_item_name:
    ld hl, inventory_name_ptrs
    ld b, 0
    ld c, a
    add hl, bc
    add hl, bc  ; hl has now the pointer to the item name
    ld e, (hl)
    inc hl
    ld d, (hl)
    ex de, hl
    ret


;-----------------------------------------------
init_current_action_text_with_current_verb:
    ; determine the verb name:
    ld a, (current_pointer_type)
    ld hl, action_name_ptrs
    dec a
    ld b, 0
    ld c, a
    add hl, bc
    add hl, bc
    ld e, (hl)
    inc hl
    ld d, (hl)
    ex de, hl
    ld c, (hl)
    inc c  ; we need to copy one extra byte (the length of the string)
    ld de, current_action_text_buffer
    ldir  ; we first copy the verb
    ret


;-----------------------------------------------
; draws the preview text, and then clears the rest of the line
; - "hl": pointer to the name of the object/item under the pointer
draw_preview_text:
    ; construct the sentence:
    push hl
        call init_current_action_text_with_current_verb
    pop hl
    ; now copy the object name:
    ; check if we have an item selected:
    ld a, (current_pointer_type)
    cp VERB_EXIT
    jr z, draw_preview_text_no_selected_item  ; no item usage with an exit
    ld a, (currently_selected_item_to_use)
    or a
    jr z,draw_preview_text_no_selected_item
    ; copy the name of the selected item:
    push hl
        push de
            dec a  ; get the idem #
            call get_ptr_to_item_name
        pop de
        ld a,(hl)
        ld c,a
        inc hl
        ldir
        ld hl,current_action_text_buffer
        add a,(hl)  ; we add the length of the item name + length of the verb
        ld (hl),a
        
        ; add " with " to the string:
        ld hl,action_with
        ld a,(hl)
        ld c,a
        inc hl
        ldir
        ld hl,current_action_text_buffer
        add a,(hl)  ; we add the length of " with "
        ld (hl),a
    pop hl

draw_preview_text_no_selected_item:
    ld a, (hl)
    ld c, a
    inc hl
    ldir
    ld hl, current_action_text_buffer
    add a, (hl)  ; we add the length of the object name + length of the verb
    ld (hl), a
draw_preview_text_directly:  ; if we use this entry point, "hl" contains the string to draw directly
    ; "a" here has the number of bytes we need to clear
    ld de, CHRTBL2+8*((32-SCREEN_WIDTH_IN_TILES*MSX_TILES_PER_ENGINE_TILE)/2)
    ld iyl, COLOR_LIGHT_GREEN*16
    jp draw_sentence

