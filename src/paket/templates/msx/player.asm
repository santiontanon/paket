;-----------------------------------------------
; returns:
; - z: player ptr found
; - nz: not found
; - ix: player ptr
get_player_ptr:
    ld ix, (player_object_ptr)
    ld a, (ix + OBJECT_STRUCT_ID)
    cp PLAYER_OBJECT_ID
    ret z
    push hl
    push de
    push bc
        call find_player_object_ptr  ; if for some reason the player object pointer has changed, we update it
    pop bc
    pop de
    pop hl
    ret


;-----------------------------------------------
; Finds the player amongst the current objects:
; returns:
; - z if player is found (and pointer in ix), 
; - nz if it is not found
find_player_object_ptr:
    xor a
    ld (player_in_room), a
    ld a, PLAYER_OBJECT_ID
    call find_room_object_ptr_by_id
    ret nz
    ld a, 1
    ld (player_in_room), a
    ld (player_object_ptr), ix
    ret


IF N_PLAYER_SPRITES > 0
;-----------------------------------------------
; sets the player sprite attributes in such a way that the player is not seen
clear_player:
    ld hl, player_sprites_attributes
    ld de, player_sprites_attributes + 1
    ld bc, 4 * N_PLAYER_SPRITES - 1
    ld a, 200    ; some large y value
    ld (hl), a
    ldir
    ret


IF PLAYER_SCALING = 1
; not implemented in MSX
;-----------------------------------------------
; player_zoom_draw_function_pointers:
;     dw synthesize_player_sprites  ; scaled
;     dw synthesize_player_sprites  ; fixed at 0.75x
;     dw synthesize_player_sprites  ; fixed at 0.875x
;     dw synthesize_player_sprites  ; fixed at 1.0x
;     dw synthesize_player_sprites  ; fixed at 1.125x
ENDIF

;-----------------------------------------------
; Sets up the sprites so that the player is rendered in the next frame
draw_player:
    ld a, (player_in_room)
    or a
    jp z, clear_player

    ; make sure we have the player object:
    call get_player_ptr
    ret nz

    call find_object_state_ptr
    ; draw the image in the current state:
    inc hl  ; skip state ID
    call object_skip_to_current_animation_frame
    inc hl  ; selection mask flag

    ; set up the sprite attributes:
    ld de, player_sprites_attributes
    ld b, N_PLAYER_SPRITES
    ld c, 2 * N_POINTER_TYPES
    push hl
draw_player_attribute_loop:
        ; y:
        ld a, (hl)
        add a, (ix + OBJECT_STRUCT_Y)
        add a, (FIRST_SCREEN_ROOM_ROW) * 8 - 1  ; "-1" since sprites are drawn one row lower in MSX
        push hl
            ld hl, room_buffer + ROOM_STRUCT_START_Y
            add a, (hl)
        pop hl
        ld (de), a
        inc de
        inc hl
        ; x:
        ld a, (ix + OBJECT_STRUCT_X)
        push hl
            ld hl, room_buffer + ROOM_STRUCT_START_X
            add a, (hl)
            add a, ((32 / MSX_TILES_PER_ENGINE_TILE - GUI_WIDTH) / 2) * 8
        pop hl
        ld (de), a
        inc de
        ; pattern index:
        ld a, c
        add a, a
        add a, a
        ld (de), a
        inc de
        inc c
        ; color:
        ld a, (hl)
        ld (de), a
        inc hl
        inc de
        djnz draw_player_attribute_loop
    pop de  ; pointer from where to get the sprite "y"s
    
IF PLAYER_SCALING = 1
    ; push hl  ; not implemented in MSX
    ;     ld hl, (player_zoom_draw_function_ptr)
    ;     jp (hl)
ELSE:
    ; jp  synthesize_player_sprites
ENDIF


;-----------------------------------------------
; Creates the player sprite patterns to simulate occlusions with the objects in the screen
synthesize_player_sprites:
IF PLAYER_SCALING = 1
    ; pop hl  ; not implemented in MSX
