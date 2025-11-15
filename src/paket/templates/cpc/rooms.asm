;-----------------------------------------------
; decompresses a room into the memory buffers, also decompressing the necessary tiles, objects and object types
; - hl: room to load
load_room:
    ; decompress the room:
    ld de, general_buffer
    call PAKET_UNPACK

IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 1
    ld hl, 0
    ld (custom_assembler_room_draw), hl
ENDIF

    ; skip as many rooms as needed:
IF ROOMS_PER_BANK == 2
    ld hl, general_buffer
    ld a, (current_room)
    and #01
    jr z, load_room_room_found
    ld bc, (general_buffer)  ; room size
    add hl, bc
load_room_room_found:
    inc hl
    inc hl
ELSE
    ld hl, general_buffer
ENDIF

    ; copy the beginning of the room (dimensions, background, and collision mask)
    push hl
        ld de, room_buffer
        ld bc, ROOM_STRUCT_COLLISION_MASK  ; we might be copying more than necessary for a room smaller than 
                                           ; MAX_ROOM_WIDTH*MAX_ROOM_HEIGHT, but it's fine
        ldir

        ; calculate the size of the room data:
        ld a, (room_buffer+ROOM_STRUCT_WIDTH)
        ld d, 0
        ld e, a
        ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
        call mul8
        ld b, h
        ld c, l
    pop hl

    ; copy the collision mask:
    ld de, ROOM_STRUCT_BACKGROUND
    add hl, de
    ; ld hl, general_buffer + ROOM_STRUCT_BACKGROUND
    add hl, bc  ; hl now points to the collision mask data
    ld de, room_buffer + ROOM_STRUCT_COLLISION_MASK
    ldir

    ; copy the rules data:
    ld c, (hl)  ; size of the rules data
    inc hl
    ld b, (hl)
    inc hl
    ld de, room_buffer + ROOM_STRUCT_RULES
    ldir

    ; on room load/start rules data:
    ld c, (hl)  ; size of the on room load rules data. This includes both the on
    inc hl      ; load and on start rules. There will be a # of on-load rules, 
    ld b, (hl)  ; followed by those rules, then a # of on-start rules, followed
    inc hl      ; by those rules.
    ld de, room_specific_on_load_or_start_rules_buffer
    ldir

    ; copy the object data directly to the object area:
    ld de, room_buffer + ROOM_STRUCT_OBJECT_DATA
    ld bc, MAX_OBJECTS_PER_ROOM*OBJECT_STRUCT_SIZE
    ldir

    ; load the necessary object types, and replace object type numbers by object type pointers:
    ; clear the cache of already loaded object type pointers
    ld a, #ff
    ld hl, general_buffer
    ld de, general_buffer + 1
    ld bc, ROOM_LOAD_BUFFER_ALLOCATED_SPACE - 1
    ld (hl), a
    ldir
    ld bc, general_buffer
    ld (object_type_loaded_cache_next_ptr), bc

    ld bc, tiles_and_object_type_data_buffer
    ld (object_type_data_buffer_next_ptr), bc  ; we reset the pointer to the next available space on the object type buffer
    ld bc, room_object_name_buffer
    ld (room_object_name_buffer_next_ptr), bc
    ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA

    or a
    jp z, load_room_load_object_types_loop_done
load_room_load_object_types_loop:
    push af
        ld (last_room_object_ptr), ix  ; we cache the pointer to the last object in the current room
        ld a, (ix + OBJECT_STRUCT_TYPE_PTR)  ; object type to load
        push ix
            ld e, (ix + OBJECT_STRUCT_NAME_PTR)  ; save the index of the object name in "de"
            ld d, (ix + OBJECT_STRUCT_NAME_PTR + 1)
            ; Check if we have it loaded already:
            ld hl, general_buffer
            ld b, MAX_OBJECT_TYPES_PER_ROOM
load_room_load_object_types_loop_check_if_loaded_loop:
            cp (hl)
            jr z, load_room_load_object_types_loop_already_loaded
            inc hl
            inc hl
            inc hl
            inc hl
            inc hl
            djnz load_room_load_object_types_loop_check_if_loaded_loop
            jr load_room_load_object_types_loop_not_loaded
load_room_load_object_types_loop_already_loaded:
            inc hl
            ld e, (hl)
            inc hl
            ld d, (hl)
            inc hl
            ld c, (hl)
            inc hl
            ld b, (hl)  ; de = pointer to the preloaded type, bc = pointer to the preloaded name
            jp load_room_load_object_types_loop_save_ptr_in_room_structure

load_room_load_object_types_loop_not_loaded:
            ; Otherwise, load it:
            push de  ; save the index of the object name text
                ; start by setting the object type on the cache structure:
                ld bc, (object_type_loaded_cache_next_ptr)
                ld (bc), a
                inc bc
                ld (object_type_loaded_cache_next_ptr),bc
IF IS_6128 == 1
                SET_128K_PAGE_4000 OBJECT_TYPE_BANK_PTRS_PAGE
