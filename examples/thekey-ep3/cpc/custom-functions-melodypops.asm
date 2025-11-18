melody_pops_history:
    db 0, 0, 0, 0, 0, 0

melodypops_procedural_sfx:
    db #e6
melodypops_procedural_sfx_insertion_point:
    db #ef, #00  ; frequency: C
    db #00  ; noise frequency
    db #88, #88, #8a, #8a
    db #8b, #8b, #8b, #8a
    db #8a, #8a, #88, #88
    db #88, #86, #86, #86
    db #84, #84, #82, #82
    db #d0, #20

melodypops_note2:
    ld a, #50  ; G
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 2
    jr melodypops_note_continue

melodypops_note3:
    ld a, #59  ; F
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 3
    jr melodypops_note_continue

melodypops_note4:
    ld a, #5f  ; E
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 4
    jr melodypops_note_continue

melodypops_note5:
    ld a, #6a  ; D
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 5
    jr melodypops_note_continue

melodypops_note6:
    ld a, #77  ; C
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 6
    jr melodypops_note_continue


;-----------------------------------------------
melodypops_note1:
    ld a, #47  ; A
    ld (melodypops_procedural_sfx_insertion_point), a
    ld a, 1
melodypops_note_continue:
    push af
        ; redraw room (only room, not hud):
        call draw_room
    pop af
    ; move all the notes down 1 position:
    ld hl, melody_pops_history + 1
    ld de, melody_pops_history
    ld bc, 5
    ldir
    ; add the new note:
    ld (melody_pops_history + 5), a

    ld hl, melodypops_procedural_sfx
    call play_SFX_with_high_priority

    ; check solution: 4, 6, 3, 2, 3, 4
    ; in fact, the solution is reversed (as notes start at the bottom in the clue): 3, 1, 4, 5, 4, 3
    ld hl, melody_pops_history
    ld a, 3
    cp (hl)
    ret nz
    inc hl
    ld a, 1
    cp (hl)
    ret nz
    inc hl
    ld a, 4
    cp (hl)
    ret nz
    inc hl
    ld a, 5
    cp (hl)
    ret nz
    inc hl
    ld a, 4
    cp (hl)
    ret nz
    inc hl
    ld a, 3
    cp (hl)
    ret nz
    ; puzzle solved!
    ld a, 1
    ld (game_state_variable_melody_pops_solved), a
    ret