ENDIF
    ; copy the raw player sprites:
    push de
    push hl
    push ix
        ; init the mask:
        ld a,#ff
        ld hl,general_buffer
        ld de,general_buffer+1
        ld bc,3*PLAYER_HEIGHT-1
        ld (hl),a
        ldir

        ld a,(ix+OBJECT_STRUCT_X)
        and #f8
        ld (player_mask_x),a
        ld a,(ix+OBJECT_STRUCT_Y)
        inc a    ; since sprites in MSX are one pixel off in the y axis
        ld (player_mask_y),a

        ; iy will bave the player
        ; ix will have the objects
        push ix
        pop iy

        ; create the mask based on the objects with depth > player's depth:
        ld c,(iy+OBJECT_STRUCT_DEPTH)
        inc c

        ld a,(room_buffer+ROOM_STRUCT_N_OBJECTS)
        or a
        jp z,synthesize_player_sprites_object_loop_done
        ld ix,room_buffer+ROOM_STRUCT_OBJECT_DATA
synthesize_player_sprites_object_loop:
        push af
            ld a,(ix+OBJECT_STRUCT_DEPTH)
            cp c
            jp m,synthesize_player_sprites_object_loop_skip

            ; see if it overlaps with the player:
            ld a,(player_mask_x)
            cp (ix+OBJECT_STRUCT_X2)
            jp p,synthesize_player_sprites_object_loop_skip
            add a,16
            cp (ix+OBJECT_STRUCT_X)
            jp m,synthesize_player_sprites_object_loop_skip
            ld a,(player_mask_y)
            cp (ix+OBJECT_STRUCT_Y2)
            jp p,synthesize_player_sprites_object_loop_skip
            add a,PLAYER_HEIGHT
            cp (ix+OBJECT_STRUCT_Y)
            jp m,synthesize_player_sprites_object_loop_skip

            ; add object selection mask to mask (for now we just add #ff):
            push bc
                ld a, (player_mask_x)
                ld c, a
                ld a, (ix + OBJECT_STRUCT_X)
                and #f8
                sub c
                sra a
                sra a
                sra a
                ld c, a

                ; extend 8bit to 16 bit
                ld e, a
                add a, a
                sbc a, a
                ld d, a
                
                ld a, PLAYER_HEIGHT
                call mul8

                ld a, (player_mask_y)
                ld b, a
                ld a, (ix + OBJECT_STRUCT_Y)
                sub b
                ld b, a  ; c, b now have the coordinates of the object in player_mask coordinates

                ; extend 8bit to 16 bit
                ld e, a
                add a, a
                sbc a, a
                ld d, a
                
                add hl,de
                ld de,general_buffer
                add hl,de  ; mask pointer: general_buffer + b + c*PLAYER_HEIGHT

                exx  ; ALTERNATE
                    call find_object_state_ptr

                    ; draw the image in the current state:
                    inc hl  ; skip state ID
                    call object_skip_to_current_animation_frame
                    inc hl  ; selection mask flag
                    ld c,(hl)    ; width in tiles
                    inc hl
                    ld b,(hl)    ; height in pixels
                    inc hl

                    ld de,0    ; this signals that there is no selection mask
                    or a
                    jr z,synthesize_player_sprites_object_loop_no_selection_mask

                    push bc
                        ld d,0
                        ld e,c
                        ld a,b
                        push hl
                            call mul8
                            ld b,h
                            ld c,l
                        pop hl
                        add hl,bc    
                        add hl,bc    
                    pop bc
                    ex de,hl     ; de: now has the pointer to the selection mask 
synthesize_player_sprites_object_loop_no_selection_mask:
                    
                    push de
                exx ; ORIGINAL
                pop de    ; here "de" has the selection mask, or "de = 0" if there is no selection mask
                exx ; ALTERNATE
                    ld a,b
synthesize_player_sprites_object_loop_loop_y:
                    push af                    
                        exx ; ORIGINAL
                            push de
                                ld a,b
                                or a
                                jp m,synthesize_player_sprites_object_loop_loop_y_next
                                cp PLAYER_HEIGHT
                                jp p,synthesize_player_sprites_object_loop_loop_y_next
                                push hl
                                push bc
                                    exx ; ALTERNATE
                                        ld a,c
                                    exx ; ORIGINAL
synthesize_player_sprites_object_loop_loop_x:
                                    push af
                                        ld a,c
                                        or a
                                        jp m,synthesize_player_sprites_object_loop_loop_x_next
                                        cp 3
                                        jp p,synthesize_player_sprites_object_loop_loop_x_next
                                        ld a,d
                                        or a
                                        jr z,synthesize_player_sprites_object_loop_loop_x_no_mask
                                            ld a,(de)
                                            cpl
synthesize_player_sprites_object_loop_loop_x_no_mask:
                                        and (hl)
                                        ld (hl),a
synthesize_player_sprites_object_loop_loop_x_next:
                                        inc c
                                        inc de
                                        push de
                                            ld de,PLAYER_HEIGHT
                                            add hl,de
                                        pop de
                                    pop af
                                    dec a
                                    jr nz,synthesize_player_sprites_object_loop_loop_x
                                pop bc
                                pop hl
synthesize_player_sprites_object_loop_loop_y_next:
                                inc hl
                                inc b
                            pop de    ; pointer to the selection mask
                            push hl
                                ex de,hl
                                    ld d,0
                                    exx
                                        ld a,c  ; width in tiles of the selection mask
                                    exx
                                    ld e,a
                                    add hl,de
                                ex de,hl
                            pop hl
                        exx ; ALTERNATE
                    pop af
                    dec a
                    jr nz,synthesize_player_sprites_object_loop_loop_y
                exx ; ORIGINAL
            pop bc

synthesize_player_sprites_object_loop_skip:
            ld de,OBJECT_STRUCT_SIZE
            add ix,de
        pop af
        dec a
        jp nz,synthesize_player_sprites_object_loop
synthesize_player_sprites_object_loop_done:

    pop ix

    ; shift the mask to the left to align it with the player, if necessary:
    ld a,(ix+OBJECT_STRUCT_X)
    and #07
synthesize_player_sprites_nmask_shift_outer_loop:
    jr z,synthesize_player_sprites_no_mask_shift
    push af
        ld bc,general_buffer+PLAYER_HEIGHT*2
        ld hl,general_buffer+PLAYER_HEIGHT
        ld de,general_buffer
        ld a,PLAYER_HEIGHT
    synthesize_player_sprites_nmask_shift_loop:
        ex af,af'
            ld a,(bc)
            sla a
            ld (bc),a
            rl (hl)
            ex de,hl
            rl (hl)
            ex de,hl
            inc hl
            inc de
            inc bc
        ex af,af'
        dec a
        jr nz,synthesize_player_sprites_nmask_shift_loop
    pop af
    dec a
    jr nz,synthesize_player_sprites_nmask_shift_outer_loop
synthesize_player_sprites_no_mask_shift:

    pop hl
    pop de

    ; synthesize the player sprites:    
    ; - player_sprite_y: (de)
    ; - player_y: (ix+OBJECT_STRUCT_Y)
    ; - masks[y] = general_buffer+y
    ; - sprite_pattern: hl
    ; - write: iy
    ; for(int i = 0;i<N_PLAYER_SPRITES;i++) {
    ;   y = player_sprite_y[i] - player_y
    ;   for(int j = 0;j<16;j++) {
    ;     if (y >= 0) {
    ;       mask = masks[y]
    ;     } else {
    ;       mask = 0
    ;     }
    ;     write(sprite_pattern[i][j] & mask)
    ;   }
    ;   for(int j = 0;j<16;j++) {
    ;     if (y >= 0) {
    ;       mask = masks[y+PLAYER_HEIGHT]
    ;     } else {
    ;       mask = 0
    ;     }
    ;     write(sprite_pattern[i][j+16] & mask)
    ;   }
    ; }

    ld a,N_PLAYER_SPRITES
    ld iy,player_sprites_patterns
synthesize_player_sprites_synth_loop:
    push af
        ld a,(de)    ; "y"
        ld bc,general_buffer
        push hl
        push af
            ; extend 8bit to 16 bit
            ld l,a
            add a,a
            sbc a,a
            ld h,a

            add hl,bc
            ld b,h
            ld c,l    ; bc = pointer to the mask
        pop af
        pop hl
        
        push ix
        ; first sprite column:
            ld ixl,16
synthesize_player_sprites_synth_loop_column1_loop:
            or a
            push af
                jp m,synthesize_player_sprites_synth_loop_outside_mask1
                ; get the mask:
                ld a,(bc)
synthesize_player_sprites_synth_loop_outside_mask1:
                and (hl)
                ld (iy),a
            pop af
            inc hl
            inc iy
            inc bc
            inc a
            dec ixl
            jr nz,synthesize_player_sprites_synth_loop_column1_loop

            ; second sprite column:
            push hl
                ld hl,PLAYER_HEIGHT-16
                add hl,bc
                ld b,h
                ld c,l
            pop hl
            ld ixl,16
            sub 16
synthesize_player_sprites_synth_loop_column2_loop:
            or a
            push af
                jp m,synthesize_player_sprites_synth_loop_outside_mask2
                ; get the mask:
                ld a,(bc)
synthesize_player_sprites_synth_loop_outside_mask2:
                and (hl)
                ld (iy),a
            pop af
            inc hl
            inc iy
            inc bc
            inc a
            dec ixl
            jr nz,synthesize_player_sprites_synth_loop_column2_loop

        pop ix

        inc de
        inc de
    pop af
    dec a
    jr nz,synthesize_player_sprites_synth_loop
    ret
ENDIF


;-----------------------------------------------
; request the player to walk to the object pointed to by "iy".
; The target position to walk to is set as: (iy.x + iy.x2)/2, iy.depth+1
; input:
; - 'iy': target object
player_request_movement_to_object:
    ld (player_current_action_target_object), iy

    ld a, (player_in_room)
    or a
    jp z, update_player_walking_reached_target  ; if the player is not in the room, just fake as if we have arrived

    ; check if the player is busy (player can only walk if it is IDLE or WALKING)
    ; if action is "PLAYER_ACTION_NOT_ON_ROOM", this will also exit    
    ld hl, player_current_action
    ld a, (hl)
    cp PLAYER_ACTION_WALKING + 1
    ret p

    ; set action and parameters, and initialize the movement state:
    ld a, (iy + OBJECT_STRUCT_DEPTH)
IF USE_PATH_FINDING == 0
    inc a
ELSE
IF STOP_EARLY_WHEN_WALKING == 1
    ld b, a
ENDIF
ENDIF
    ld (player_current_action_parameters + 1), a
    ; find the target coordinates (potentially stopping some pixels early, for
    ; not blocking the pointer):
    call get_player_ptr
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH / 2
    ld c, a
    ld (hl), PLAYER_ACTION_WALKING
    ld d, 0
    ld e, (iy + OBJECT_STRUCT_X)
    ld h, 0
    ld l, (iy + OBJECT_STRUCT_X2)
    add hl, de
    srl h
    rr l    
    ld a, l  ; a = (x+x2)/2 ; we need to do it via 16 bit operations, since x+x2 might be > 255

IF STOP_EARLY_WHEN_WALKING == 1
    cp c  ; a = target x, c = player x position
    jr nc, player_request_movement_to_object_from_left
player_request_movement_to_object_from_right:
    ; if player_x > a -> a = min(player_x, a+slack)
    add a, PLAYER_MOVE_TO_OBJECT_SLACK
    cp c
    jr c, player_request_movement_to_object_from_right_more_than_slack
    ld a, c  ; do not use slack
IF USE_PATH_FINDING != 0
    jr player_request_movement_to_object_from_right_slack_set
player_request_movement_to_object_from_right_more_than_slack:
    ld c, a
    call check_collision_mask
    ld a, c
    jr z, player_request_movement_to_object_from_right_slack_set
    sub PLAYER_MOVE_TO_OBJECT_SLACK
player_request_movement_to_object_from_right_slack_set:
ELSE
player_request_movement_to_object_from_right_more_than_slack:
ENDIF
    ld (player_current_action_parameters), a    
IF USE_PATH_FINDING == 0  ; no path finding
    ret
ELSE
IF DOUBLE_CLICK_ON_EXIT == 1
    ld a, (double_click)
    or a
    jr nz, double_click_on_exit_check_if_there_is_path
ENDIF
IF USE_PATH_FINDING == 1  ; pathfinding on click
    jp path_finding
ELSE  ; pathfinding on collision
    xor a
    ld (path_finding_invoked), a
    ret
ENDIF
ENDIF

player_request_movement_to_object_from_left:
    ; if player_x < a -> a = max(player_x, a-slack)
    sub PLAYER_MOVE_TO_OBJECT_SLACK
    cp c
    jr nc, player_request_movement_to_object_from_left_more_than_slack
    ld a, c  ; do not use slack
IF USE_PATH_FINDING != 0
    jr player_request_movement_to_object_from_left_slack_set
player_request_movement_to_object_from_left_more_than_slack:
    ld c, a
    call check_collision_mask
    ld a, c
    jr z, player_request_movement_to_object_from_left_slack_set
    add a, PLAYER_MOVE_TO_OBJECT_SLACK
player_request_movement_to_object_from_left_slack_set:
ELSE
player_request_movement_to_object_from_left_more_than_slack:
ENDIF

ENDIF

    ld (player_current_action_parameters), a    
IF USE_PATH_FINDING == 0  ; no path finding
    ret
ELSE
IF DOUBLE_CLICK_ON_EXIT == 1
    ld a, (double_click)
    or a
    jr nz, double_click_on_exit_check_if_there_is_path
ENDIF
IF USE_PATH_FINDING == 1  ; pathfinding on click
    jp path_finding
ELSE  ; pathfinding on collision
    xor a
    ld (path_finding_invoked), a
    ret
ENDIF
ENDIF


IF DOUBLE_CLICK_ON_EXIT == 1
;-----------------------------------------------
double_click_on_exit_check_if_there_is_path:
    ; If USE_PATH_FINDING == 1, it would have already been called
IF USE_PATH_FINDING != 1
    call path_finding
ENDIF
    ld a, (path_finding_best_distance)  ; check if the path takes us close enough
    cp PLAYER_ACTION_DISTANCE_THRESHOLD / 2
    ret nc
    ; directly exit!
    call eventMatchesRule
    ret nz
    ; rule matched! execute rule effect:
    ld a, PLAYER_ACTION_IDLE
    ld (player_current_action), a
    jp executeRuleScript
ENDIF


;-----------------------------------------------
; request the player to walk to the coordinates pointed by pointer_x, pointer_y
player_request_movement_to_pointer:
    xor a
    ld (player_current_action_target_object), a  ; clear the selected object ptr
    ld (player_current_action_target_object + 1), a
    ld a, (player_in_room)
    or a
    ret z  ; if the player is not in the room, just cancel the request
    ; check if the player is busy (player can walk, if it is IDLE or WALKING)
    ; if action is "PLAYER_ACTION_NOT_ON_ROOM", this will also exit
    ld hl, player_current_action
    ld a, (hl)
    cp PLAYER_ACTION_WALKING + 1
    ret p

    ld a, TRIGGER_WALK
    ld (current_event), a  ; just something that will not match any rule

    ; set action and parameters, and initialize the movement state:
    ld (hl), PLAYER_ACTION_WALKING
    ld a, (pointer_x)
    add a, 4
    ld hl, room_buffer + ROOM_STRUCT_START_X
    sub (hl)
    ld (player_current_action_parameters), a    
    ld a, (pointer_y)
    add a, 4
    inc hl
    sub (hl)
    ld (player_current_action_parameters + 1), a
IF USE_PATH_FINDING == 0  ; no path finding
    ret
ELSE
IF USE_PATH_FINDING == 1  ; pathfinding on click
    jp path_finding
ELSE  
    ; pathfinding on collision
    xor a
    ld (path_finding_invoked), a
    ret
ENDIF
ENDIF


;-----------------------------------------------
; Updates the current player in case it is doing any action
update_player:
    ld a, (player_in_room)
    or a
    ret z  ; if the player is not in the room, nothing to update
    ld hl, player_current_action
    ld a, (hl)
    cp PLAYER_ACTION_WALKING
    jr z, update_player_walking
    ret

update_player_walking:
    ld a, (game_cycle)
    and #01  ; player speed, change to #03 to make it slower
    ret nz

    call get_player_ptr

    ld b, 0  ; mark if we have moved, or movement is over
IF USE_PATH_FINDING == 2  ; path finding on collision
    ld a, (path_finding_invoked)
    or a
    jr z, update_player_walking_no_path
ENDIF
IF USE_PATH_FINDING != 0
update_player_walking_check_if_there_is_path:
    ld b, 0  ; mark if we have moved, or movement is over
    ; If there is any point in the path_finding_path buffer, go there,
    ; otherwise, just go directly to the destination:
    ld hl, (path_finding_path_ptr)
    inc hl
    ld a, (hl)  ; "y"
    cp #ff  ; check if we have a path
    jr z, update_player_walking_no_path

    cp (ix + OBJECT_STRUCT_Y)
    jr z, update_player_walking_path_new_position_y_calculated
    push af
        call m, update_player_walking_move_up
    pop af
    call p, update_player_walking_move_down
 update_player_walking_path_new_position_y_calculated:
    ld hl, (path_finding_path_ptr)
    ld a, (hl)  ; "x"
    sub PLAYER_WIDTH / 2
    cp (ix + OBJECT_STRUCT_X)
    jr z, update_player_walking_path_new_position_calculated
    push af
        call m, update_player_walking_move_left
    pop af
    call p, update_player_walking_move_right
update_player_walking_path_new_position_calculated:
    ld a, b
    or a
    ret nz

    ; we did not move, so, we reached the waypoint!
    ld hl, (path_finding_path_ptr)
    dec hl
    dec hl
    ld (path_finding_path_ptr), hl
    jr update_player_walking_check_if_there_is_path

update_player_walking_no_path:
ENDIF

    ld a, (player_current_action_parameters + 1)
    sub PLAYER_HEIGHT
    cp (ix + OBJECT_STRUCT_Y)
    jr z, update_player_walking_new_position_y_calculated
    push af
        call m, update_player_walking_move_up
    pop af
    call p, update_player_walking_move_down
update_player_walking_new_position_y_calculated:
    ld a, (player_current_action_parameters)
    sub PLAYER_WIDTH / 2
    jr nc, update_player_walking_new_position_y_calculated_target_x_set
    xor a
update_player_walking_new_position_y_calculated_target_x_set:
    cp (ix + OBJECT_STRUCT_X)
    jr z, update_player_walking_new_position_calculated    
    push af
        call c, update_player_walking_move_left
    pop af
    call nc, update_player_walking_move_right

update_player_walking_new_position_calculated:
    ld a, b
    or a
    jp nz, update_player_walking_still_moving

    ; reached the target or an obstacle. Check if we are close enough:
    ld hl, player_current_action
    ld (hl), PLAYER_ACTION_IDLE
IF RESET_PLAYER_DIRECTION_ON_IDLE == 1
    ; Reset state/direction to idle/left:
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), PLAYER_DIRECTION_ON_IDLE
ELSE
    ; Reset state to idle, but keep direction:
    ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
    and #f0
    add a, OBJECT_STATE_IDLE
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
ENDIF
    ; set the object width:
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH
    ld (ix + OBJECT_STRUCT_X2), a

    ; check if we reached close enough to the target:
    ld a, (player_current_action_parameters + 1)
    sub PLAYER_HEIGHT
    sub (ix + OBJECT_STRUCT_Y)
    jp p, update_player_walking_new_position_calculated_y_diff_positive
    neg