ENDIF
                push af
                    ; Get the object type bank where the object is located:
                    srl a
                    srl a  ; a = object type bank #
                    ld hl, objectTypeBanksPointers
                    or a
                    jr z, load_room_load_object_types_loop_object_type_bank_ptr_loop_done
load_room_load_object_types_loop_object_type_bank_ptr_loop:
                    inc hl
                    inc hl
IF IS_6128 == 1
                    inc hl  ; if it's a 6128, we also store the page number (followed by a 0)
                    inc hl
ENDIF
                    dec a
                    jr nz, load_room_load_object_types_loop_object_type_bank_ptr_loop
load_room_load_object_types_loop_object_type_bank_ptr_loop_done:
                    ld e, (hl)
                    inc hl
                    ld d, (hl)
IF IS_6128 == 1
                    inc hl
                    ld a, (hl)  ; RAM page
                    SET_128K_PAGE_4000_A
ENDIF
                    ex de, hl  ; hl = pointer to the object type bank we want
                    ld de, general_buffer+ROOM_LOAD_BUFFER_ALLOCATED_SPACE
                    call PAKET_UNPACK
load_room_load_object_types_loop_object_type_bank_is_same_as_already_loaded:
                pop af
                and #03  ; a = object type # within the bank we just decompressed

                ; copy it over to the object type buffer:
                ld hl, general_buffer+ROOM_LOAD_BUFFER_ALLOCATED_SPACE
                or a
                jr z, load_room_load_object_types_loop_object_type_ptr_loop_done
load_room_load_object_types_loop_object_type_ptr_loop:
                ld c, (hl)
                inc hl
                ld b, (hl)
                inc hl
                add hl, bc
                dec a
                jr nz, load_room_load_object_types_loop_object_type_ptr_loop
load_room_load_object_types_loop_object_type_ptr_loop_done:
                ld c, (hl)
                inc hl
                ld b, (hl)
                inc hl
                ld de, (object_type_data_buffer_next_ptr)
                push de
                    ldir
                    ld (object_type_data_buffer_next_ptr), de
                pop de
                ; save the pointer to the cache:
                ld hl, (object_type_loaded_cache_next_ptr)
                ld (hl), e
                inc hl
                ld (hl), d
                inc hl
            pop bc    ; we had pushed "de", but we get it now into "bc", since we want to preserve "de"

            ; load the object name:
            ld a, b
            push de
            push hl
                ld de, (room_object_name_buffer_next_ptr)
                call get_text_from_bank
                ld bc, (room_object_name_buffer_next_ptr)
                ld (room_object_name_buffer_next_ptr), de
            pop hl
            pop de
            ; save pointer to the object name:
            ld (hl), c
            inc hl
            ld (hl), b
            inc hl
            ld (object_type_loaded_cache_next_ptr), hl

load_room_load_object_types_loop_save_ptr_in_room_structure:
        pop ix
        ; set the pointer in the room structure:
        ld (ix + OBJECT_STRUCT_TYPE_PTR), e
        ld (ix + OBJECT_STRUCT_TYPE_PTR + 1), d
        ; save the name (which is in "bc")
        ld (ix + OBJECT_STRUCT_NAME_PTR), c
        ld (ix + OBJECT_STRUCT_NAME_PTR + 1), b

        ld bc, OBJECT_STRUCT_SIZE
        add ix, bc
    pop af
    dec a
    jp nz, load_room_load_object_types_loop
load_room_load_object_types_loop_done:

    ; decompress the tiles necessary for the room:
    ld bc, (object_type_data_buffer_next_ptr)
    ld (tile_buffer_ptr), bc
    call decompress_room_tiles

IF PLAYER_SCALING = 1
    ld a, (room_buffer + ROOM_STRUCT_ROOM_FLAGS)
    srl a
    and #0e
    ld hl, player_zoom_draw_function_pointers
    ld b, 0
    ld c, a
    add hl, bc
    ld e, (hl)
    inc hl
    ld d, (hl)
    ld (player_zoom_draw_function_ptr), de
ENDIF

    ; set the current room palette:
    ld hl, game_palettes
    ld a, (room_buffer + ROOM_STRUCT_PALETTE_ID)
    add a, a
    ld b, 0
    ld c, a
    add hl, bc
    ld e, (hl)
    inc hl
    ld d, (hl)
    ld (current_palette_ptr), de
    ex de, hl
    call set_palette_16

    call find_player_object_ptr
    call clear_dirty_min_max_y

    ; check for exits upon clicking outside
    xor a
    ld (exit_upon_clicking_outside), a
    ld a, TRIGGER_CLICK_OUTSIDE_ROOM_AREA
    ld (current_event), a
    xor a
    ld (current_event + 1), a
    ld (current_event + 2), a
    call eventMatchesRule
    jr nz, adjust_room_using_persistent_state

    ; rule matched!
    ld a, 1
    ld (exit_upon_clicking_outside), a
    jr adjust_room_using_persistent_state


;-----------------------------------------------
; After loading a room, this function makes sure the state of the room is
; consistent with the "persistent state". This is achieved via on_room_load rules.
adjust_room_using_persistent_state:
IF IS_6128 == 1
    SET_128K_PAGE_4000 RULES_AND_SCRIPTS_DATA_PAGE
