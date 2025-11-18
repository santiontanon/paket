;-----------------------------------------------
; Actual:
; - RA: 10:30 - 00:00  (variable ranges from 0 (00:00) to 42 (10:30))
; - DE: 70 - -10  (variable ranges from 0 (-10) to 16 (70))

; - galaxy dimensions: 112 * 176 pixels (14 * 22 tiles)
; - viewport dimensions: 24 * 48 pixels (3 * 6 tiles)
; - range of motion is:
;   - [0 - 11] horizontally (12 tiles)
;   - [0 - 16] vertically (17 tiles)
; 
; - top-left of the viewport is:
;   x (RA): 84 - 2 * RA (in 15mins increments)
;   y (DE): 112 - 8 * DE (in 5 degree increments)

custom_telescopio_top_left_coordinate:
    db 0, 0
; telescopio_debug_top_left_1:
;     db 0, 0
; telescopio_debug_top_left_2:
;     db 0, 0
; telescopio_debug_ptr_3:
;     db 0, 0


GALAXIA_NAMETABLE_WIDTH: equ 14
GALAXIA_NAMETABLE_HEIGHT: equ 22

custom_telescopio_galaxia_tiles: equ general_buffer
custom_telescopio_galaxia_nametable: equ general_buffer + 32 * 7


RADIO_LINES_COLOR_MASK: equ #41
TELESCOPIO_N_CONSTELLATIONS: equ 10
; x, y, width, height, focus RA, focus DE, ptr: (x, and width are in bytes)
custom_telescopio_constellations:
    db 38, 28, 12, 20  ; casiopea
    db 5, 14  ; 1h15m, 60º (puzzle Beethoven)
    dw custom_telescopio_constellations_casiopea_zx0
    db 20, 112, 12, 48  ; orion
    db 22, 2  ; 5h30', 0º
    dw custom_telescopio_constellations_orion_zx0
    db 38, 68, 10, 32  ; triangle
    db 6, 8  ; 1h30', 30º
    dw custom_telescopio_constellations_triangle_zx0
    db 6, 100, 10, 36  ; lynx
    db 36, 4  ; 9h, 10º 
    dw custom_telescopio_constellations_lynx_zx0
    db 18, 0, 12, 48  ; camelopardis
    db 24, 16  ; 6h, 70º
    dw custom_telescopio_constellations_camelopardis_zx0
    db 30, 40, 12, 48  ; perseo
    db 12, 11  ; 3h, 45º
    dw custom_telescopio_constellations_perseo_zx0
    db 30, 92, 12, 28  ; aries
    db 12, 6  ; 3h, 20º
    dw custom_telescopio_constellations_aries_zx0
    db 22, 80, 12, 24  ; taurus
    db 20, 7  ; 5h, 25º
    dw custom_telescopio_constellations_taurus_zx0
    db 14, 84, 8, 40  ; gemini
    db 28, 6  ; 7h, 20º
    dw custom_telescopio_constellations_gemini_zx0
    db 20, 48, 8, 36  ; auriga
    db 24, 11  ; 6h, 45º
    dw custom_telescopio_constellations_auriga_zx0


;-----------------------------------------------
custom_telescopio_up_ra:
    ld a, (game_state_variable_telescope_ra)
    cp 42
    jr z, custom_telescopio_up_ra_error
    inc a
    ld (game_state_variable_telescope_ra), a
    jr custom_telescopio_redraw_numbers_after_change

custom_telescopio_up_ra_error:
    ld hl, sfx_sfx_error_afx
    jp play_SFX_with_high_priority


;-----------------------------------------------
custom_telescopio_down_ra:
    ld a, (game_state_variable_telescope_ra)
    or a
    jr z, custom_telescopio_up_ra_error
    dec a
    ld (game_state_variable_telescope_ra), a
    jr custom_telescopio_redraw_numbers_after_change


;-----------------------------------------------
custom_telescopio_up_de:
    ld a, (game_state_variable_telescope_de)
    cp 16
    jr z, custom_telescopio_up_ra_error
    inc a
    ld (game_state_variable_telescope_de), a
    jr custom_telescopio_redraw_numbers_after_change


;-----------------------------------------------
custom_telescopio_down_de:
    ld a, (game_state_variable_telescope_de)
    or a
    jr z, custom_telescopio_up_ra_error
    dec a
    ld (game_state_variable_telescope_de), a
    ; jr custom_telescopio_redraw_numbers_after_change