update_player_walking_new_position_calculated_y_diff_positive:
    ld b, a
    ; Divide all the x coordinates by 2, to avoid overflows:
    ld a, (player_current_action_parameters)
    sub PLAYER_WIDTH / 2
    sra a
    ld c, (ix + OBJECT_STRUCT_X)
    sra c
    sub c
    jp p, update_player_walking_new_position_calculated_x_diff_positive
    neg
update_player_walking_new_position_calculated_x_diff_positive:
    ld c, a
    ; here "c" contains |difference in x|/2 and "b" contains |difference in y|
    ; if we are going to an object, take the object width into account,
    ; otherwise, just check if we are close enough:
    ld iy, (player_current_action_target_object)
    ld a, iyl
    add a, iyh
    jr z, update_player_walking_new_position_calculated_no_object

    ; Subtract half the width/2 of the object to |difference in x|/2, to account
    ; for object width:
    ld a, (iy + OBJECT_STRUCT_X2)
    sub (iy + OBJECT_STRUCT_X)
    srl a
    srl a
    cp c
    jr nc, update_player_walking_new_position_calculated_zero_x_diff
    neg
    add a, c
    ld c, a
    jr update_player_walking_new_position_calculated_no_object
update_player_walking_new_position_calculated_zero_x_diff:
    ld c, 0