ENDIF    
    ld hl, room_specific_on_load_or_start_rules_buffer
    call adjust_room_using_persistent_state_rules
    ld hl, global_rules_on_room_load
adjust_room_using_persistent_state_rules:
    ld a, (hl)
    inc hl
    or a
adjust_room_using_persistent_state_rules_loop:
    ret z
    push af
        push hl
            inc hl  ; skip rule size
            call checkForTriggersSatisfied
            jr z, adjust_room_using_persistent_state_rules_match
adjust_room_using_persistent_state_rules_loop_next_rule:
        pop hl
        ld b, 0
        ld c, (hl)
        add hl, bc
    pop af
    dec a
    jr adjust_room_using_persistent_state_rules_loop

adjust_room_using_persistent_state_rules_match:
    pop hl
    push hl
        ; skip rule size (1 byte) and the trigger:
        ; TODO: this code assumes there is only one trigger in the rule.
        ;       Check to see if this is problematic.
        inc hl
        call skipTrigger
        call executeGlobalRuleScript
        jr adjust_room_using_persistent_state_rules_loop_next_rule


;-----------------------------------------------
adjust_room_using_persistent_state_on_room_start_rules:
    ld hl, room_specific_on_load_or_start_rules_buffer
    ld a, (hl)
    inc hl
    or a
    jr z, adjust_room_using_persistent_state_on_room_start_rules_start_found
    ld b, a
    ; Skip all the on room load rules, to reach the on room start ones:
adjust_room_using_persistent_state_on_room_start_rules_loop:
    ld d, 0
    ld e, (hl)  ; rule size
    add hl, de  ; skip rule
    djnz adjust_room_using_persistent_state_on_room_start_rules_loop
adjust_room_using_persistent_state_on_room_start_rules_start_found:
    jr adjust_room_using_persistent_state_rules


;-----------------------------------------------
; input:
; - c,b: x, y coordinates to check
; output:
; - z: no collision
; - nz: collision
check_collision_mask:
    ; check out of bounds x collision:
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    add a, a
    add a, a
    add a, a  ; a = room width in pixels
    cp c
    jr nc, check_collision_mask_inside_x_bounds
    or 1  ; collision
    ret
    
check_collision_mask_inside_x_bounds:
    ; check out of bounds y collision:
    ld a, (room_buffer+ROOM_STRUCT_HEIGHT)
    add a, a
    add a, a
    add a, a  ; a = room height in pixels
    cp b
    jr nc, check_collision_mask_inside_y_bounds
    or 1  ; collision
    ret
    
check_collision_mask_inside_y_bounds:
check_collision_mask_for_path_finding:
check_collision_mask_for_path_finding_a_set:
    call collision_mask_ptr_and_mask
    ld a, (hl)  ; a = collision mask of the tile we need to check collision with
    and e
    ret