;-----------------------------------------------
custom_telescopio_redraw_numbers_after_change:
    ld hl, sfx_sfx_btn_afx
    call play_SFX_with_high_priority
    call clear_pointer
    call custom_telescopio_redraw  ; mdl:no-opt (to prevent MDL from optimizing this, since
                                   ;             it does now know we can call 'custom_telescopio_redraw'
                                   ;             externally).
    jp draw_pointer


;-----------------------------------------------
custom_telescopio_redraw_numbers:
    ; RA:
    ld a, (game_state_variable_telescope_ra)
    and #03
    ld c, a
    add a, a
    add a, a
    add a, a
    add a, a
    sub c  ; a *= 15
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 8*4 + 2 + #1800
    call custom_radio_draw_2_digit_number
    ld a, (game_state_variable_telescope_ra)
    rrca
    rrca
    and #3f
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 7*4 + 1 + #1800
    call custom_radio_draw_2_digit_number

    ; DE:
    ; remove the minus sign:
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 10*4 + 1 + #2800
    ld (hl), 0
    ld a, (game_state_variable_telescope_de)
    sub 2
    jr nc, custom_telescopio_redraw_numbers_de_positive
    ; DE is negative:
    ; draw a minus sign:
    ld (hl), RADIO_COLOR_WHITE_MASK + RADIO_COLOR_WHITE_MASK*2
    neg
custom_telescopio_redraw_numbers_de_positive:
    ld c, a
    add a, a
    add a, a
    add a, c  ; a *= 5
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 10*4 + 2 + #1800
    call custom_radio_draw_2_digit_number

    ; dot:
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 11*4 + 2 + #3800
    ld (hl), RADIO_COLOR_WHITE_MASK

    xor a
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*11 + 11*4 + 3 + #1800
    jp custom_radio_draw_digit


;-----------------------------------------------
custom_telescopio_redraw:
    call custom_telescopio_redraw_numbers   
    ; jp custom_telescopio_draw_stars


;-----------------------------------------------
custom_telescopio_draw_stars:
    xor a
    ld (game_state_variable_constellation_focused), a
    ; - top-left of the viewport is:
    ;   x (RA): 84 - 4 * RA (in 30mins increments)
    ;   y (DE): 112 - 8 * (DE / 5)

    ; compute the offset:
    ld a, 42
    ld hl, game_state_variable_telescope_ra  ; 0 - 42
    sub (hl)
    ld c, a  ; x in increments of 2 pixels
    ld a, 16
    ld hl, game_state_variable_telescope_de  ; 0 - 16
    sub (hl)
    ld b, a  ; y in increments of 1 tile
    push bc
        ; convert it to bytes/pixel coordinates for later:
        add a, a
        add a, a
        add a, a
        ld b, a
        ld (custom_telescopio_top_left_coordinate), bc
    pop bc

    ld a, (game_state_variable_telescopio_pentagrama)
    or a
    jp nz, custom_telescopio_draw_stars_with_pentagram

    push bc
        ; decompress the bg data:
        ld hl, custom_telescopio_bg_zx0
        ld de, general_buffer
        call dzx0_standard
    pop bc

    ; ptr to draw is thus:
    ; - we need to draw tiles starting at:
    ; - custom_telescopio_galaxia_nametable + c + b * GALAXIA_NAMETABLE_WIDTH
    ld l, b
    ld h, 0
    add hl, hl  ; * 2
    push hl
        add hl, hl  ; * 4 
        add hl, hl  ; * 8
        add hl, hl  ; * 16
    pop de
    or a
    sbc hl, de  ; hl = b * 14  (since GALAXIA_NAMETABLE_WIDTH == 14)
    push bc
        srl c
        srl c
        ld b, 0
        add hl, bc  ; hl = c + b * GALAXIA_NAMETABLE_WIDTH
        ld bc, custom_telescopio_galaxia_nametable
        add hl, bc  ; ptr to the name table to start drawing from
        push hl
        pop ix
    pop bc
    ld a, c
    and #03
    ld iyl, a  ; pixel to start from in each tile
;     jr nc, custom_telescopio_draw_stars_continue
;         ld iyl, 2  ; we need to start half-way through a tile
; custom_telescopio_draw_stars_continue:

    ; draw the background:
    ld hl, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*8 + 1*4 + 2
    ld bc, #060c  ; 6 vertical tiles, 12 horizontal bytes