update_player_walking_new_position_calculated_no_object:
    ld a, c
    add a, b  ; |difference in x|/2 + |difference in y| of the target position!
    cp PLAYER_ACTION_DISTANCE_THRESHOLD
    jp p, update_player_walking_not_reached_close_enough

    ; check if any rule was triggered:
update_player_walking_reached_target:
    call eventMatchesRule
    jr nz, update_player_walking_reached_target_no_rule_match
    ; rule matched! execute rule effect:
    jp executeRuleScript

update_player_walking_reached_target_no_rule_match:
    ld a, (current_event)
    cp TRIGGER_EXAMINE_OBJECT
    ret nz

    ; If it was an examine action, print the object description:
    ld iy, (player_current_action_target_object)
    ld c, (iy + OBJECT_STRUCT_DESCRIPTION_IDX)
    ld a, (iy + OBJECT_STRUCT_DESCRIPTION_IDX + 1)
    ld de, current_action_text_buffer
    call get_text_from_bank
    ld hl, current_action_text_buffer
    ld de, CHRTBL2 + 8 * ((32 - SCREEN_WIDTH_IN_TILES * MSX_TILES_PER_ENGINE_TILE)/2) + REGULAR_TEXT_FIRST_ROW*32*8
    ld iyl, COLOR_WHITE * 16
    jp draw_multi_line_sentence