;-----------------------------------------------
; input:
; - c: x coordinate (in pixels)
; - b: y coordinate (in pixels)
; output:
; - hl: ptr to the collision mask tile
; - e: mask for the bit we need to check
collision_mask_ptr_and_mask:
    ; calculate the offset in the collision mask: 
    ; offset = (room_buffer+ROOM_STRUCT_COLLISION_MASK)+(x/8)*room_height + (y/8)
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld d, 0
    ld e, a
    ld a, c    ; x
    srl a
    srl a
    srl a
    push bc
        call mul8  ; hl = (x/8)*room_height
    pop bc
    ld e, b
    srl e
    srl e
    srl e
    ld d, 0
    add hl, de
    ld de, room_buffer + ROOM_STRUCT_COLLISION_MASK
    add hl, de

    ; check the proper bit:
    ; (y&#04) + ((x/2)&#03)
    ; so, we will calculate a mask:
    ; (y&04)*4 << ((x/2)&#03)
    bit 2, b
    jr z,check_collision_mask_bottom_row
check_collision_mask_top_row:
    ld e, 16
    jr check_collision_mask_row_determined
check_collision_mask_bottom_row:
    ld e, 1
check_collision_mask_row_determined:
    ld a, c
    srl a
    and #03
    ret z
check_collision_mask_mask_loop:
    sla e
    dec a
    jr nz, check_collision_mask_mask_loop
    ret


;-----------------------------------------------
IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 1
draw_room_any_dirty_p:
    ld b, MAX_ROOM_WIDTH * 4
    ld hl, dirty_column_buffer
draw_room_any_dirty_p_loop:
    ld a, (hl)
    or a
    ret nz
    inc hl
    djnz draw_room_any_dirty_p_loop
    ret
    

draw_room_dirty_columns:
    ld a, (custom_assembler_room_draw_fullredraw_only)
    or a
    jr nz, draw_room_dirty_columns_internal
    ld hl, (custom_assembler_room_draw)
    ld a, h
    or l
    jr z, draw_room_dirty_columns_internal
    push hl
        call draw_room_any_dirty_p
        jr z, draw_room_dirty_columns_skip
        call draw_room_dirty_columns_internal
    pop hl
    jp (hl)
draw_room_dirty_columns_skip:
    pop hl
    ret
ENDIF


;-----------------------------------------------
draw_room:
IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 1
    ld hl, (custom_assembler_room_draw)
    ld a, h
    or l
    jr z, draw_room_internal
    push hl
        call draw_room_internal
    pop hl
    jp (hl)
ENDIF


;-----------------------------------------------
; draws a complete room from scratch
draw_room_internal:
    ; mark all the columns as dirty:
    ld a, 1
    ld b, MAX_ROOM_WIDTH * 4
    ld hl, dirty_column_buffer
draw_room_loop:
    ld (hl), a
    inc hl
    djnz draw_room_loop
    ; now draw the room:
IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 0
draw_room_dirty_columns:
ENDIF
draw_room_dirty_columns_internal:
    ld c, 0
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld b, a
    jr draw_room_dirty_columns_from_c_to_b


;-----------------------------------------------
clear_dirty_min_max_y:
    ; clear the min/max y variables:
    ld hl, dirty_min_y
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld (hl), a
    inc hl  ; dirty_max_y
    ld (hl), 0
    ret


;-----------------------------------------------
; redraws only the dirty columns of a room
; and only the rows that collide with the player
draw_room_dirty_columns_player_height:
    ld a, (player_in_room)
    or a
    jr z, draw_room_dirty_columns
    call get_player_ptr
    ld a, (ix + OBJECT_STRUCT_Y2)
    srl a
    srl a
    srl a
    add a, 2  ; we draw a bit more below to prevent garbage in the screen
    ld b, a  ; we store (player_y2/8)
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    cp b
    ; if a < b, we need to decrement b
    jr nc, draw_room_dirty_columns_player_height_no_overflow
    ld b, a
draw_room_dirty_columns_player_height_no_overflow:

    ; if (dirty_max_y) > b: b = (dirty_max_y)
    ld a, (dirty_max_y)
    cp b
    jr c, draw_room_dirty_columns_player_height_no_b_adjust
    ld b, a
draw_room_dirty_columns_player_height_no_b_adjust:

    ld a, (ix + OBJECT_STRUCT_Y)
    srl a
    srl a
    srl a
    dec a  ; -1 to avoid leaving garbage in the screen when moving down
    ld c, a  ; we store (player_y / 8)

IF PLAYER_SCALING = 1
    ld a, (player_scale)
    inc a
    jr nz, draw_room_dirty_columns_player_height_no_adjust_due_to_scaling
    dec c  ; If player is scaled up, add extra space at the top
draw_room_dirty_columns_player_height_no_adjust_due_to_scaling:
ENDIF

    ld a, c
    or a
    jp p,draw_room_dirty_columns_player_height_c_positive
    ld c, 0  ; If the minimum y is negative, we just set it to 0, to prevent
             ; trying to draw outside the screen area.
draw_room_dirty_columns_player_height_c_positive:

    ; if (dirty_min_y) < c: c = (dirty_min_y)
    ld a, (dirty_min_y)
    cp c
    jr nc, draw_room_dirty_columns_player_height_no_c_adjust
    ld c, a
draw_room_dirty_columns_player_height_no_c_adjust:

    ; Switch from "c -> b" to "c -> c + b"
    ld a, b
    sub c
    ld b, a

    call clear_dirty_min_max_y
    ; jr draw_room_dirty_columns_from_c_to_b


;-----------------------------------------------
; redraws only the dirty columns of a room
; and only redraws rows between c to c + b ("b" and "c" are in "tiles")
draw_room_dirty_columns_from_c_to_b:
    ld ixl, c  ; first row
    ld ixh, b  ; n rows to draw

    ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_Y)
    add a, c
    ld de, SCREEN_WIDTH_IN_BYTES
    call mul8  ; hl = de * a
    ld d, 0
    ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_X)
    ld (current_column_x), a
    ld e, a
    add hl, de
    ld de, VIDEO_MEMORY
    add hl, de
    ex de, hl  ; video mem address to start drawing

    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND  ; hl has the pointer to the tiles to draw
    ld a, ixl
    ld b, 0
    ld c, a
    add hl, bc
    ld b, h
    ld c, l

    ld hl, dirty_column_buffer
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    add a, a
    add a, a    
    ld iyl, a  ; iyl has the # of columns to draw
    xor a

draw_room_dirty_columns_loop:
    push af
        ld a, (hl)
        or a
        jp z, draw_room_dirty_columns_loop_next_column
        dec (hl)  ; clear the dirty mark
    pop af

    push af
        ; draw column: --------------------------
        push bc
        push de
        push hl
            ; Draw tiles:
            push af
                and #03  ; within tile column
                sla a
                sla a
                sla a    
                ld iyh, a  ; iyh = within tile column * 8
                exx
                    ; Add (ixl)*8
                    ld hl, column_draw_buffer
                    ld a, ixl
                    add a, a
                    add a, a
                    add a, a
                    ld d, 0
                    ld e, a
                    add hl, de
                    ex de, hl
                exx
                ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
                ld l, a

                ld a, ixh
                ld l, a  ; number of rows to draw

