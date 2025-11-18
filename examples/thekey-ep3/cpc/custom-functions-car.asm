;-----------------------------------------------
; state variables:
; custom_ending_driving_scene_light_state: 
;  - %16: position of the object moving through the screen (could be out of scroll)
;  - /16: 00, 01, 10: light, 11: amstrad symbol
; custom_ending_driving_scene_car_state1: 
;  - 0: no car
;  - 1: approaching from left
;  - 2: full car drawn
;  - 3: disappearing from right
;  - 4: no car
; custom_ending_driving_scene_car_state2:
;  - position/timer in each of the states

custom_ending_driving_scene_light_state: equ general_buffer + 1
custom_ending_driving_scene_car_state1: equ general_buffer + 2
custom_ending_driving_scene_car_state2: equ general_buffer + 3
custom_ending_driving_scene_car_wobble: equ general_buffer + 4  ; 2 bytes
custom_ending_driving_scene_sprites: equ general_buffer + 6

custom_ending_driving_scene_car_sprite: equ custom_ending_driving_scene_sprites
custom_ending_driving_scene_fence_sprite: equ custom_ending_driving_scene_sprites + 40*24
custom_ending_driving_scene_light_sprite: equ custom_ending_driving_scene_fence_sprite + 4*8
custom_ending_driving_scene_light_clear_sprite: equ custom_ending_driving_scene_light_sprite + 4*40

;-----------------------------------------------
custom_ending_driving_scene:
    ld hl, palette_ending_driving_fade1
    call set_palette_16_setting_current

    ; draw mountains:
IF IS_6128
    SET_128K_PAGE_4000 CUTSCENE_PTRS_PAGE
ENDIF
    ld hl, cutscene_ending_driving_bg
    ld de, #c000 + 1*4 + 6*14*2*2  ; draw ptr
    ld bc, #0300
    ld iy, #0c00
    xor a  ; draw in mode 0
    call executeRuleScript_draw_cutscene_image_internal

    ld hl, custom_ending_driving_scene_sprites_zx0
    ld de, custom_ending_driving_scene_sprites
    call PAKET_UNPACK

    ; draw fence:
    ld hl, custom_ending_driving_scene_fence_sprite
    ld a, 12
    ld de, #c000 + 1*4 + 13*14*2*2
    ld bc, #0804
custom_ending_driving_scene_fence_draw_loop:
    push af
    push hl
    push bc
        push de
            call draw_sprite_variable_size_to_screen_no_transparency    
        pop hl
        ld bc, 4
        add hl, bc
        ex de, hl
    pop bc
    pop hl
    pop af
    dec a
    jr nz,custom_ending_driving_scene_fence_draw_loop
    
    ld bc, 50
    call wait_bc_halts

    ; set-palette("ending_driving")
    ld hl, palette_ending_driving
    call set_palette_16_setting_current

    xor a
    ld hl,custom_ending_driving_scene_light_state
    ld (hl), a
    inc hl
    ld (hl), a
    inc hl
    ld (hl), a
custom_ending_driving_scene_loop:
    call wait_for_next_frame
    call update_keyboard_buffers
    ld a, (keyboard_line_clicks + 4)
    bit 7, a
    jr nz, custom_ending_driving_scene_loop_done_no_space

    call custom_ending_driving_scene_update_car
    call custom_ending_driving_scene_update_lights

    ; See if we want to load a new song:
    call song_load_request_check

    jr custom_ending_driving_scene_loop
custom_ending_driving_scene_loop_done:

    call wait_for_space

custom_ending_driving_scene_loop_done_no_space:

    ld hl, palette_ending_driving_fade1
    call set_palette_16_setting_current

    ld bc, 50
    call wait_bc_halts
    ret


;-----------------------------------------------
custom_ending_driving_scene_update_car:
    ld hl, game_cycle
    ld a, (hl)
    and #07
    ret nz
    ; wobble up and down
    ld bc,0
    ld a, (hl)
    bit 5,a
    jr z,custom_ending_driving_scene_update_car_no_wobble
    ld bc,#0800
custom_ending_driving_scene_update_car_no_wobble:
    ld (custom_ending_driving_scene_car_wobble), bc
custom_ending_driving_scene_update_car_just_changed_state:

    ld hl, custom_ending_driving_scene_car_state1
    ld a, (hl)
    or a
    jr z,custom_ending_driving_scene_update_car_state1_0
    dec a
    jr z,custom_ending_driving_scene_update_car_state1_1
    dec a
    jr z,custom_ending_driving_scene_update_car_state1_2
    dec a
;     jr z,custom_ending_driving_scene_update_car_state1_3
    ; state 3 is when the car is already gone, so, nothing to do, just "ret"
    ret nz
custom_ending_driving_scene_update_car_state1_3:
    ; car disappearing form the right:
    inc hl  ; custom_ending_driving_scene_car_state2
    ld a, (hl)
    inc a
    cp a,40
    jr z,custom_ending_driving_scene_update_car_next_state
    ld (hl),a
    ld hl,#c000 + 3*4 + 14*14*2*2  ; draw ptr
    ld bc, (custom_ending_driving_scene_car_wobble)
    add hl,bc
    ld b,0
    ld c,a
    push bc
    pop ix
    add hl,bc
    ex de,hl
    ld hl,custom_ending_driving_scene_car_sprite
    neg
    add a,40
    ld b,24
    ld c,a
    jp draw_sprite_slice_to_screen

