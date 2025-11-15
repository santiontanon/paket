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
    ld hl,general_buffer
ENDIF

    ; copy the beginning of the room (dimensions, background, and collision mask)
    ld hl, general_buffer
    ld de, room_buffer
    ld bc, ROOM_STRUCT_COLLISION_MASK  ; we might be copying more than necessary for a room smaller than 
                                       ; MAX_ROOM_WIDTH*MAX_ROOM_HEIGHT, but it's fine
    ldir

IF USE_PATH_FINDING != 0
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld b, 0
    ld c, a
    ld hl, collision_column_offsets
    ld de, room_buffer + ROOM_STRUCT_COLLISION_MASK
    ld a, MAX_ROOM_COLLISION_WIDTH
load_room_collision_column_offsets_loop:
    ld (hl), e
    inc hl
    ld (hl), d
    inc hl
    ex de, hl
    add hl, bc
    ex de, hl
    dec a
    jr nz, load_room_collision_column_offsets_loop
ENDIF

    ; calculate the size of the room data:
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    ld d, 0
    ld e, a
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    call mul8
    ld b, h
    ld c, l

    ; copy the collision mask:
    ld hl, general_buffer + ROOM_STRUCT_BACKGROUND
    add hl, bc  ; hl now points to the collision mask data
IF MAX_ROOM_COLLISION_WIDTH < MAX_ROOM_WIDTH
    ; This means that we use one collision tile per 2 engine tiles, so, we need to recalculate the size:
    push hl
        ld a, (room_buffer + ROOM_STRUCT_WIDTH)
        inc a
        srl a  ; (a + 1) / 2
        ld d, 0
        ld e, a
        ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
        call mul8
        ld b, h
        ld c, l
    pop hl
ENDIF
    ld de, room_buffer+ROOM_STRUCT_COLLISION_MASK
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
    ld bc, MAX_OBJECTS_PER_ROOM * OBJECT_STRUCT_SIZE
    ldir

    ; load the necessary object types, and replace object type numbers by object type pointers:
    ; clear the cache of already loaded object type pointers
    ld a, #ff
    ld hl, general_buffer
    ld de, general_buffer + 1
    ld bc, MAX_OBJECT_TYPES_PER_ROOM * 5 - 1
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
            ld e, (ix + OBJECT_STRUCT_NAME_PTR)    ; save the index of the object name in "de"
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
            jp load_room_load_object_types_loop_set_ptr_in_room_structure

load_room_load_object_types_loop_not_loaded:
            ; Otherwise, load it:
            push de  ; save the index of the object name text
                ; start by setting the object type on the cache structure:
                ld bc, (object_type_loaded_cache_next_ptr)
                ld (bc), a
                inc bc
                ld (object_type_loaded_cache_next_ptr), bc
                push af
IF IS_MEGAROM == 1
                    SETMEGAROMPAGE_8000 OBJECT_TYPE_BANK_PTRS_PAGE
ENDIF
                pop af
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
IF IS_MEGAROM == 1
                    inc hl  ; if it's a MegaROM, we also store the page number (followed by a 0)
                    inc hl
ENDIF
                    dec a
                    jr nz, load_room_load_object_types_loop_object_type_bank_ptr_loop
load_room_load_object_types_loop_object_type_bank_ptr_loop_done:
                    ld e, (hl)
                    inc hl
                    ld d, (hl)
IF IS_MEGAROM == 1
                    inc hl
                    ld a, (hl)  ; megarom page
                    SETMEGAROMPAGE_8000_A
ENDIF
                    ex de, hl  ; hl = pointer to the object type bank we want
                    ld de, general_buffer + MAX_OBJECT_TYPES_PER_ROOM * 5  ; 5, since we are skipping 5 bytes with objet type id, pointer to the preloaded type, pointer to the preloaded name 
                    call PAKET_UNPACK
load_room_load_object_types_loop_object_type_bank_is_same_as_already_loaded:
                pop af
                and #03  ; a = object type # within the bank we just decompressed
IF OBJECT_TYPES_PER_BANK != 4
    ERROR "PAKET Engine MSX platform assumes OBJECT_TYPES_PER_BANK = 4"
ENDIF

                ; copy it over to the object type buffer:
                ld hl, general_buffer + MAX_OBJECT_TYPES_PER_ROOM * 5  ; this is where we decompressed the object types
                or a
                jr z, load_room_load_object_types_loop_object_type_ptr_loop_done
                ; find the ptr to the object we want to copy:
load_room_load_object_types_loop_object_type_ptr_loop:
                ld c, (hl)
                inc hl
                ld b, (hl)  ; bc = object type size of the object we want to skip
                inc hl
                add hl, bc  ; skip to the next object type
                dec a
                jr nz,load_room_load_object_types_loop_object_type_ptr_loop