draw_room_dirty_columns_tiles_loop:
                ld a, (bc)
                exx
                    ; or a
                    ; jr z, draw_room_dirty_columns_tiles_loop_empty_tile
                    ld h, 0
                    ld l, a
                    ld b, h
                    ld c, iyh  ; iyh = within tile column * 8
                    add hl, hl
                    add hl, hl
                    add hl, hl
                    add hl, hl
                    add hl, hl  ; hl = (# tile)*32
                    add hl, bc  ; add the column within this tile
                    ld bc, (tile_buffer_ptr)
                    add hl, bc  ; hl = ptr to the tile to draw
                    ldi
                    ldi
                    ldi
                    ldi
                    ldi
                    ldi
                    ldi
                    ldi    
;draw_room_dirty_columns_tiles_loop_back_from_empty_tile:
                exx
                inc bc
                dec l
                jr nz, draw_room_dirty_columns_tiles_loop
            pop af
            ld c, a  ; c = # of column we are drawing

            ; Draw objects:
            push ix
                ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
                or a
                jp z, draw_room_dirty_columns_objects_loop_done
                ld b, a
                ld hl, room_buffer + ROOM_STRUCT_OBJECT_DATA
                ld de, OBJECT_STRUCT_SIZE
draw_room_dirty_columns_objects_loop:
                push hl
                pop ix

                ; check if we need to draw the object, or it is hidden:
                ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
                and #0f
                cp OBJECT_STATE_HIDDEN  ; hidden objects are not drawn
                jp z, draw_room_dirty_columns_skip_object

                ld a, (hl)
                srl a  ; a = o.x2 / 2
                sub c
                jp c, draw_room_dirty_columns_skip_object
                jp z, draw_room_dirty_columns_skip_object
                inc hl
                ld a, (hl)
                dec hl
                srl a  ; a = o.x / 2
                sub c
                jr z, draw_room_dirty_columns_draw_object
                jr nc, draw_room_dirty_columns_skip_object
                neg
draw_room_dirty_columns_draw_object:
                exx
                    ; Calculate the pointer where to draw:
                    ld de, column_draw_buffer
                    ld h, 0
                    ld l, (ix + OBJECT_STRUCT_Y)
                    add hl, de
                    ex de, hl

                    ; 'a' contains the column to draw:
                    ld l, (ix + OBJECT_STRUCT_TYPE_PTR)
                    ld h, (ix + OBJECT_STRUCT_TYPE_PTR + 1)
draw_room_dirty_columns_objects_state_loop:
                    ld b, a
                    ld a, (hl)  ; state-direction

                    inc hl
                    cp (ix + OBJECT_STRUCT_STATE_DIRECTION)
                    ld a, b
                    jr nz, draw_room_dirty_columns_objects_wrong_state
                    ; Draw object:
                    call object_skip_to_current_animation_frame  ; preserves 'a'
                    ld b, (hl)  ; image selection mask type
                    bit 7, b  ; if this bit is 1, this state has an empty image
                    jr nz, draw_room_dirty_columns_objects_state_column_found_draw_loop_done
                    inc hl  ; skip image selection mask
                    ld b, 0
                    cp (hl)  ; compare column to draw with image width
                    jr nc, draw_room_dirty_columns_objects_state_column_found_draw_loop_done
                    inc hl  ; skip image width
                    ld c, (hl)  ; image height
                    inc hl
                    or a  ; a still contains the column to draw
                    jr z, draw_room_dirty_columns_objects_state_column_found
draw_room_dirty_columns_objects_state_column_loop:
                    add hl, bc
                    dec a
                    jr nz, draw_room_dirty_columns_objects_state_column_loop
draw_room_dirty_columns_objects_state_column_found:
                    ; ---- draw object column (pixel transparency) ----:
IF PLAYER_SCALING = 1
                    ld a, (ix + OBJECT_STRUCT_ID)
                    cp PLAYER_OBJECT_ID
                    jr nz, draw_room_dirty_columns_objects_state_column_found_draw_loop
                    ld a, c  ; preserve "c"
                    ld bc, (player_zoom_draw_function_ptr)
                    push bc
                    ld c, a
                    ret  ; jump to the "player_zoom_draw_function_ptr" function
ENDIF
draw_room_dirty_columns_objects_state_column_found_draw_loop:
                    ld a, (hl)
                    or a
                    jr z, draw_room_dirty_columns_objects_state_column_found_skip_pixel
                    ld b, a
                    and #55  ; check if the first pixel is transparent
                    ld a, b
                    jr nz, draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent
                    ; a = (de) & #55 + a & #aa
                    and #aa
                    ld b, a
                    ld a, (de)
                    and #55
                    add a, b
                    ; if the first pixel is transparent, the second cannot be, so, just draw it:
                    ld (de), a
                    inc hl
                    inc de
                    dec c
                    jr nz, draw_room_dirty_columns_objects_state_column_found_draw_loop
                    jr draw_room_dirty_columns_objects_state_column_found_draw_loop_done
                    
                    ; Note: this code does not belong here, it's just here,
                    ; since it's the only place where I can fit it and still
                    ; keep a "jr" instead of a "jp" :)