custom_telescopio_draw_stars_bg_y_loop:  ; one iteration per vertical tile
    push bc
        push ix
        push hl
            ld b, c
custom_telescopio_draw_stars_bg_x_loop:
            push bc
            push hl
                ld a, (ix)  ; tile to draw
                ; get tile ptr:
                ; - ptr: custom_telescopio_galaxia_tiles + a * 32
                add a, a
                add a, a
                add a, a
                add a, a
                add a, a  ; a = a*32  (a can only go up to 6, so, it's safe to do it in 8 bit arithmetic)
                add a, iyl  ; which pixel are we drawing of this row
                ld de, custom_telescopio_galaxia_tiles
                ADD_DE_A
                ld b, 8
                push de
custom_telescopio_draw_stars_bg_y2_loop:  ; one iteration per line inside a tile
                    ld a, (de)
                    ld (hl), a
                    ; ld (hl), RADIO_COLOR_WHITE_MASK + RADIO_COLOR_WHITE_MASK*2
                    ld a, 8
                    add a, h
                    ld h, a  ; next pixel line
                    inc de
                    inc de
                    inc de
                    inc de
                    djnz custom_telescopio_draw_stars_bg_y2_loop
                pop de
                inc de
                inc iyl
                ld a, iyl
                cp 4
                jr nz, custom_telescopio_draw_stars_bg_x_loop_continue
                ; next tile:
                inc ix
                ld iyl, 0
custom_telescopio_draw_stars_bg_x_loop_continue:
            pop hl
            pop bc
            inc hl
            djnz custom_telescopio_draw_stars_bg_x_loop
        pop hl
        pop ix
        ld bc, SCREEN_WIDTH_IN_BYTES
        add hl, bc
        ld bc, GALAXIA_NAMETABLE_WIDTH
        add ix, bc
    pop bc
    djnz custom_telescopio_draw_stars_bg_y_loop

custom_telescopio_draw_stars_with_pentagram:
    ; draw the constellations (with or without lines):
    ld b, TELESCOPIO_N_CONSTELLATIONS
    ld iyh, 1  ; constellation number
    ld hl, custom_telescopio_constellations
custom_telescopio_draw_stars_c_loop:
    push bc
        ; x, y, width, height, ptr
        ld c, (hl)  ; x in bytes
        inc hl
        ld b, (hl)  ; y
        inc hl
        ; determine whether we need to draw it:
        ; horizontal check:
        ld a, (custom_telescopio_top_left_coordinate)
        add a, 12  ; viewport width in bytes
        sub c  ; a = viewport.x + 12 - constellation.x
        jp c, custom_telescopio_draw_stars_c_loop_skip_6inc  ; constellation is to the right
        ld a, c
        add a, (hl)  ; a = (constellation.x + constellation.w)
        ld e, a
        ld a, (custom_telescopio_top_left_coordinate)
        sub e
        jp nc, custom_telescopio_draw_stars_c_loop_skip_6inc  ; constellation is to the left
        inc hl
        ; vertical check:
        ld a, (custom_telescopio_top_left_coordinate + 1)
        add a, 48  ; viewport width
        sub b  ; a = viewport.y + 48 - constellation.y
        jp c, custom_telescopio_draw_stars_c_loop_skip_5inc  ; constellation is below
        ld a, b
        add a, (hl)  ; a = (constellation.y + constellation.h)
        ld d, a
        ld a, (custom_telescopio_top_left_coordinate + 1)
        sub d  ; a = viewport_y - (constellation.y + constellation.h)
        jp nc, custom_telescopio_draw_stars_c_loop_skip_5inc  ; constellation is above

        ; here:
        ; - c, b -> e, d are the constellation coordinates in the galaxy (x is in bytes)
        ; - we will turn them into relative to the viewport by subtracting (custom_telescopio_top_left_coordinate):
        push hl
            ld hl, custom_telescopio_top_left_coordinate
            ld a, c
            sub (hl)
            ld c, a
            ld a, e
            sub (hl)
            ld e, a
            inc hl
            ld a, b
            sub (hl)
            ld b, a
            ld a, d
            sub (hl)
            ld d, a
        pop hl
        ; - c, b -> e, d are the constellation coordinates in the viewport (x is in bytes)

        ; Do we need to draw the lines?
custom_telescopio_draw_stars_c_loop_focus_variable_check:
        ld iyl, 0  ; do not draw the lines
        push de
            inc hl
            ld e, (hl)  ; RA
            inc hl
            ld a, (game_state_variable_telescope_ra)
            cp e
            jr nz, custom_telescopio_draw_stars_c_loop_no_lines
            ld e, (hl)  ; DE
            ld a, (game_state_variable_telescope_de)
            cp e
            jr nz, custom_telescopio_draw_stars_c_loop_no_lines
            ld iyl, 1  ; draw the lines
            ; mark a constellation as focused:
            ld a, iyh
            ld (game_state_variable_constellation_focused), a
custom_telescopio_draw_stars_c_loop_no_lines:
        pop de

        ; ld (telescopio_debug_top_left_1), bc
        ; ld (telescopio_debug_top_left_2), de
        ; We need to draw the whole or part of it, decompress it:
        push hl
            push de
                push bc
                    inc hl
                    ld e, (hl)
                    inc hl
                    ld h, (hl)
                    ld l, e
                    ld de, general_buffer
                    call dzx0_standard
                pop bc

                ; draw the part that is necessary:
                ; compute the ptr to the viewport coordinate c, b:
                ; - ptr = VIDEO_MEMORY + 1*4 + 2 + SCREEN_WIDTH_IN_BYTES*(8 + b/8) + c + (b%8)*#0800
                ld a, b
                and #07  ; a = b%8
                add a, a
                add a, a
                add a, a
                ld h, a
                ld l, 0  ; hl = (b%8)*#0800
                bit 7, c
                jr nz, custom_telescopio_draw_stars_c_loop_ptr_c_neg
                ld e, c
                ld d, 0
                add hl, de  ; hl = c + (b%8)*#0800
                jr custom_telescopio_draw_stars_c_loop_ptr_c_handled
custom_telescopio_draw_stars_c_loop_ptr_c_neg:
                ld a, c
                neg
                ld e, a
                ld d, 0
                or a
                sbc hl, de  ; hl = c + (b%8)*#0800
custom_telescopio_draw_stars_c_loop_ptr_c_handled:
                ld a, b
                or a
                jp m, custom_telescopio_draw_stars_c_loop_ptr_b_neg
                rrca
                rrca
                rrca
                and #1f  ; a = b/8
                ld de, SCREEN_WIDTH_IN_BYTES
                jr custom_telescopio_draw_stars_c_loop_ptr_loop
custom_telescopio_draw_stars_c_loop_ptr_b_neg:
                neg
                add a, 7  ; to round properly after negating
                rrca
                rrca
                rrca
                and #1f  ; a = -b/8
                ld de, -SCREEN_WIDTH_IN_BYTES
custom_telescopio_draw_stars_c_loop_ptr_loop:
                jr z, custom_telescopio_draw_stars_c_loop_ptr_loop_done
                add hl, de
                dec a
                jr nz, custom_telescopio_draw_stars_c_loop_ptr_loop
custom_telescopio_draw_stars_c_loop_ptr_loop_done:
                ld de, VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*8 + 1*4 + 2
                add hl, de  ; hl = VIDEO_MEMORY + 1*4 + 2 + SCREEN_WIDTH_IN_BYTES*(8 + b/8) + c + (b%8)*#0800
                ; ld (telescopio_debug_ptr_3), hl
            pop de
            ld ix, general_buffer
            ld a, b
            or a
            jp p, custom_telescopio_draw_stars_c_no_top_crop
            push bc
                neg
                push af
                    ld a, e
                    sub c  ; a = width in bytes of the constelation we are drawing
                    ld c, a
                    ld b, 0
                pop af
custom_telescopio_draw_stars_c_top_crop_loop:
                add ix, bc
                dec a
                jr nz, custom_telescopio_draw_stars_c_top_crop_loop
            pop bc
custom_telescopio_draw_stars_c_no_top_crop:

custom_telescopio_draw_stars_c_loop_y_loop:
            ld a, b
            or a
            jp m, custom_telescopio_draw_stars_c_loop_y_loop_next
            cp 48
            jr nc, custom_telescopio_draw_stars_c_loop_y_loop_done

            push bc
            push hl
custom_telescopio_draw_stars_c_loop_x_loop:
                ld a, c
                or a
                jp m, custom_telescopio_draw_stars_c_loop_x_loop_next
                cp 12
                jr nc, custom_telescopio_draw_stars_c_loop_x_loop_next
                ; jr nc, custom_telescopio_draw_stars_c_loop_x_loop_done

                ; draw this pixel:
                DRAW_CONSTELATION_PIXEL_MACRO

custom_telescopio_draw_stars_c_loop_x_loop_next:
                inc hl
                inc ix
                inc c
                ld a, c
                cp e
                jr nz, custom_telescopio_draw_stars_c_loop_x_loop
custom_telescopio_draw_stars_c_loop_x_loop_done:
            pop hl
            pop bc
custom_telescopio_draw_stars_c_loop_y_loop_next:
            push de
                ld de, #0800
                add hl, de  ; next line
                ; wrap around when we move from the 8th pixel line back to the 0th:
                ld a, h
                cp #c0
                jr nc, custom_telescopio_draw_stars_c_loop_y_loop_next_continue
                add a, #c0
                ld h, a
                ld de, SCREEN_WIDTH_IN_BYTES
                add hl, de
custom_telescopio_draw_stars_c_loop_y_loop_next_continue:
            pop de
            inc b
            ld a, b
            cp d
            jr nz, custom_telescopio_draw_stars_c_loop_y_loop
custom_telescopio_draw_stars_c_loop_y_loop_done:
        pop hl
        jr custom_telescopio_draw_stars_c_loop_skip_3inc

custom_telescopio_draw_stars_c_loop_skip_6inc:
        inc hl
custom_telescopio_draw_stars_c_loop_skip_5inc:
        inc hl
custom_telescopio_draw_stars_c_loop_skip_4inc:
        inc hl
custom_telescopio_draw_stars_c_loop_skip_3inc:
        inc hl
custom_telescopio_draw_stars_c_loop_skip_2inc:
        inc hl
        inc hl
    pop bc
    inc iyh
    dec b
    jp nz, custom_telescopio_draw_stars_c_loop

    ld de, (pointer_background_buffer_video_mem_ptr)
    jp save_pointer_background


;-----------------------------------------------
DRAW_CONSTELATION_PIXEL_MACRO: macro
    ld a, (ix)
    or a
    jr z, DRAW_CONSTELATION_PIXEL_MACRO_done
    ex af, af'
        ld a, iyl
        or a
        jr nz, custom_telescopio_draw_stars_c_loop_x_loop_draw_lines
        ; do not draw the lines:
    ex af, af'
    exx
        ld c, a
        and #55
        cp RADIO_LINES_COLOR_MASK
        jr nz, custom_telescopio_draw_stars_c_loop_x_loop_remove_lines_continue1
        ld a, c
        and #aa
        ld c, a
custom_telescopio_draw_stars_c_loop_x_loop_remove_lines_continue1:
        ld a, c
        and #aa
        cp RADIO_LINES_COLOR_MASK*2
        jr nz, custom_telescopio_draw_stars_c_loop_x_loop_remove_lines_continue2
        ld a, c
        and #55
        ld c, a
custom_telescopio_draw_stars_c_loop_x_loop_remove_lines_continue2:
        ld a, c
    exx
    ex af, af'
custom_telescopio_draw_stars_c_loop_x_loop_draw_lines:
    ex af, af'
    or a
    jr z, DRAW_CONSTELATION_PIXEL_MACRO_done

    ; transparency:
    ld b, a
    and #55  ; check if the first pixel is transparent
    ld a, b
    jr nz, draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent
    ; a = (hl) & #55 + a & #aa
    and #aa
    ld b, a
    ld a, (hl)
    and #55
    add a, b
    ; if the first pixel is transparent, the second cannot be, so, just draw it:
    ld (hl), a
    jr DRAW_CONSTELATION_PIXEL_MACRO_done
    
draw_room_dirty_columns_objects_state_column_found_first_pixel_not_transparent:
    and #aa  ; check if the second pixel is transparent:
    ld a, b  ; b still contains the original pixel
    jr nz, draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent
    ; a = (hl) & #aa + a & #55
    and #55
    ld b, a
    ld a, (hl)
    and #aa
    add a, b
draw_room_dirty_columns_objects_state_column_found_second_pixel_not_transparent:
    ld (hl), a
DRAW_CONSTELATION_PIXEL_MACRO_done:
    endm