custom_ending_driving_scene_update_car_state1_2:
    ; car moving in the center:
    inc hl  ; custom_ending_driving_scene_car_state2
    inc (hl)
    ld a, (hl)
    cp 8
    jr z,custom_ending_driving_scene_update_car_next_state

    ld hl, #c000 + 1*4 + 14*14*2*2  ; draw ptr
    ld bc, (custom_ending_driving_scene_car_wobble)
    add hl,bc
    ld b, 0
    ld c, a
    add hl,bc
    ex de ,hl
    ld hl,custom_ending_driving_scene_car_sprite
    ld bc,24 * 256 + 40
    jp draw_sprite_variable_size_to_screen_no_transparency

custom_ending_driving_scene_update_car_state1_0:
    ; waiting for car to appear
    inc hl  ; custom_ending_driving_scene_car_state2
    inc (hl)
    ld a,(hl)
    cp 32
    ret nz
    ; move to state 1:
custom_ending_driving_scene_update_car_next_state:
    ld (hl), 0
    dec hl
    inc (hl)
    ld a, (hl)
    cp 1
    jr z, custom_ending_driving_scene_update_car_next_state_text_1
    cp 3
    jr z, custom_ending_driving_scene_update_car_next_state_text_2
    cp 4
    jr z, custom_ending_driving_scene_update_car_next_state_text_3
    jr custom_ending_driving_scene_update_car_just_changed_state

custom_ending_driving_scene_update_car_state1_1:
    ; car appearing from the left:
    inc hl  ; custom_ending_driving_scene_car_state2
    inc (hl)
    ld a, (hl)
    cp 40
    jr z,custom_ending_driving_scene_update_car_next_state

    ld hl,custom_ending_driving_scene_car_sprite
    push af
        neg
        add a,40
        ld b,0
        ld c,a
        add hl,bc
        push bc
        pop ix
    pop af
    ld de, #c000 + 1*4 + 14*14*2*2  ; draw ptr
    ex de,hl
    ld bc, (custom_ending_driving_scene_car_wobble)
    add hl,bc
    ex de,hl
    ld b,24
    ld c,a
    jp draw_sprite_slice_to_screen

custom_ending_driving_scene_update_car_next_state_text_1:
    ld hl, ending_drive_back_auxiliar1
custom_ending_driving_scene_update_car_next_state_text_1_continue:
    ld a, (last_script_in_this_rule)
    push af
    call executeRuleScript_internal
    pop af
    ld (last_script_in_this_rule), a    
    ; we need to re unpack the data, since text rendering would have messed this up:
    ld hl, custom_ending_driving_scene_sprites_zx0
    ld de, custom_ending_driving_scene_sprites
    call PAKET_UNPACK    
    jp custom_ending_driving_scene_update_car_just_changed_state
custom_ending_driving_scene_update_car_next_state_text_2:
    call clear_text_area_for_scroll
    jp custom_ending_driving_scene_update_car_just_changed_state
custom_ending_driving_scene_update_car_next_state_text_3:
    ld hl, ending_drive_back_auxiliar2
    jr custom_ending_driving_scene_update_car_next_state_text_1_continue


; ------------------------------------------------
custom_ending_driving_scene_update_lights:
    ld hl,custom_ending_driving_scene_light_state
    inc (hl)
    ld a, (hl)
    and #0f
    ; Draw a fence at tile "a":
    push af
        cp 12
        jr nc,custom_ending_driving_scene_update_lights_no_draw
        add a, a
        add a, a
        ld b, 0
        ld c, a
        ld hl, #c000 + 1*4 + 9*14*2*2  ; draw ptr
        add hl, bc
        ex de, hl
        ld bc, #2804
        ld hl,custom_ending_driving_scene_light_sprite
        call draw_sprite_variable_size_to_screen_no_transparency

custom_ending_driving_scene_update_lights_no_draw:
    pop af
    ; Delete the previous fence at "a+1"
    inc a
    and #0f
    cp 12
    ret nc
    add a,a
    add a,a
    ld b,0
    ld c,a
    ld hl, #c000 + 1*4 + 9*14*2*2  ; draw ptr
    add hl,bc
    ex de,hl
    ld bc, #2804
    ld hl,custom_ending_driving_scene_light_clear_sprite
    jp draw_sprite_variable_size_to_screen_no_transparency


; ------------------------------------------------
; Draws a sprite/tile to video memory (mode 0)
; Arguments:
; - hl: sprite to paint
; - de: video memory address of the top-left pixel (assuming only even x coordinate positions)
; - c: width (in bytes) of the slice to draw
; - b: height
; - ix: stride in bytes 
draw_sprite_slice_to_screen:
draw_sprite_slice_to_screen_loop_y:
    push bc
        ld b,0
        push de
            ldir
            ex de,hl
            ; hl += ix
            push ix
                add ix,de
                push ix
                pop hl
            pop ix
        pop de
        ld a,d
        add a,#08
        ld d,a
        sub #c0
        jr nc, draw_sprite_slice_to_screen_loop_next_line
        ld bc, #c000 + SCREEN_WIDTH_IN_BYTES
        ex de,hl
        add hl,bc
        ex de,hl
draw_sprite_slice_to_screen_loop_next_line:
    pop bc
    djnz draw_sprite_slice_to_screen_loop_y
    ret

