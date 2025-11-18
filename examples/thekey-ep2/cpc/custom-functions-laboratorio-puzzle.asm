;-----------------------------------------------
; plata: 1
; mercurio: 2, 3, 4
; oro: 5, 6, 7, 8, 9
laboratorio_puzzle_state_particles:
    db 0, 0, 0, 0, 0, 0, 0, 0, 0  ; which particles have you generated
laboratorio_puzzle_state_tablero_particles:
    db 0, 0, 0, 0, 0, 0, 0, 0, 0  ; which particles have you placed
laboratorio_puzzle_state_n_m_particles:
    db 0
laboratorio_puzzle_state_n_p_particles:
    db 0
laboratorio_puzzle_state_n_o_particles:
    db 0
laboratorio_puzzle_state_selected_particle:
    db 0

laboratorio_puzzle_state_tablero_particles_solution_1:
    db 5, 1, 4
    db 3, 6, 8
    db 2, 7, 9

laboratorio_puzzle_state_tablero_particles_solution_2:
    db 2, 1, 8
    db 5, 9, 3
    db 6, 7, 4

laboratorio_puzzle_state_tablero_particles_solution_3:
    db 5, 7, 4
    db 6, 1, 8
    db 2, 9, 3

laboratorio_puzzle_state_tablero_particles_solution_4:
    db 2, 7, 8
    db 5, 1, 9
    db 3, 6, 4


;-----------------------------------------------
laboratorio_puzzle_reset_button:
    call laboratorio_puzzle_reset_clicked_particle_if_any
laboratorio_puzzle_reset:
    xor a
    ld (game_state_variable_laboratorio_puzzle_bottle), a
    ld (game_state_variable_laboratorio_puzzle_n_particles), a
    ld hl, laboratorio_puzzle_state_particles
    ld bc, 9 + 9 + 4
    call clear_memory

    ; reset bar particle object states and descriptions:
laboratorio_puzzle_reset_bar_and_tablero:
    ld b, 0
laboratorio_puzzle_reset_loop1:
    push bc
        xor a
        call laboratorio_puzzle_change_particle_object_state
    pop bc
    inc b
    ld a, b
    cp 9 + 9
    jr nz, laboratorio_puzzle_reset_loop1
    ret


;-----------------------------------------------
laboratorio_puzzle_reset_clicked_particle_if_any:
    ld a, (laboratorio_puzzle_state_selected_particle)
    or a
    ret z
    call find_room_object_ptr_by_id    
    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y2)
    dec (ix + OBJECT_STRUCT_Y2)
    xor a
    ld (laboratorio_puzzle_state_selected_particle), a
    ret


;-----------------------------------------------
; - assumes "iy" points to the object we are interacting with
laboratorio_puzzle_add_particle:
    call laboratorio_puzzle_reset_clicked_particle_if_any
    call laboratorio_puzzle_make_tablero_non_clickable
laboratorio_puzzle_add_particle_no_previous_selected:
    ld a, (game_state_variable_laboratorio_puzzle_n_particles)
    ld b, a
    ld hl, laboratorio_puzzle_state_particles
    ADD_HL_A
    ex de, hl
    ld a, (game_state_variable_laboratorio_puzzle_bottle)
    dec a
    jr z, laboratorio_puzzle_add_particle_e
    dec a
    jr z, laboratorio_puzzle_add_particle_p
laboratorio_puzzle_add_particle_o:
    ld hl, laboratorio_puzzle_state_n_o_particles
    inc (hl)
    ld a, (hl)
    add a, 4
laboratorio_puzzle_add_particle_o_loop:
    cp 10
    jr c, laboratorio_puzzle_add_particle_generic
    sub a, 5
    jr laboratorio_puzzle_add_particle_o_loop
laboratorio_puzzle_add_particle_e:
    ld hl, laboratorio_puzzle_state_n_m_particles
    inc (hl)
    ld a, (hl)
    inc a
laboratorio_puzzle_add_particle_e_loop:
    cp 5
    jr c, laboratorio_puzzle_add_particle_generic
    sub a, 3
    jr laboratorio_puzzle_add_particle_e_loop
