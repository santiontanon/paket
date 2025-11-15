;-----------------------------------------------
; finds the pointer to the current state of ab object
; input:
; - ix: object ptr
; output:
; - hl: current state ptr
find_object_state_ptr:
    ld l, (ix + OBJECT_STRUCT_TYPE_PTR)
    ld h, (ix + OBJECT_STRUCT_TYPE_PTR + 1)
    ; find the current state:
find_object_state_ptr_loop:
    ld a, (hl)
    cp (ix + OBJECT_STRUCT_STATE_DIRECTION)
    ret z
    inc hl
    ld c, (hl)
    inc hl
    ld b,(hl)
    inc hl
    add hl, bc
    jr find_object_state_ptr_loop


; ------------------------------------------------
; input:
; - hl: pointer to the current state (pointing at the state size)
; - ix: pointer to the current object
; output:
; - hl: pointer to the current animation frame
object_skip_to_current_animation_frame:
    ; The object type state data consists of:
    ; - state+direction: 1 byte
    ; - state length: 2 bytes  <- input hl points here
    ; - animation tempo: 1 byte
    ; - animation frames: n*2 bytes (each is the offset we need to add to get to the frame)
    ; - for each frame:
    ;  - image size: 2 bytes
    ;  - image: n bytes: selection mask, width, height, image data
    inc hl  ; we skip the two state length bytes
    inc hl    
    inc hl  ; # animation frames
    inc hl  ; animation tempo    
    ld b, 0
    ld c, (ix + OBJECT_STRUCT_ANIMATION_STEP)       
    add hl, bc
    ld c, (hl)
    inc hl
    ld b, (hl)
    add hl, bc  ; each step stores the offset we need to add to hl from here
    ret


; ------------------------------------------------
update_object_animations:
    ld a, (room_buffer + ROOM_STRUCT_N_OBJECTS)
    or a
    ret z
    ld b,a
    ld ix, room_buffer + ROOM_STRUCT_OBJECT_DATA
    ld de, OBJECT_STRUCT_SIZE
update_object_animations_loop:
    push bc
        ld a, (ix + OBJECT_STRUCT_ANIMATION_TIMER)
        inc a  ; if timer == #ff, it means this object has no animation
        jp z,update_object_animations_loop_next
        ld l, (ix + OBJECT_STRUCT_TYPE_PTR)
        ld h, (ix + OBJECT_STRUCT_TYPE_PTR + 1)
update_object_animations_loop_state_loop:
        ld a, (hl)  ; state
        inc hl
        cp (ix + OBJECT_STRUCT_STATE_DIRECTION)
        jr z, update_object_animations_state_found
        ld c, (hl)
        inc hl
        ld b, (hl)
        inc hl
        add hl, bc
        jr update_object_animations_loop_state_loop
update_object_animations_state_found:
        inc hl  ; we skip the two state length bytes
        inc hl    
        ld a, (hl)  ; # animation frames
        cp 1  ; if there is only one animation frame, no need for animation
        jr z, update_object_animations_loop_next_mark_no_animation
        
        ; animation cycle:
        ; - increment timer, if it matches tempo, increase step
        ; - if step is equal to # animation frames, loop back
        ld c, a  ; # frames
        inc hl
        ld b, (hl)  ; tempo
        inc (ix + OBJECT_STRUCT_ANIMATION_TIMER)
        ld a, (ix + OBJECT_STRUCT_ANIMATION_TIMER)
        cp b
        jr c, update_object_animations_loop_next
update_object_animations_loop_next_frame:
        ; next step!
        sub b  ; We subtract the tempo from the current timer.
               ; We do not set this to 0 directly, so that objects that start
               ; with a timer set to a high value at room start, skip the
               ; corresponding number of frames.
        
        ld (ix + OBJECT_STRUCT_ANIMATION_TIMER), a
        ld a, (ix + OBJECT_STRUCT_ANIMATION_STEP)
        srl a  ; divide by 2
        inc a
        cp c  ; do we need to loop back?
        jr nz, update_object_animations_state_found_no_loop_back
        xor a
update_object_animations_state_found_no_loop_back:
        add a, a
        ld (ix + OBJECT_STRUCT_ANIMATION_STEP), a
        ; check if we need to skip even more frames:
        ld a, (ix + OBJECT_STRUCT_ANIMATION_TIMER)
        ld b, (hl)
        cp b
        jr nc, update_object_animations_loop_next_frame

        ; mark dirty columns:
        ld hl, dirty_column_buffer
        ld a, (ix + OBJECT_STRUCT_X)
        srl a
        ADD_HL_A_VIA_BC
        ld a, (ix + OBJECT_STRUCT_X2)
        sub (ix + OBJECT_STRUCT_X)
        srl a
        ld b, a
update_object_animations_state_found_dirty_column_loop:
        ld (hl), 1
        inc hl
        djnz update_object_animations_state_found_dirty_column_loop

        ; Update dirty_min_y/dirty_max_y:
        ld a, (ix + OBJECT_STRUCT_Y)
        srl a
        srl a
        srl a
        ; if a < (dirty_min_y): (dirty_min_y) = a
        ld hl, dirty_min_y
        cp (hl)
        jr nc, update_object_animations_state_found_skip_dirty_min_y
        ld (hl), a
update_object_animations_state_found_skip_dirty_min_y:

        ld a, (ix + OBJECT_STRUCT_Y2)
        add a, 7  ; even if a single pixel reaches the next tile, we need to
                  ; redraw it.
        srl a
        srl a
        srl a
        ; if a > (dirty_max_y): (dirty_max_y) = a
        ld hl, dirty_max_y
        cp (hl)
        jr c, update_object_animations_state_found_skip_dirty_max_y
        ld (hl), a
update_object_animations_state_found_skip_dirty_max_y:

        jr update_object_animations_loop_next
update_object_animations_loop_next_mark_no_animation:
        ld (ix + OBJECT_STRUCT_ANIMATION_TIMER), #ff  ; mark that this object has no animation to speed up things
update_object_animations_loop_next:
        add ix, de
    pop bc
    dec b
    jp nz, update_object_animations_loop
    ret