update_player_walking_still_moving:
    ret

update_player_walking_move_left:
    ; left-most screen limit:
    ld a, (ix + OBJECT_STRUCT_X)
    or a
    ret z
    add a, (PLAYER_WIDTH / 2) - (PLAYER_COLLISION_WIDTH / 2) - 1
    ld c, a
    push bc
        ld b, (ix + OBJECT_STRUCT_Y2)
        dec b
        call check_collision_mask
    pop bc
    ret nz

    ; mark that we moved:
    ld b, 1

    ; animation frame:
    ld a, (ix + OBJECT_STRUCT_X)
    dec a
    srl a
    cpl
IF PLAYER_WALK_LEFT_N_FRAMES == 4
    and #03
ELSE
IF PLAYER_WALK_LEFT_N_FRAMES == 2
    srl a
    and #01
ELSE
    xor a
ENDIF
ENDIF
    add a, OBJECT_DIRECTION_LEFT * 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a

    ; update the object coordinates:    
    dec (ix + OBJECT_STRUCT_X)
    dec (ix + OBJECT_STRUCT_X2)
    ret

update_player_walking_move_right:
    ; right-most screen limit:
    ld a, (ix + OBJECT_STRUCT_X)
    cp (MAX_ROOM_WIDTH * 16) - PLAYER_WIDTH
    ret z
    add a, PLAYER_WIDTH / 2 + (PLAYER_COLLISION_WIDTH / 2) + 1
    ld c, a
    push bc
        ld b, (ix + OBJECT_STRUCT_Y2)
        dec b
        call check_collision_mask
    pop bc
    ret nz

    ld b, 1

    ; animation frame:
    ld a, (ix + OBJECT_STRUCT_X)
    dec a
    srl a