laboratorio_puzzle_add_particle_p:
    ld a, 1  ; plata particles are '1'
    ; jr laboratorio_puzzle_add_particle_generic

; a: particle type
; b: n particle
; de: ptr to laboratorio_puzzle_state_particles
laboratorio_puzzle_add_particle_generic:
    ld (de), a
    ld hl, game_state_variable_laboratorio_puzzle_n_particles
    inc (hl)
laboratorio_puzzle_change_particle_object_state:
    push af
        ld a, b
        add a, 7  ; the first particle object Id is 7
        call find_room_object_ptr_by_id
    pop af
    or a
    jr z, laboratorio_puzzle_change_particle_object_state_reset
    add a, a
    add a, a
    add a, a
    add a, a
    add a, OBJECT_STATE_NUMBER
    jr laboratorio_puzzle_change_particle_object_state_continue
laboratorio_puzzle_change_particle_object_state_reset:
    ld a, OBJECT_STATE_IDLE + OBJECT_DIRECTION_DOWN * 16
laboratorio_puzzle_change_particle_object_state_continue:
    ld (ix + OBJECT_STRUCT_STATE_DIRECTION), a
    xor a
    ld (ix + OBJECT_STRUCT_ANIMATION_STEP), a
    ld (ix + OBJECT_STRUCT_ANIMATION_TIMER), a
    ld a, 1
    ld (redraw_whole_room_signal),a
    ret


;-----------------------------------------------
laboratorio_puzzle_select_particle:
    call laboratorio_puzzle_reset_clicked_particle_if_any
    ld a, (iy + OBJECT_STRUCT_ID)
    ld (laboratorio_puzzle_state_selected_particle), a
    inc (iy + OBJECT_STRUCT_Y)
    inc (iy + OBJECT_STRUCT_Y)
    inc (iy + OBJECT_STRUCT_Y2)
    inc (iy + OBJECT_STRUCT_Y2)
    ld a, 1
    ld (redraw_whole_room_signal),a
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority
    ; jp laboratorio_puzzle_make_tablero_clickable


;-----------------------------------------------
laboratorio_puzzle_make_tablero_clickable:
    ld b, 9
    ld de, OBJECT_STATE_IDLE + OBJECT_DIRECTION_DOWN * 16 + (OBJECT_STATE_OPEN + OBJECT_DIRECTION_DOWN * 16) * 256
    jp laboratorio_puzzle_make_tablero_non_clickable_loop


;-----------------------------------------------
; - assumes "iy" points to the object we are interacting with
laboratorio_puzzle_place_particle:
    ld a, (iy + OBJECT_STRUCT_STATE_DIRECTION)
    cp (OBJECT_STATE_OPEN + OBJECT_DIRECTION_DOWN * 16)
    ret nz
    ld a, (laboratorio_puzzle_state_selected_particle)
    ld hl, laboratorio_puzzle_state_particles - 7
    ADD_HL_A
    ld c, (hl)  ; particle type to set
    ld a, (iy + OBJECT_STRUCT_ID)
    ld hl, laboratorio_puzzle_state_tablero_particles - 16
    ADD_HL_A
    ld (hl), c
    ld a, c
    add a, a
    add a, a
    add a, a
    add a, a
    add a, OBJECT_STATE_NUMBER
    ld (iy + OBJECT_STRUCT_STATE_DIRECTION), a
    xor a
    ld (iy + OBJECT_STRUCT_ANIMATION_STEP), a
    ld (iy + OBJECT_STRUCT_ANIMATION_TIMER), a

    ; remove from bar: (1) set state to idle, (2) reset position, (3) reset laboratorio_puzzle_state_selected_particle
    ld a, (laboratorio_puzzle_state_selected_particle)
    sub a, 7
    ld b, a
    xor a
    call laboratorio_puzzle_change_particle_object_state
    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y)
    dec (ix + OBJECT_STRUCT_Y2)
    dec (ix + OBJECT_STRUCT_Y2)
    xor a
    ld (laboratorio_puzzle_state_selected_particle), a
    ld hl, sfx_sfx_button_press_afx
    call play_SFX_with_high_priority
    call laboratorio_puzzle_make_tablero_non_clickable

    ; check for final solution:
    ld b, 9
    ld de, laboratorio_puzzle_state_tablero_particles
    ld hl, laboratorio_puzzle_state_tablero_particles_solution_1
    call laboratorio_puzzle_place_particle_generic_check_solution_loop
    jr z, laboratorio_puzzle_place_particle_generic_check_solution_match
    ld b, 9
    ld de, laboratorio_puzzle_state_tablero_particles
    ld hl, laboratorio_puzzle_state_tablero_particles_solution_2
    call laboratorio_puzzle_place_particle_generic_check_solution_loop
    jr z, laboratorio_puzzle_place_particle_generic_check_solution_match
    ld b, 9
    ld de, laboratorio_puzzle_state_tablero_particles
    ld hl, laboratorio_puzzle_state_tablero_particles_solution_3
    call laboratorio_puzzle_place_particle_generic_check_solution_loop
    jr z, laboratorio_puzzle_place_particle_generic_check_solution_match
    ld b, 9
    ld de, laboratorio_puzzle_state_tablero_particles
    ld hl, laboratorio_puzzle_state_tablero_particles_solution_4
    call laboratorio_puzzle_place_particle_generic_check_solution_loop
    ret nz
