;-----------------------------------------------
; Loads the interrupt hook for playing music:
setup_custom_interrupt:
    ld a, JPCODE  ; NEW HOOK SET
    di
        ld (TIMI), a
        ld hl, interrupt_callback
        ld (TIMI + 1), hl
    ei
    ret


; ------------------------------------------------
; My interrupt handler:
interrupt_callback:
    push af
    push bc
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
IF MUSIC_TYPE_WYZ == 1
        ; play SFX:
        ld a, (SFX_player_active)
        or a
        call nz,play_ayfx
ENDIF
        ld a, (MUSIC_muted)
        or a
        jr nz, interrupt_callback_music_muted
        call sound_update
IF MUSIC_TYPE_WYZ == 1
        jr interrupt_callback_music_done
interrupt_callback_music_muted:
        call sound_update_music_muted
ELSE:
interrupt_callback_music_muted:
ENDIF
interrupt_callback_music_done:
    pop af
    pop bc
    ei
    ret