IF PLAYER_WALK_RIGHT_N_FRAMES == 4
    and #03
ELSE
IF PLAYER_WALK_RIGHT_N_FRAMES == 2
    srl a
    and #01
ELSE
    xor a
ENDIF
ENDIF
    add a, OBJECT_DIRECTION_RIGHT * 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a

    ; update the object coordinates:    
    inc (ix + OBJECT_STRUCT_X)
    inc (ix + OBJECT_STRUCT_X2)
    ret

update_player_walking_move_up:
    ; top screen limit:
    ld a, (ix + OBJECT_STRUCT_Y)
    or a
    ret z

    ld a, (ix + OBJECT_STRUCT_X)
    add a, (PLAYER_WIDTH / 2) - (PLAYER_COLLISION_WIDTH / 2)
    ld c, a
    ld b, (ix + OBJECT_STRUCT_Y2)
    dec b
    dec b
    call check_collision_mask
    jp nz, update_player_walking_move_up_collision  ; collision
    ld a, c
    add a, PLAYER_COLLISION_WIDTH
    ld c, a
    call check_collision_mask
update_player_walking_move_up_collision:
    ld b, 0
    ret nz

    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y2)
    dec (ix + OBJECT_STRUCT_DEPTH)
    ; call reorder_object_by_depth_up
    ld (player_object_ptr), ix
    ld b, 1
    ; animation frame:
    ld a, (ix + OBJECT_STRUCT_Y)
    srl a