draw_room_dirty_columns_objects_wrong_state:
                    ld c, (hl)
                    inc hl
                    ld b, (hl)
                    inc hl
                    add hl, bc
                    jr draw_room_dirty_columns_objects_state_loop

draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent:
                    and #aa  ; check if the second pixel is transparent:
                    ld a, b  ; b still contains the original pixel
                    jr nz, draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent
                    ; a = (de) & #aa + a & #55
                    and #55
                    ld b, a
                    ld a, (de)
                    and #aa
                    add a, b
draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent:
                    ld (de), a
draw_room_dirty_columns_objects_state_column_found_skip_pixel:
                    inc hl
                    inc de
                    dec c
                    jr nz, draw_room_dirty_columns_objects_state_column_found_draw_loop
draw_room_dirty_columns_objects_state_column_found_draw_loop_done:
                exx
draw_room_dirty_columns_skip_object:
                add hl, de
                dec b
                jp nz, draw_room_dirty_columns_objects_loop
                ; djnz draw_room_dirty_columns_objects_loop
draw_room_dirty_columns_objects_loop_done:
            pop ix
        pop hl
        pop de
        pop bc

        ; copy column to video memory: --------------------------
        ; Note: this code assumes that rooms are always drawn on a y coordinate
        ; multiple of 8 pixels! Remember that we are drawing rows from ixl to ixl + ixh.
        push de
        exx
            ld hl, column_draw_buffer
            ld a, ixl
            add a, a
            add a, a
            add a, a
            ld d, 0
            ld e, a
            add hl, de
            ex de, hl

            pop hl

            ld a, ixh  ; number of rows to draw

; This debug code draws the room slowly, and with a green line scrolling through, so that it can be seen visually.
;            push af
;            push de
;            push hl
;draw_room_dirty_columns_loop_loop1_debug:
;            ex af, af'
;                ld bc, #0808  ; b = 8 for doing 8 iterations of the loop, and c = 8 as the increment we need to do to the MSB of the video mem pointer
;draw_room_dirty_columns_loop_loop2_debug:
;                ld (hl), #ff
;                ld a, c
;                add a, h
;                ld h, a
;                inc de
;                djnz draw_room_dirty_columns_loop_loop2_debug
;                ld bc, #c000 + SCREEN_WIDTH_IN_BYTES
;                add hl, bc
;            ex af, af'
;            dec a
;            jr nz, draw_room_dirty_columns_loop_loop1_debug
;            pop hl
;            pop de
;            ld bc, 10
;            call wait_bc_halts
;            pop af

draw_room_dirty_columns_loop_loop1:
            ex af, af'
                ld bc, #0808  ; b = 8 for doing 8 iterations of the loop, and c = 8 as the increment we need to do to the MSB of the video mem pointer
draw_room_dirty_columns_loop_loop2:
                ld a, (de)
                ld (hl), a
                ld a, c
                add a, h
                ld h, a
                inc de
                djnz draw_room_dirty_columns_loop_loop2
                ld bc, #c000 + SCREEN_WIDTH_IN_BYTES
                add hl, bc
            ex af, af'
            dec a
            jr nz, draw_room_dirty_columns_loop_loop1

            ; check if we need to overwrite the pointer_background_buffer:
            ; xoffs = current_column_x - pointer_background_buffer_x
            ; if 0 <= xoffs < POINTER_WIDTH_IN_BYTES:
            ;     yoffs1 = (ixl+room_y)*8 - pointer_background_buffer_y
            ;     yoffs2 = (ixl+ixh+room_y)*8 - pointer_background_buffer_y
            ;     if yoffs2 > POINTER_HEIGHT:
            ;         yoffs2 = POINTER_HEIGHT
            ;     if yoffs1 < POINTER_HEIGHT && yoffs2 >= 0:
            ;         if yoffs1 <= 0:
            ;             ptr1 = column_draw_buffer - yoffs1 + ixl*8
            ;              ptr2 = pointer_background_buffer + xoffs
            ;         else if yoffs1 > 0:
            ;             ptr1 = column_draw_buffer
            ;             ptr2 = pointer_background_buffer + xoffs + yoffs1*POINTER_WIDTH_IN_BYTES
            ;         for i = yoffs1 to yoffs2:
            ;             *ptr2 = *ptr1
            ;             ptr2 += POINTER_WIDTH_IN_BYTES
            ;             ptr1 ++ 
            ld hl, pointer_background_buffer_x
            ld a, (current_column_x)
            sub (hl)  ; xoffs: a = current_column_x - pointer_background_buffer_x
            ld c, a
            or a
            jp m, draw_room_dirty_columns_loop_skip_update_pointer_bg
            cp POINTER_WIDTH_IN_BYTES
            jp p, draw_room_dirty_columns_loop_skip_update_pointer_bg