laboratorio_puzzle_place_particle_generic_check_solution_match:
    ld a, 1
    ld (game_state_variable_laboratorio_puzzle_solved), a
    ret

laboratorio_puzzle_place_particle_generic_check_solution_loop:
    ld a, (de)
    cp (hl)
    ret nz
    inc de
    inc hl
    djnz laboratorio_puzzle_place_particle_generic_check_solution_loop
    xor a
    ret



;-----------------------------------------------
laboratorio_puzzle_make_tablero_non_clickable:
    ld b, 9
    ld de, OBJECT_STATE_OPEN + OBJECT_DIRECTION_DOWN * 16 + (OBJECT_STATE_IDLE + OBJECT_DIRECTION_DOWN * 16) * 256
laboratorio_puzzle_make_tablero_non_clickable_loop:
    push bc
        ld a, b
        add a, 15  ; 16 - 1
        push de
            call find_room_object_ptr_by_id
        pop de
        ld a, (ix + OBJECT_STRUCT_STATE_DIRECTION)
        cp e
        jr nz, laboratorio_puzzle_make_tablero_non_clickable_loop_skip
        ld (ix + OBJECT_STRUCT_STATE_DIRECTION), d
        xor a
        ld (ix + OBJECT_STRUCT_ANIMATION_STEP), a
        ld (ix + OBJECT_STRUCT_ANIMATION_TIMER), a
laboratorio_puzzle_make_tablero_non_clickable_loop_skip:
    pop bc
    djnz laboratorio_puzzle_make_tablero_non_clickable_loop
    ld a, 1
    ld (redraw_whole_room_signal),a
    ret


;-----------------------------------------------
laboratorio_puzzle_reset_tablero:
    call laboratorio_puzzle_reset_clicked_particle_if_any
    ld hl, laboratorio_puzzle_state_tablero_particles
    ld bc, 9
    call clear_memory
    call laboratorio_puzzle_reset_bar_and_tablero
    ; re-add all the elements:
    ld b, 9
    ld hl, laboratorio_puzzle_state_particles
laboratorio_puzzle_reset_tablero_loop:
    push bc
        ; b = 9 - b
        ld a, 9
        sub b
        ld b, a
        ld a, (hl)
        or a
        jr z, laboratorio_puzzle_reset_tablero_loop_skip
        push hl
            call laboratorio_puzzle_change_particle_object_state
        pop hl
        inc hl
laboratorio_puzzle_reset_tablero_loop_skip:
    pop bc
    djnz laboratorio_puzzle_reset_tablero_loop
    ld hl, sfx_sfx_button_press_afx
    jp play_SFX_with_high_priority