load_room_load_object_types_loop_object_type_ptr_loop_done:
                ld c, (hl)
                inc hl
                ld b, (hl)  ; bc = object type size of the object we want to copy
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
            pop bc  ; we had pushed "de", but we get it now into "bc", since we want to preserve "de"

            ; load the object name:
            ld a, b
            push de
            push hl
                ld de, (room_object_name_buffer_next_ptr)
                call get_text_from_bank
                ld bc, (room_object_name_buffer_next_ptr)
                ld (room_object_name_buffer_next_ptr),de
            pop hl
            pop de
            ; save pointer to the object name:
            ld (hl), c
            inc hl
            ld (hl), b
            inc hl
            ld (object_type_loaded_cache_next_ptr), hl

load_room_load_object_types_loop_set_ptr_in_room_structure:
        pop ix
        ; set the pointer in the room structure:
        ld (ix + OBJECT_STRUCT_TYPE_PTR), e
        ld (ix + OBJECT_STRUCT_TYPE_PTR + 1), d
        ; save the name (which is in "bc")
        ld (ix + OBJECT_STRUCT_NAME_PTR), c
        ld (ix + OBJECT_STRUCT_NAME_PTR+1),b 

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
    ; not implemented in MSX
    ; ld a, (room_buffer + ROOM_STRUCT_ROOM_FLAGS)
    ; srl a
    ; and #0e
    ; ld hl, player_zoom_draw_function_pointers
    ; ld b, 0
    ; ld c, a
    ; add hl, bc
    ; ld e, (hl)
    ; inc hl
    ; ld d, (hl)
    ; ld (player_zoom_draw_function_ptr), de
ENDIF

    call find_player_object_ptr

    ; check for exits upon clicking outside
    xor a
    ld (exit_upon_clicking_outside), a
    ld a, TRIGGER_CLICK_OUTSIDE_ROOM_AREA
    ld (current_event), a
    ld a, 0
    ld (current_event + 1), a
    ld (current_event + 2), a
    call eventMatchesRule
    jr nz, adjust_room_using_persistent_state

    ; rule matched!
    ld a, 1
    ld (exit_upon_clicking_outside), a
    jp adjust_room_using_persistent_state


;-----------------------------------------------
; After loading a room, this function makes sure the state of the room is consistent with 
; the "persistent state", this is achieved via on_room_load rules
adjust_room_using_persistent_state:
IF IS_MEGAROM == 1
    SETMEGAROMPAGE_8000 RULES_AND_SCRIPTS_DATA_PAGE
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
; - c, b: x, y coordinates to check
; output:
; - z: no collision
; - nz: collision
check_collision_mask:
check_collision_mask_for_path_finding:
IF USE_PATH_FINDING == 0
    ; calculate the offset in the collision mask: 
    ; offset = (room_buffer + ROOM_STRUCT_COLLISION_MASK) + (x / 16) * room_height + (y / 8)
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld d, 0
    ld e, a
    ld a, c  ; x
    srl a
    srl a
    srl a
    srl a
    push bc
        call mul8  ; hl = (x / 16) * room_height
    pop bc
    ld e, b
    srl e
    srl e
    srl e
    ld d, 0    
    add hl, de
    ld de, room_buffer + ROOM_STRUCT_COLLISION_MASK
    add hl, de
ELSE
    ; When we use pathfinding, we need to make things faster (but use an extra precomputed buffer):
    ld a, c  ; x
check_collision_mask_for_path_finding_a_set:
    rrca
    rrca
    rrca
    and #1e
    ld hl, collision_column_offsets
    ADD_HL_A
    ld e, (hl)
    inc hl
    ld d, (hl)
    ld l, b
    srl l
    srl l
    srl l
    ld h, 0
    add hl, de