draw_room_dirty_columns_loop_update_pointer_bg:
            ld a, ixl  ; first row to draw
            add a, ixh  ; a = one after the last row to draw
            ld hl, room_buffer + ROOM_STRUCT_VIDEO_MEM_START_Y
            add a, (hl)
            sub FIRST_SCREEN_ROOM_ROW
            add a, a
            add a, a
            add a, a
            ld hl, pointer_background_buffer_y
            sub (hl)  ; yoffs2
            or a
            jp m, draw_room_dirty_columns_loop_skip_update_pointer_bg
            cp POINTER_HEIGHT
            jp m, draw_room_dirty_columns_loop_update_pointer_bg_no_yoffs2_overflow
            ld a, POINTER_HEIGHT
draw_room_dirty_columns_loop_update_pointer_bg_no_yoffs2_overflow:
            ld b, a  ; b = yoffs2
            ld a, ixl  ; first row to draw
            ld hl, room_buffer + ROOM_STRUCT_VIDEO_MEM_START_Y
            add a, (hl)
            sub FIRST_SCREEN_ROOM_ROW
            add a, a
            add a, a
            add a, a
            ld hl, pointer_background_buffer_y
            sub (hl)  ; yoffs1
            cp POINTER_HEIGHT
            jp p, draw_room_dirty_columns_loop_skip_update_pointer_bg
            or a
            jp p, draw_room_dirty_columns_loop_update_pointer_bg_yoffs1_gt_0
            ; ptr1 (de) = column_draw_buffer - yoffs1 + ixl*8
            ; ptr2 (hl) = pointer_background_buffer + xoffs
            ld hl, column_draw_buffer
            neg
            ld d, 0
            ld e, a
            add hl, de
            ld a, ixl
            add a, a
            add a, a
            add a, a
            ld e, a
            add hl, de
            push hl
                ld hl, pointer_background_buffer
                ld d, 0
                ld e, c
                add hl, de  ; ptr2    
            pop de  ; ptr1
            xor a  ; we set yoffs1 to 0
            jr draw_room_dirty_columns_loop_update_pointer_bg_copy_data
draw_room_dirty_columns_loop_update_pointer_bg_yoffs1_gt_0:
            ; ptr1 (de) = column_draw_buffer + ixl*8
            ; ptr2 (hl) = pointer_background_buffer + xoffs + yoffs1*POINTER_WIDTH_IN_BYTES
            push af
                push bc
                    ld d, 0
                    ld e, POINTER_WIDTH_IN_BYTES
                    call mul8
                pop bc
                push bc
                    ld b, 0
                    add hl, bc
                    ld bc, pointer_background_buffer
                    add hl, bc  ; ptr2
                pop bc
                push hl
                    ld a, ixl
                    add a, a
                    add a, a
                    add a, a
                    ld h, 0
                    ld l, a
                    ld de, column_draw_buffer  ; ptr1
                    add hl, de
                    ex de, hl
                pop hl
            pop af


draw_room_dirty_columns_loop_update_pointer_bg_copy_data:
            ; at this point:
            ; - a: yoffs1
            ; - b: yoffs2
            ; - c: xoffs
            ; - de: ptr1
            ; - hl: ptr2
            ; for i = yoffs1 to yoffs2:
            ;     *ptr2 = *ptr1
            ;     ptr2 += POINTER_WIDTH_IN_BYTES
            ;     ptr1 ++ 
            sub b
            jr z, draw_room_dirty_columns_loop_skip_update_pointer_bg
            neg  ; yoffs2 - yoffs1
            ld b, 0
            ld c, POINTER_WIDTH_IN_BYTES
draw_room_dirty_columns_loop_update_pointer_bg_copy_data_loop:
            ex af, af'
                ld a, (de)
                ld (hl), a
            ex af, af'
            inc de
            add hl, bc
            dec a
            jr nz, draw_room_dirty_columns_loop_update_pointer_bg_copy_data_loop

draw_room_dirty_columns_loop_skip_update_pointer_bg:

        exx

draw_room_dirty_columns_loop_next_column:
        inc de
        inc hl
    pop af
    exx
        ld hl, current_column_x
        inc (hl)
    exx
    inc a
    push af
        ; update the pointer to the current tile:
        and #03
        jr nz, draw_room_dirty_columns_loop_no_next_tile
        push hl
            ld a, (room_buffer+ROOM_STRUCT_HEIGHT)
            ld l, a
            ld h, 0
            add hl, bc
            ld b, h
            ld c, l
        pop hl
draw_room_dirty_columns_loop_no_next_tile:
    pop af
    cp iyl  ; # of columns to draw
    jp nz, draw_room_dirty_columns_loop
    ret



IF PLAYER_SCALING = 1

player_zoom_draw_function_pointers:
    dw draw_room_dirty_columns_objects_state_column_found_draw_player_scaled
    dw draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_75_0
    dw draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_87_5
    dw draw_room_dirty_columns_objects_state_column_found_draw_loop
    dw draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_112_5


