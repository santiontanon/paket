custom_piano_history:
    db 0, 0, 0, 0, 0


custom_piano_procedural_sfx:
    db #e6
custom_piano_procedural_sfx_insertion_point:
    db #ef, #00  ; frequency: C
    db #00  ; noise frequency
    db #8f, #8e, #8d, #8c
    db #8c, #8a, #8a, #8a
    db #8a, #8a, #88, #88
    db #88, #88, #86, #86
    db #86, #86, #84, #84
    db #84, #84, #84, #84
    db #d0, #20

custom_piano_notes:
    db #ef  ; C4
    db #d5  ; D4
    db #be  ; E4
    db #b3  ; F4
    db #9f  ; G4
    db #8e  ; A4
    db #7f  ; B4
    db #77  ; C5
    db #6a  ; D5
    db #5f  ; E5
    db #e1  ; C#4
    db #c9  ; D#4
    db #a9  ; F#4
    db #96  ; G#4
    db #86  ; A#4
    db #71  ; C#5
    db #64  ; D#5


;-----------------------------------------------
custom_piano_press_key:
    ld a, (iy + OBJECT_STRUCT_ID)
    ; move all the notes down 1 position:
    ld hl, custom_piano_history + 1
    ld de, custom_piano_history
    ld bc, 4
    ldir
    ; add the new note:
    ld (custom_piano_history + 4), a

    ld hl, custom_piano_notes - 10
    ADD_HL_A
    ld a, (hl)
    ld (custom_piano_procedural_sfx_insertion_point), a

    ld hl, custom_piano_procedural_sfx
    call play_SFX_with_high_priority

    ; check solution:
    ; - RE4, SOL3, SI3, RE3, SOL3
    ; - 18, 14, 16, 11, 14
    ld hl, custom_piano_history
    ld a, 18
    cp (hl)
    ret nz
    inc hl
    ld a, 14
    cp (hl)
    ret nz
    inc hl
    ld a, 16
    cp (hl)
    ret nz
    inc hl
    ld a, 11
    cp (hl)
    ret nz
    inc hl
    ld a, 14
    cp (hl)
    ret nz
    ; puzzle solved!
    ld a, 1
    ld (game_state_variable_piano_solved), a
    ret