IF PLAYER_WALK_UP_N_FRAMES == 4
    and #03
ELSE
IF PLAYER_WALK_UP_N_FRAMES == 2
    srl a
    and #01
ELSE
    xor a
ENDIF
ENDIF
    add a, OBJECT_DIRECTION_UP * 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    ret

update_player_walking_move_down:
    ; bottom screen limit:
    ld a, (ix + OBJECT_STRUCT_Y)
    cp (MAX_ROOM_HEIGHT * 8) - PLAYER_HEIGHT
    ret z
    ld a, (ix + OBJECT_STRUCT_X)
    add a, (PLAYER_WIDTH / 2) - (PLAYER_COLLISION_WIDTH / 2)
    ld c, a
    ld b, (ix + OBJECT_STRUCT_Y2)
    call check_collision_mask
    jp nz, update_player_walking_move_down_collision  ; collision
    ld a, c
    add a, PLAYER_COLLISION_WIDTH
    ld c, a
    call check_collision_mask
update_player_walking_move_down_collision:
    ld b, 0
    ret nz  ; collision

    inc (ix + OBJECT_STRUCT_Y)
    inc (ix + OBJECT_STRUCT_Y2)
    inc (ix + OBJECT_STRUCT_DEPTH)
    ; call reorder_object_by_depth_down
    ld (player_object_ptr), ix
    ld b, 1
    ; animation frame:
    ld a, (ix + OBJECT_STRUCT_Y)
    srl a