; SCALE: draw "c" pixels from hl to de
draw_room_dirty_columns_objects_state_column_found_draw_player_scaled:
    xor a
    ld (player_scale), a
    ld a, (ix + OBJECT_STRUCT_DEPTH)
    cp PLAYER_SCALING_THRESHOLD_112_5
    jp p, draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_112_5
    cp PLAYER_SCALING_THRESHOLD_87_5
    jp p, draw_room_dirty_columns_objects_state_column_found_draw_loop
    cp PLAYER_SCALING_THRESHOLD_75_0
    jp p, draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_87_5
draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_75_0:
    ld a, 2
    ld (player_scale), a
    ld a, #03
    ld (draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled_self_modifying + 1), a
    ; we first need to skip as many pixels as we are going to make the player shorter:
REPT PLAYER_HEIGHT / 4
    inc de
ENDR
    jr draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled

draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_87_5:
    ld a, 1
    ld (player_scale), a
    ld a, #07
    ld (draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled_self_modifying + 1), a
REPT PLAYER_HEIGHT / 8
    inc de
ENDR

draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled:
    ld a, (hl)
    or a
    jr z, draw_room_dirty_columns_objects_state_column_found_skip_pixel_player_scaled
    ld b, a
    and #55    ; check if the first pixel is transparent
    ld a, b
    jr nz, draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent_player_scaled
    ; a = (de)&#55 + a&#aa
    and #aa
    ld b, a
    ld a, (de)
    and #55
    add a, b
    ; if the first pixel is transparent, the second cannot be, so, just draw it:
    jr draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent_player_scaled

draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent_player_scaled:
    and #aa ; check if the second pixel is transparent:
    ld a, b    ; b still contains the original pixel
    jr nz, draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent_player_scaled
    ; a = (de)&#aa + a&#55
    and #55
    ld b, a
    ld a, (de)
    and #aa
    add a, b
draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent_player_scaled:
    ld (de), a
draw_room_dirty_columns_objects_state_column_found_skip_pixel_player_scaled:
    inc hl
    inc de
    dec c
    jp z, draw_room_dirty_columns_objects_state_column_found_draw_loop_done
    ld a, c
draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled_self_modifying:
    and #03  ; mdl:no-opt
    jr nz, draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled
    ld a, (player_scale)
    inc a
    jr z, draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_double_pixel
    ; skip one more pixel:
    inc hl
    dec c
    jp z, draw_room_dirty_columns_objects_state_column_found_draw_loop_done
    jr draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled

draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_double_pixel:
    ; double pixel:
    dec hl
    jr draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled

draw_room_dirty_columns_objects_state_column_found_draw_player_scaled_112_5:
    ld a, #ff
    ld (player_scale), a
    ld a, c
    add a, PLAYER_HEIGHT / 8
    ld c, a
REPT PLAYER_HEIGHT / 8
    dec de  ; we shift the sprite up to account for the scale
ENDR
    ld a, #07
    ld (draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled_self_modifying + 1), a
    jr draw_room_dirty_columns_objects_state_column_found_draw_loop_player_scaled

ENDIF


;-----------------------------------------------
; finds the pointer to an object with ID "a"
; returns:
; - z, and the pointer in ix if found
; - nz if not found
find_room_object_ptr_by_id:
    ld hl, room_buffer + ROOM_STRUCT_N_OBJECTS
    ld d, (hl)
    inc d
    dec d
    jr z, find_room_object_ptr_by_id_not_found
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
    ld bc, OBJECT_STRUCT_SIZE
find_room_object_ptr_by_id_loop:
    cp (ix + OBJECT_STRUCT_ID)
    ret z
    add ix, bc
    dec d
    jr nz, find_room_object_ptr_by_id_loop
    ; object not found:
find_room_object_ptr_by_id_not_found:
    or 1
    ret


;-----------------------------------------------
; removes the object with id "a"
remove_room_object:
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
    ld bc, OBJECT_STRUCT_SIZE
remove_room_object_loop:
    cp (ix + OBJECT_STRUCT_ID)
    jr z, remove_room_object_found
    add ix, bc
    jr remove_room_object_loop
remove_room_object_found:
    ; redraw the whole room:
    ld a, 1
    ld (redraw_whole_room_signal), a

    ld hl, (last_room_object_ptr)
    ld bc, -OBJECT_STRUCT_SIZE
    add hl, bc
    ld (last_room_object_ptr), hl

    ; remove it
    push ix
    pop bc
    ld hl, room_buffer + ROOM_STRUCT_OBJECT_DATA+(MAX_OBJECTS_PER_ROOM - 1) * OBJECT_STRUCT_SIZE + 1  ; we add 1 to prevent the amount being 0 for the last object
    xor a
    sbc hl, bc
    ld d, b
    ld e, c  ; de = pointer to the object to delete
    ld b, h
    ld c, l  ; bc = amount of data to move
    ld h, d
    ld l, e
    push bc
        ld bc, OBJECT_STRUCT_SIZE
        add hl, bc  ; hl = pointer to the next object
    pop bc
    ldir
    ld hl, room_buffer + ROOM_STRUCT_N_OBJECTS
    dec (hl)
    ret
