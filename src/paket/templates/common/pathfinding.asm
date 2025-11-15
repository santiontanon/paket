path_finding_parent_buffer:         equ general_buffer
path_finding_open_list:             equ general_buffer + PATH_FINDING_MAP_WIDTH * PATH_FINDING_MAP_HEIGHT
path_finding_open_list_end:         equ path_finding_open_list + PATH_FINDING_OPEN_LIST_SIZE * 4
path_finding_open_list_first_ptr:   equ path_finding_open_list_end
path_finding_open_list_next_ptr:    equ path_finding_open_list_first_ptr + 2
path_finding_best_distance:         equ path_finding_open_list_next_ptr + 2
path_finding_best_node:             equ path_finding_best_distance + 1

PATHFINDING_RIGHT: equ 1
PATHFINDING_DOWN: equ 2
PATHFINDING_LEFT: equ 3
PATHFINDING_UP: equ 4


;-----------------------------------------------
; Input:
; - target destination is in: (player_current_action_parameters), (player_current_action_parameters+1)
; - player in (player_object_ptr)
; Output:
; - the path that was found in "path_finding_path"
path_finding:
    ; initialize the path-finding buffers:
    ; - path finding resolution will be 4x4 pixels in CPC (tiles divided in 4).
    ; - in "general_buffer": a PATH_FINDING_MAP_WIDTH * PATH_FINDING_MAP_HEIGHT buffer to
    ;   store "parent" (initialized to #ff). This is at "collision tile" level.
    ; - an "open" list, starting at "general_buffer + PATH_FINDING_MAP_WIDTH * PATH_FINDING_MAP_HEIGHT"
    ;   where each element is 4 bytes:
    ;   - A 2 byte pointer to the buffer above,
    ;   - x
    ;   - y
    ; - "open" list is a circular list, with at most 128 elements. (512 bytes).
    ld bc, (player_current_action_parameters)
    ld iyl, c  ; target x
    ld iyh, b  ; target y
    ld (path_finding_stack), sp

    ; Initialize parent buffer:
    ld hl, path_finding_parent_buffer
    ld a, #ff
    ld bc, PATH_FINDING_MAP_WIDTH * PATH_FINDING_MAP_HEIGHT
    call clear_memory_to_a

    ; Initialize the open list:

    ; Get the starting position of the player (we consider the left-pixel of the
    ; collision base):
    ; - collision offset = (x / TILE_WIDTH) * room_height + (y / 8)
    ; - collision bit = bit mask calculated by "check_collision_mask"
    ; - path finding offset = (x / PATH_FINDING_WALK_TILE_WIDTH) * PATH_FINDING_MAP_HEIGHT + (y / PATH_FINDING_WALK_TILE_HEIGHT)
    ; add it to the open list
    call get_player_ptr
    ld a, (ix + OBJECT_STRUCT_X)
    add a, (PLAYER_WIDTH / 2) - (PLAYER_COLLISION_WIDTH / 2)
    ld c, a
    ld a, (ix + OBJECT_STRUCT_Y2)
    dec a
    ld b, a
    call ptr_to_path_finding_tile
    ex de, hl
    ld hl, path_finding_open_list
    ld (path_finding_open_list_first_ptr), hl
    ld (path_finding_open_list_next_ptr), hl

    ld a, #ff
    ld (path_finding_best_distance), a
    ld (path_finding_best_node), de

    ; This push de/bc is just because "path_finding_push_node_to_open_list" will
    ; assume they are pushed if it happens to find "distance 0" and no search
    ; is needed.
    push de
        xor a  ; start of the path
        call path_finding_push_node_to_open_list
    pop de

    ; Path-finding loop:
    ; - It ends if we reach the goal, or if the open list becomes empty
path_finding_loop:
    ; Check if the open list becomes empty:
    ld hl, (path_finding_open_list_first_ptr)
    ld a, (path_finding_open_list_next_ptr)
    cp l
    jp nz, path_finding_loop_not_done
    ld a, (path_finding_open_list_next_ptr + 1)
    cp h
    jp z, path_finding_loop_done
path_finding_loop_not_done:
    ; remove the first element of the list:
    ; - get the ptr to path finding buffer in de, and x,y in bc
    ; - for each of the possible 4 neighbors:
    ;   - get the new de
    ;   - if it was visited already, ignore
    ;   - get the new bc
    ;   - if collision, ignore
    ;   - otherwise, mark in "de" the parent, and push "de/bc" to the open list
    ;   - calculate the "distance" to the goal, and if it's better than the
    ;     current best, replace. In case it's 0, end the search.
    ld e, (hl)
    inc hl
    ld d, (hl)
    inc hl
    ld c, (hl)
    inc hl
    ld b, (hl)
    inc hl
    ld a, h
    cp path_finding_open_list_end / 256
    call z, path_finding_open_list_ptr_wrap_around_check2
    ld (path_finding_open_list_first_ptr), hl

path_finding_loop_left:
    ; left neighbor:
    dec de
    ld a, (de)
    inc a
    jp nz, path_finding_loop_up  ; if it's already visited, ignore
    ld a, c
    cp PATH_FINDING_WALK_TILE_WIDTH  ; check if we are at the edge of the screen
    jp c, path_finding_loop_up
    add a, -PATH_FINDING_WALK_TILE_WIDTH
    ld c, a
    push de
        call check_collision_mask_for_path_finding
    pop de
    ; mark parent in "de", and push "de/bc" to the open list:
    ld a, PATHFINDING_RIGHT  ; coming from the right
    call z, path_finding_push_node_to_open_list
    ld a, c
    add a, PATH_FINDING_WALK_TILE_WIDTH
    ld c, a

path_finding_loop_up:
    inc de
    push de
        ; up neighbor:
        ld hl, -PATH_FINDING_MAP_WIDTH
        add hl, de
        ld a, (hl)
        inc a
        jp nz, path_finding_loop_right  ; if it's already visited, ignore
        ld a, b
        cp PATH_FINDING_WALK_TILE_HEIGHT  ; check if we are at the edge of the screen
        jp c, path_finding_loop_right
        add a, -PATH_FINDING_WALK_TILE_HEIGHT
        ld b, a
        push hl
            call check_collision_mask_for_path_finding
        pop de  ; pop into "de", since we had moved it into "hl" above
        jr nz, path_finding_loop_right_adjust_b
        ; Check the right edge of the player collision box:
        ld a, c
        add a, PLAYER_COLLISION_WIDTH
        ld c, a
        push de
            call check_collision_mask_for_path_finding_a_set
        pop de
        ex af, af'
            ld a, c
            add a, -PLAYER_COLLISION_WIDTH
            ld c, a
        ex af, af'
        ; mark parent in "de", and push "de/bc" to the open list:
        ld a, PATHFINDING_DOWN
        call z, path_finding_push_node_to_open_list
path_finding_loop_right_adjust_b:
        ld a, b
        add a, PATH_FINDING_WALK_TILE_HEIGHT
        ld b, a

path_finding_loop_right:
    pop de
    ; right neighbor:
    inc de
    ld a, (de)
    inc a
    jp nz, path_finding_loop_down  ; if it's already visited, ignore
    ld a, c
    cp (PATH_FINDING_MAP_WIDTH - 1) * PATH_FINDING_WALK_TILE_WIDTH  ; check if we are at the edge of the screen
    jp nc, path_finding_loop_down
    ; We add "PLAYER_COLLISION_WIDTH" to account for the fact that we are checking
    ; collisions to the right now (while the coordinates we had were for the
    ; left-side of the player)
    add a, PLAYER_COLLISION_WIDTH + PATH_FINDING_WALK_TILE_WIDTH
    ld c, a
    push de
        call check_collision_mask_for_path_finding_a_set
    pop de
    ; restore the left-side of the player:
    ex af, af'
        ld a, c
        add a, -PLAYER_COLLISION_WIDTH
        ld c, a
    ex af, af'
    ; mark parent in "de", and push "de/bc" to the open list:
    ld a, PATHFINDING_LEFT
    call z, path_finding_push_node_to_open_list
    ld a, c
    add a, -PATH_FINDING_WALK_TILE_WIDTH
    ld c, a

path_finding_loop_down:
    ; down neighbor:
    ld hl, PATH_FINDING_MAP_WIDTH - 1  ; "-1" to correct for the "inc de" we did when looking for the "right neighbor"
    add hl, de
    ld a, (hl)
    inc a
    jp nz, path_finding_loop  ; if it's already visited, ignore
    ld a, b
    cp (PATH_FINDING_MAP_HEIGHT - 1) * PATH_FINDING_WALK_TILE_HEIGHT  ; check if we are at the edge of the screen
    jp nc, path_finding_loop
    add a, PATH_FINDING_WALK_TILE_HEIGHT
    ld b, a
    push hl
        call check_collision_mask_for_path_finding
    pop de  ; pop into "de", since we had moved it into "hl" above
    jp nz, path_finding_loop
    ; Check the right edge of the player collision box:
    push bc
        ld a, c
        add a, PLAYER_COLLISION_WIDTH
        ld c, a
        push de
            call check_collision_mask_for_path_finding_a_set
        pop de
    pop bc
    ; mark parent in "de", and push "de/bc" to the open list:
    ld a, PATHFINDING_UP
    call z, path_finding_push_node_to_open_list
    jp path_finding_loop
    

path_finding_loop_done:
    ld hl, path_finding_path_buffer
    ld (hl), #ff
    inc hl
    ld (hl), #ff
    inc hl

    ; Extract the path and store it so the player can execute it:
    ld a, (path_finding_best_distance)
    inc a  ; Not a single node explored
    jr z, path_finding_loop_done_end_of_path
    ld de, (path_finding_best_node)
    
    ; We recreate the path using the final node position as 0,0, and then we
    ; will update all the coordinates backwards, once we reach the start point:
    ld bc, 0
    ld a, (de)
    or a  ; if the best node is the start location
    jr z, path_finding_loop_done_end_of_path
path_finding_reconstruct_path_loop:
    ld (hl), c
    inc hl
    ld (hl), b
    inc hl

    ; Check if the path is too long:
    ld a, (path_finding_path_buffer + PATH_FINDING_MAX_LENGTH * 2) % 256
    cp l
    jr nz, path_finding_reconstruct_path_loop_not_too_long
    ld a, (path_finding_path_buffer + PATH_FINDING_MAX_LENGTH * 2) / 256
    cp h
    jr z, path_finding_path_too_long  ; Path is too long for our buffer!
path_finding_reconstruct_path_loop_not_too_long:

    ld a, (de)
    dec a  ; "from right"
    jr z,path_finding_reconstruct_path_loop_from_right
    dec a  ; "from down"
    jr z,path_finding_reconstruct_path_loop_from_down
    dec a  ; "from left"
    jr z,path_finding_reconstruct_path_loop_from_left
    dec a  ; "from up"
    jr z,path_finding_reconstruct_path_loop_from_up

    jr path_finding_loop_done_end_of_path

path_finding_reconstruct_path_loop_from_right:
    ld a, c
    add a, PATH_FINDING_WALK_TILE_WIDTH
    ld c, a
    inc de
    jr path_finding_reconstruct_path_loop

path_finding_reconstruct_path_loop_from_down:
    ld a, b
    add a, PATH_FINDING_WALK_TILE_HEIGHT
    ld b, a
    push hl
        ld hl, PATH_FINDING_MAP_WIDTH
        add hl, de
        ex de, hl
    pop hl
    jr path_finding_reconstruct_path_loop
    
path_finding_reconstruct_path_loop_from_left:
    ld a, c
    add a, -PATH_FINDING_WALK_TILE_WIDTH
    ld c, a
    dec de
    jr path_finding_reconstruct_path_loop

path_finding_reconstruct_path_loop_from_up:
    ld a, b
    add a, -PATH_FINDING_WALK_TILE_HEIGHT
    ld b, a
    push hl
        ld hl, -PATH_FINDING_MAP_WIDTH
        add hl, de
        ex de, hl
    pop hl
    jr path_finding_reconstruct_path_loop


path_finding_path_too_long:
    ld hl, path_finding_path_buffer + 2


path_finding_loop_done_end_of_path:
    dec hl
    dec hl
    ld (path_finding_path_ptr), hl
    inc hl
    inc hl

    ; Now go backwards on the path, updating the x,y coordinates:
    ; - At this point, the start position is "c,b" (current), but it should have
    ;   been "(ix+OBJECT_STRUCT_X),(ix+OBJECT_STRUCT_Y)" (target).
    ; - So, we calculate "target - current", and that's the offset we need to
    ;   add to all the coordinates in the path.
    ld a, (ix + OBJECT_STRUCT_X)
    ; add a,(PLAYER_WIDTH/2)-(PLAYER_COLLISION_WIDTH/2)
    add a, (PLAYER_WIDTH / 2)
    sub c
    ld c, a
    ld a, (ix + OBJECT_STRUCT_Y)
    sub b
    ld b, a  ; bc now has how much we need to add to each coordinate in the path

path_finding_adjust_path_coordinates_loop:
    dec hl
    ld a, (hl)
    cp #ff
    jr z, path_finding_adjust_path_coordinates_done

    ; add the offset
    add a, b  ; a contains the "y" coordinate of the waypoint
    ld (hl), a
    dec hl
    ld a, (hl)  ; "x"
    add a, c
    ld (hl), a
    jr path_finding_adjust_path_coordinates_loop

path_finding_adjust_path_coordinates_done:
    ; Remove the first waypoint from the path (to prevent jerky movement, since
    ; the waypoint coordinates are at a coarser level than pixels):
    inc hl
    ld (hl), #ff
    inc hl
    ld (hl), #ff    
    ret


;-----------------------------------------------
; If an open list pointer has reached the end of the list, wrap around to the
; beginning again.
; input:
; - hl: open list ptr
; output:
; - hl: updated open list ptr
; path_finding_open_list_ptr_wrap_around:
;     ld a, h
;     cp path_finding_open_list_end / 256
;     ret nz
path_finding_open_list_ptr_wrap_around_check2:
    ld a, l
    cp path_finding_open_list_end % 256
    ret nz
    ld hl, path_finding_open_list
    ret


;-----------------------------------------------
path_finding_push_node_to_open_list:
    ld (de), a
    ld hl, (path_finding_open_list_next_ptr)
    ld (hl), e
    inc hl
    ld (hl), d
    inc hl
    ld (hl), c
    inc hl
    ld (hl), b
    inc hl
    ld a, h
    cp path_finding_open_list_end / 256
    call z, path_finding_open_list_ptr_wrap_around_check2
    ld (path_finding_open_list_next_ptr), hl

    ; check the distance to the goal and see if we have a new best:
    ld a, iyl  ; target x
    sub c
    jp p, path_finding_push_node_to_open_list_continue1
    neg
path_finding_push_node_to_open_list_continue1:
    ld l, a
    srl l ; We divide by 2, to prevent overflow in this distance calculation,
          ; as we are only using 8 bits
    ld a, iyh  ; target y
    sub b
    jp p, path_finding_push_node_to_open_list_continue2
    neg
path_finding_push_node_to_open_list_continue2:
    srl a
    add a, l  ; "a" now has the distance in pixels (divided by 2)
    ld hl, path_finding_best_distance
    cp (hl)
    ret nc  ; ret if no new best
    ; new best:
    ld (hl), a
    ld (path_finding_best_node), de
    or a
    ret nz  ; not reached the goal
path_finding_push_node_to_open_list_reached_goal:
    ld sp, (path_finding_stack)
    jp path_finding_loop_done


;-----------------------------------------------
; input:
; - c, b: x, y pixel coordinates
; output:
; - hl: ptr to the path_finding tile
ptr_to_path_finding_tile:
    ; ptr = path_finding_parent_buffer + (x / PATH_FINDING_WALK_TILE_WIDTH) * PATH_FINDING_MAP_HEIGHT + (y / PATH_FINDING_WALK_TILE_HEIGHT)
    ld d, 0
    ld e, PATH_FINDING_MAP_HEIGHT
    ld a, c  ; x
    srl a
    srl a
IF PATH_FINDING_WALK_TILE_WIDTH == 8
    srl a
ENDIF
    push bc
        call mul8  ; hl = (x / PATH_FINDING_WALK_TILE_WIDTH) * PATH_FINDING_MAP_HEIGHT
    pop bc
    ld e, b
IF PATH_FINDING_WALK_TILE_HEIGHT != 4
    ERROR "ptr_to_path_finding_tile assumes that PATH_FINDING_WALK_TILE_HEIGHT = 4"
ENDIF
    srl e
    srl e
    ld d, 0
    add hl, de
    ld de, path_finding_parent_buffer
    add hl, de
    ret