IF PLAYER_WALK_DOWN_N_FRAMES == 4
    and #03
ELSE
IF PLAYER_WALK_DOWN_N_FRAMES == 2
    srl a
    and #01
ELSE
    xor a
ENDIF
ENDIF
    add a, OBJECT_DIRECTION_DOWN * 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    ret


update_player_walking_not_reached_close_enough:
IF USE_PATH_FINDING == 2  ; path finding on collision
    ld hl, path_finding_invoked
    ld a, (hl)
    or a
    jr nz, update_player_walking_not_reached_close_enough_even_with_path
    ld (hl), 1
    ld hl, player_current_action
    ld (hl), PLAYER_ACTION_WALKING
    call path_finding
    jp update_player_walking_check_if_there_is_path
update_player_walking_not_reached_close_enough_even_with_path:
ENDIF
    ; ld a, (current_event)
    ; cp TRIGGER_PICK_UP_OBJECT
    ; ret nz
update_player_walking_not_reached_close_enough_error:
    ld c, CANNOT_REACH_ERROR_MESSAGE_IDX / 256
    ld a, CANNOT_REACH_ERROR_MESSAGE_IDX % 256
    ld de, current_action_text_buffer
    call get_text_from_bank
    ld hl, current_action_text_buffer
    ld de, CHRTBL2 + 8 * ((32 - GUI_WIDTH) / 2) + REGULAR_TEXT_FIRST_ROW * 32 * 8
    ld iyl, COLOR_WHITE * 16
    jp draw_multi_line_sentence