ENDIF

    ; check the proper byte:
    ; (y & #04) + ((x / 2) & #03)
    ; so, we will calculate a mask:
    ; (y & 04) * 4 << ((x / 4) & #03)
    bit 2, b
    jr z, check_collision_mask_bottom_row
check_collision_mask_top_row:
    ld e, 16
    jr check_collision_mask_row_determined
check_collision_mask_bottom_row:
    ld e, 1
check_collision_mask_row_determined:
    ld a, c
    srl a
    srl a
    and #03
    jr z, check_collision_mask_mask_determined
check_collision_mask_mask_loop:
    sla e
    dec a
    jr nz, check_collision_mask_mask_loop
check_collision_mask_mask_determined:
    ld a, (hl)  ; a = collision mask of the tile we need to check collision with
    and e
    ret


;-----------------------------------------------
IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 1
draw_room_any_dirty_p:
    ld b, MAX_ROOM_WIDTH
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
; draws a complete room from scratch
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
    ld hl, dirty_column_buffer
    ld bc, MAX_ROOM_WIDTH
    ld a, 1
    call clear_memory_to_a

IF SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED == 0
draw_room_dirty_columns:
ENDIF
draw_room_dirty_columns_internal:
    ; VRAM address of the top-left of the room:
    ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_Y)
    ld h, 0
    ld l, a
    add hl, hl
    add hl, hl
    add hl, hl
    add hl, hl
    add hl, hl
    ld a, (room_buffer + ROOM_STRUCT_VIDEO_MEM_START_X)
    ld b, 0
    ld c, a
    add hl, bc
    add hl, hl
    add hl, hl
    add hl, hl
    ld bc, CHRTBL2
    add hl, bc
    ex de, hl

    ld ix, dirty_column_buffer
    ld hl, room_buffer + ROOM_STRUCT_BACKGROUND
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    ld b, a
    ld iyl, 0  ; column index (in MSX tiles)
draw_room_x_loop:
    ld a, (ix)
    or a
    jr z, draw_room_x_loop_skip_column
    push ix
    push bc
        ; clear column buffer:
        push de
        push hl
            call draw_room_column_tiles
            call draw_room_column_objects
        pop hl
        pop de
        
        ; copy column to vdp:        
        push hl
            push de
                call draw_room_copy_column_to_vdp
            pop hl
            ld bc, MSX_TILES_PER_ENGINE_TILE * 8
            add hl, bc
        ex hl, de
        pop hl
        inc hl
    pop bc
    pop ix
    inc iyl
IF MSX_TILES_PER_ENGINE_TILE == 2
    inc iyl
ENDIF
    ld (ix), 0
    inc ix
    djnz draw_room_x_loop
    ret

draw_room_x_loop_skip_column:
    ex hl, de
        ld a, MSX_TILES_PER_ENGINE_TILE * 8
        ADD_HL_A
    ex hl, de
    inc hl
IF MSX_TILES_PER_ENGINE_TILE == 2
    inc iyl
ENDIF
    inc iyl
    inc ix
    djnz draw_room_x_loop
    ret



;-----------------------------------------------
; input:
; - hl: ptr to the tile column to draw
draw_room_column_tiles:
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld ixh, a
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    ld b, 0
    ld c, a
    exx
        ld de, room_column_draw_buffer
    exx        
draw_room_column_tiles_loop:
    ld a, (hl)
    ; draw tile "a":
    exx
        ld bc, (tile_buffer_ptr)
        add a, a
        add a, a
        add a, a  ; * 8
        ld h, 0
        ld l, a
        add hl, hl
IF MSX_TILES_PER_ENGINE_TILE == 2
        add hl, hl
ENDIF
        add hl, bc  ; hl now has the pointer to the tile info
REPT MSX_TILES_PER_ENGINE_TILE * 8
        ldi
ENDR
        push de
            ld a, 256 - MSX_TILES_PER_ENGINE_TILE * 8
            ADD_DE_A
REPT MSX_TILES_PER_ENGINE_TILE * 8
            ldi
ENDR
        pop de
    exx
    ; next row:
    add hl, bc
    dec ixh
    jp nz, draw_room_column_tiles_loop
    ret


;-----------------------------------------------
; input:
; - iyl: column index
draw_room_column_objects:
    ; Draw the objects:
    ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
    or a
    ret z
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
draw_room_objects_loop:
    push af
        ld a, (ix + OBJECT_STRUCT_ID)
        cp PLAYER_OBJECT_ID
        jp z, draw_room_objects_loop_next

        ; Draw object:
        ld a, (ix + OBJECT_STRUCT_X2)
        srl a
        srl a
        srl a
        sub iyl
        jp m, draw_room_objects_loop_next

        ld a, (ix + OBJECT_STRUCT_X)
        srl a
        srl a
        srl a
        sub iyl
        cp MSX_TILES_PER_ENGINE_TILE  ; object is outside of the column we are drawing
        jp p, draw_room_objects_loop_next
        ld iyh, a  ; x coordinate of the object in column buffer coordinates

        add a, a
        add a, a
        add a, a

        ; sign extend a into bc:
        ld c, a
        add a, a
        sbc a
        ld b, a

        ld a, (ix + OBJECT_STRUCT_Y)
        and #f8
        ld l, a
        ld h, 0
IF MSX_TILES_PER_ENGINE_TILE == 2
        add hl, hl
ENDIF
        ld a, (ix + OBJECT_STRUCT_Y)  ; object y
        and #07
        ADD_HL_A  ; hl = (y % 8) + (y / 8) * MSX_TILES_PER_ENGINE_TILE * 8

        ld de, room_column_draw_buffer
        add hl, de
        add hl, bc
        ex de, hl  ; de = ptr to the column buffer
            call find_object_state_ptr

            ; draw the image in the current state:
            inc hl  ; skip state ID

            call object_skip_to_current_animation_frame

            inc hl  ; selection mask flag
            ld b, (hl)  ; width in MSX tiles (8 pixels per tile)
            inc hl
            ld c, (hl)  ; height in pixels
            inc hl
        ex de, hl  ; hl: column buffer pointer, de: object data
draw_room_objects_loop_draw_loop_y:
        push bc
        push iy
            push hl
                ld c, iyh
draw_room_objects_loop_draw_loop_x:
                ld a, c  ; x coordinate of the current MSX tile in column buffer coordinates
                cp MSX_TILES_PER_ENGINE_TILE
                ld a, (de)  ; pattern data
                inc de  ; does not affect the flags
                jr nc, draw_room_objects_loop_draw_loop_x_next
                ld (hl), a
                ld a, (de)  ; attribute data
                inc h  ; move to the attribute data
                ld (hl), a
                dec h  ; move back to the pattern data
draw_room_objects_loop_draw_loop_x_next:
                inc de
                ld a, 8
                ADD_HL_A
                inc c
                djnz draw_room_objects_loop_draw_loop_x
            pop hl
            ; next row:
            inc hl
IF MSX_TILES_PER_ENGINE_TILE == 2
            ; we detect if we need to add an additional 8, assuming that the 'room_column_draw_buffer' is 8-aligned
            ld a, l
            and #07
            jp nz, draw_room_objects_loop_draw_loop_y_no_next_tile
            ld a, 8
            ADD_HL_A
draw_room_objects_loop_draw_loop_y_no_next_tile:
ENDIF
        pop iy
        pop bc
        dec c
        jp nz,draw_room_objects_loop_draw_loop_y

draw_room_objects_loop_next:
        ld bc, OBJECT_STRUCT_SIZE
        add ix, bc
    pop af
    dec a
    jp nz,draw_room_objects_loop
    ret


;-----------------------------------------------
; input:
; - de: ptr to the VDP when to copy the column
draw_room_copy_column_to_vdp:
    ld hl, room_column_draw_buffer
    push de
        ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
        ld ixl, a
draw_room_copy_column_to_vdp_loop_patterns:
        ld b, MSX_TILES_PER_ENGINE_TILE * 8
        fast_LDIRVM_small_macro
        inc d
        dec ixl
        jp nz, draw_room_copy_column_to_vdp_loop_patterns
    pop hl
    ld bc, CLRTBL2 - CHRTBL2
    add hl, bc
    ex de, hl

    ; copy attributes:
    ld hl, room_column_draw_buffer + 16 * 16
    ld a, (room_buffer + ROOM_STRUCT_HEIGHT)
    ld ixl, a
draw_room_copy_column_to_vdp_loop_attributes:
    ld b, MSX_TILES_PER_ENGINE_TILE * 8
    fast_LDIRVM_small_macro
    inc d
    dec ixl
    jp nz, draw_room_copy_column_to_vdp_loop_attributes
    ret


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
    ld e, (ix + OBJECT_STRUCT_ID)
    cp e
    ret z
    add ix, bc
    dec d
    jr nz, find_room_object_ptr_by_id_loop
find_room_object_ptr_by_id_not_found:
    ; object not found:
    or 1
    ret


;-----------------------------------------------
; removes te object with id "a"
remove_room_object:
    ld ix,room_buffer+ROOM_STRUCT_OBJECT_DATA
    ld bc,OBJECT_STRUCT_SIZE
remove_room_object_loop:
    cp (ix+OBJECT_STRUCT_ID)
    jr z,remove_room_object_fouund
    add ix,bc
    jr remove_room_object_loop
remove_room_object_fouund:
    ; redraw the whole room:
    ld a,1
    ld (redraw_whole_room_signal),a

    ld hl,(last_room_object_ptr)
    ld bc,-OBJECT_STRUCT_SIZE
    add hl,bc
    ld (last_room_object_ptr),hl
    
    ; remove it
    push ix
    pop bc
    ld hl,room_buffer+ROOM_STRUCT_OBJECT_DATA+(MAX_OBJECTS_PER_ROOM-1)*OBJECT_STRUCT_SIZE+1 ; we add 1 to prevent the amount being 0 for the last object
    xor a
    sbc hl,bc
    ld d,b
    ld e,c    ; de = pointer to the object to delete
    ld b,h
    ld c,l    ; bc = amount of data to move
    ld h,d
    ld l,e
    push bc
        ld bc,OBJECT_STRUCT_SIZE
        add hl,bc    ; hl = pointer to the next objec5
    pop bc
    ldir
    ld hl,room_buffer+ROOM_STRUCT_N_OBJECTS
    dec (hl)
    ret
