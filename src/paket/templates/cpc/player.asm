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
    ld a, (iy + OBJECT_STRUCT_X)
    add a, (iy + OBJECT_STRUCT_X2)
    srl a  ; a = (x + x2) / 2

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
    call path_finding
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
    add a, 2
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
    and #01     ; player speed, change to #03 to make it slower
    ret nz

    call get_player_ptr
    ld a,(ix + OBJECT_STRUCT_ID)
    cp PLAYER_OBJECT_ID
    call nz, find_player_object_ptr  ; if for some reason the player object pointer has changed, we update it

    ; mark the current position as dirty:
    ld hl, dirty_column_buffer
    ld a,(ix + OBJECT_STRUCT_X)
    srl a
    ADD_HL_A_VIA_BC
REPT PLAYER_WIDTH / 2
    ld (hl), 1
    inc hl
ENDR
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
    jr z,update_player_walking_new_position_y_calculated
    push af
        call m, update_player_walking_move_up
    pop af
    call p, update_player_walking_move_down
update_player_walking_new_position_y_calculated:
    ld a, (player_current_action_parameters)
    sub PLAYER_WIDTH / 2
    cp (ix + OBJECT_STRUCT_X)
    jr z, update_player_walking_new_position_calculated
    push af
        call m, update_player_walking_move_left
    pop af
    call p, update_player_walking_move_right

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
    ld a, (player_current_action_parameters)
    sub PLAYER_WIDTH / 2
    sub (ix + OBJECT_STRUCT_X)
    jp p, update_player_walking_new_position_calculated_x_diff_positive
    neg
update_player_walking_new_position_calculated_x_diff_positive:
    ld c, a
    ; here "c" contains |difference in x| and "b" contains |difference in y|
    ; if we are going to an object, take the object width into account,
    ; otherwise, just check if we are close enough:
    ld iy, (player_current_action_target_object)
    ld a, iyl
    add a, iyh
    jr z, update_player_walking_new_position_calculated_no_object

    ; Subtract half the width of the object to |difference in x|, to account
    ; for object width:
    ld a, (iy + OBJECT_STRUCT_X2)
    sub (iy + OBJECT_STRUCT_X)
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
    add a, b  ; |difference in x| + |difference in y| of the target position!
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
    ld de, VIDEO_MEMORY + REGULAR_TEXT_FIRST_ROW * SCREEN_WIDTH_IN_BYTES
    ld iyl, #88    ; color (white)
    jp draw_multi_line_sentence


update_player_walking_still_moving:
    ; mark the new position as dirty:
    ld hl,dirty_column_buffer
    ld a,(ix+OBJECT_STRUCT_X)
    srl a
    ADD_HL_A_VIA_BC
REPT PLAYER_WIDTH / 2
    ld (hl),1
    inc hl
ENDR
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
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH
    ld (ix + OBJECT_STRUCT_X2), a
    ret

update_player_walking_move_right:
    ; right-most screen limit:
    ld a, (room_buffer + ROOM_STRUCT_WIDTH)
    add a, a
    add a, a
    add a, a
    sub PLAYER_WIDTH
    ld e, a  ; e has the room width in pixels - PLAYER_WIDTH
    ld a, (ix + OBJECT_STRUCT_X)
    cp e
    ret z
    add a,(PLAYER_WIDTH/2)+(PLAYER_COLLISION_WIDTH/2)+1
    ld c,a
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
    add a,OBJECT_DIRECTION_RIGHT * 16
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a

    ; update the object coordinates:    
    inc (ix + OBJECT_STRUCT_X)
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH
    ld (ix + OBJECT_STRUCT_X2), a
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
    ret nz  ; collision

    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y2)
    dec (ix + OBJECT_STRUCT_DEPTH)
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH
    ld (ix + OBJECT_STRUCT_X2), a    
    call reorder_object_by_depth_up
    ld (player_object_ptr),ix
    ld b,1
    ; animation frame:
    ld a,(ix+OBJECT_STRUCT_Y)
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
    add a,OBJECT_DIRECTION_UP*16
    ld (ix+OBJECT_STRUCT_STATE_DIRECTION),a
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
    ld a, (ix + OBJECT_STRUCT_X)
    add a, PLAYER_WIDTH
    ld (ix + OBJECT_STRUCT_X2), a    
    call reorder_object_by_depth_down
    ld (player_object_ptr),ix
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
    ld de, VIDEO_MEMORY + REGULAR_TEXT_FIRST_ROW*SCREEN_WIDTH_IN_BYTES
    ld iyl, #88    ; color (white)
    jp draw_multi_line_sentence


;-----------------------------------------------
; Checks if the object pointed by ix should be moved earlier in the room object list
; output:
; - ix: the new pointer to the object
reorder_object_by_depth_up:
    ; get the pointer to the first object:
    ld hl,room_buffer+ROOM_STRUCT_OBJECT_DATA
    ld b,ixh
    ld c,ixl
    xor a    ; clear the carry flag
    sbc hl,bc
    ret z    ; the current object is the first in the list, so nothing else to do!
    push ix
    pop iy
    ld bc,-OBJECT_STRUCT_SIZE
    add iy,bc
    ld a,(ix+OBJECT_STRUCT_DEPTH)
    cp (iy+OBJECT_STRUCT_DEPTH)
    ret p    ; if the current object has a higher or equal depth than the previous object, we are done!
    ; swap the objects:
        call reorder_object_swap_objects
    jr reorder_object_by_depth_up


;-----------------------------------------------
; Checks if the object pointed by ix should be moved later in the room object list
; output:
; - nz if the object was moved
; - ix: the new pointer to the object
reorder_object_by_depth_down:
    ; get the pointer to the last object:
    ld hl,(last_room_object_ptr)
    ld b,ixh
    ld c,ixl
    xor a    ; clear the carry flag
    sbc hl,bc
    ret z    ; the current object is the last in the list, so nothing else to do!
    push ix
    pop iy
    ld bc,OBJECT_STRUCT_SIZE
    add iy,bc
    ld a,(iy+OBJECT_STRUCT_DEPTH)
    cp (ix+OBJECT_STRUCT_DEPTH)
    ret p    ; if the next object has a higher or equal depth than the current object, we are done!
    ; swap the objects:
        call reorder_object_swap_objects
    jr reorder_object_by_depth_down



;-----------------------------------------------
; swaps two objects (pointed by "ix" an "iy")
reorder_object_swap_objects:
    ; check if the current selected object is one of these two, to update 
    ; the pointer:
    ld bc,(player_current_action_target_object)
    push iy
    pop hl
    xor a
    sbc hl,bc
    jr nz,reorder_object_by_depth_up_not_iy
    ; we had "iy" selected:
    ld (player_current_action_target_object),ix
    jr reorder_object_by_depth_up_continue
reorder_object_by_depth_up_not_iy:
    push ix
    pop hl
    xor a
    sbc hl,bc
    jr nz,reorder_object_by_depth_up_continue
    ; we had "ix" selected:
    ld (player_current_action_target_object),iy
reorder_object_by_depth_up_continue:
    push iy
            ld b,OBJECT_STRUCT_SIZE
reorder_object_by_depth_up_copy_loop:
            ld c,(ix)
            ld a,(iy)
            ld (ix),a
            ld (iy),c
            inc ix
            inc iy
            djnz reorder_object_by_depth_up_copy_loop
    pop ix    ; ix now has the position of the previous object!
    ret

