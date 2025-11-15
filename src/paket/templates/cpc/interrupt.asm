; ------------------------------------------------
; My interrupt handler to replace ISR 7 for screen split between mode 1 and mode 0
interrupt_callback_split_mode1_mode0:
    push bc
    push af
        ld bc, interrupt_count
        ld a, (bc)
        inc a
        ld (bc), a

        cp 1
        jr z, interrupt_callback_delay_interrupt

        cp SCREEN_MODE0_INTERRUPT_COUNT
        jr nz, interrupt_callback_continue

interrupt_callback_mode0_area:
        ld bc,#7f00   ; Gate array port
        ld a, #8c      ; Mode and ROM selection + set mode 0
        out (c),a     ; Send it

        ; call set_white_border

        push hl
        push iy
            ld hl, (current_palette_ptr)
            call set_palette_4_colors_without_disabling_interrupts
        pop iy
        pop hl

interrupt_callback_continue:
        ; Check if we are in vsync:
        ld b, #f5
        in a, (c)
        rra     
        jr nc, interrupt_callback_not_vsync
    
        ; we are in a vsync, do whatever we need to do once per game frame:
        ld bc, vsyncs_since_last_frame
        ld a, (bc)
        cp 4
        jp p, interrupt_callback_do_not_increment_vsyncs
        inc a
        ld (bc), a
interrupt_callback_do_not_increment_vsyncs:
        inc bc
        ld a, (bc)
        inc a
        ld (bc), a   ; increment "game_cycle"
        xor a
        ld (interrupt_count), a
IF IS_6128 == 1
        ld a, (current_4000_page)
        push af
            ld a, (MUSIC_muted)
            or a
            call z, sound_update
IF SCRIPT_SAVE_GAME_USED + SCRIPT_LOAD_GAME_USED != 0
            push hl
            push de
                call paketdsk_interrupt_execute_tickers
            pop de
            pop hl
ENDIF
        pop af
        SET_128K_PAGE_4000_A
ELSE
IF MUSIC_TYPE_WYZ == 1
        ; play SFX:
        ld a, (SFX_player_active)
        or a
        jr z, interrupt_no_sfx
        push de
        push hl
            call play_ayfx
        pop hl
        pop de
interrupt_no_sfx:
ENDIF
        ld a, (MUSIC_muted)
        or a
        jr nz, interrupt_music_muted
IF MUSIC_TYPE_WYZ == 1
        push de
        push hl
        push ix
        push iy
            call sound_update
        pop iy
        pop ix
        pop hl
        pop de
        jr interrupt_callback_not_vsync
interrupt_music_muted:
        push de
        push hl
            call sound_update_music_muted
        pop hl
        pop de
ELSE
        call sound_update
interrupt_music_muted:
ENDIF
ENDIF
interrupt_callback_not_vsync:
    pop af
    pop bc
    ei
    ret


interrupt_callback_delay_interrupt:
    push hl
    push iy
        ld hl, text_area_palette
        call set_palette_4_colors_without_disabling_interrupts
    pop iy
    pop hl

    ; insert some delay to lower the interrupt as much as we can:
    ld b, 48
interrupt_callback_delay_interrupt_loop:
    djnz interrupt_callback_delay_interrupt_loop

    ld bc, #7f00   ; Gate array port
    ld a, #9d      ; Mode and ROM selection + set mode 1 and delay the next interrupt
    out (c), a     ; Send it

    ; call set_grey_border

    jr interrupt_callback_not_vsync


; ------------------------------------------------
; My interrupt handler to replace ISR 7 for mode 0 or 1 screen
interrupt_callback_mode0:
interrupt_callback_mode1:
    push bc
    push af
    ld bc, interrupt_count
    ld a, (bc)
    inc a
    ld (bc), a
    jr interrupt_callback_continue


; ------------------------------------------------
; set_white_border:
;     ld bc, #7f00   ; Gate Array port
;     ld a, #10      ; select border
;     out (c), a    
;     ld a, #4b      ; select color (white)
;     out (c), a    
;     ret


; ------------------------------------------------
set_grey_border:
;     push af
;         ld bc, #7f00   ; Gate Array port
;         ld a, #10      ; select border
;         out (c), a    
;         ld a, #40      ; select color (grey)
;         out (c), a   
;     pop af
;     ret