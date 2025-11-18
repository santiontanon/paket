;-----------------------------------------------
PKTD_HISTORY_SIZE: equ 12

pktd_history: ds PKTD_HISTORY_SIZE, 0

pktd_history_position:
    db 0


pktd_solution_escaleras:  ; verde, violeta, naranja, rojo
    db 10, 18, 20, 11  ; length 4

pktd_solution_tv_0:  ; -.-..-.----- (shows you couch, you get ATON, KA)
    db 21, 19, 21, 19, 19, 21, 19, 21, 21, 21, 21, 21  ; length 12

pktd_solution_tv_1:  ; .------. (shows caballo, you get ANKH, HAPY)
    db 19, 21, 21, 21, 21, 21, 21, 19  ; length 8

pktd_solution_tv_2:  ; .--.-.-.... (shows ficus, you get SAH, ANDYETY)
    db 19, 21, 21, 19, 21, 19, 21, 19, 19, 19, 19  ; length 11

pktd_solution_tv_3:  ; ....-....  (shows cubos, you get BA, AJ)
    db 19, 19, 19, 19, 21, 19, 19, 19, 19  ; length 9

pktd_solution_espejo:  ; 186021
    db 10, 17, 15, 20, 11, 10  ; length 4


;-----------------------------------------------
custom_pktd_init:
    ld hl, pktd_history
    ld bc, PKTD_HISTORY_SIZE + 1  ; to clear the position too
    jp clear_memory


;-----------------------------------------------
custom_pktd_button_press:
    ld hl, sfx_sfx_btn_afx
    call play_SFX_with_high_priority

    ld hl, pktd_history
    ld a, (pktd_history_position)
    cp PKTD_HISTORY_SIZE
    jr z, custom_pktd_button_press_continue
    push af
        ADD_HL_A
        ld a, (iy + OBJECT_STRUCT_ID)
        ld (hl), a
    pop af
    inc a
    ld (pktd_history_position), a
custom_pktd_button_press_continue:
    ; check solution
    ld a, (game_state_variable_pktd_location)
    or a
    jr z, custom_pktd_button_press_escaleras
    dec a
    jr z, custom_pktd_button_press_tv

custom_pktd_button_press_espejo:
    ld ix, game_state_variable_espejo_solved
    ld iyl, 1
    ld hl, pktd_history
    ld de, pktd_solution_espejo
    ld b, 6
    jr custom_pktd_button_press_solution_loop


custom_pktd_button_press_escaleras:
    ld ix, game_state_variable_escaleras_pktd_solved
    ld iyl, 1
    ld hl, pktd_history
    ld de, pktd_solution_escaleras
    ld b, 4

    ; here:
    ; - ix: ptr to the variable to change if solved
    ; - iyl: value to set to that variable
custom_pktd_button_press_solution_loop:
    ld a, (de)
    cp (hl)
    ret nz  ; fail
    inc de
    inc hl
    djnz custom_pktd_button_press_solution_loop
    ; solved!
    ld a, iyl
    ld (ix), a

    ld hl, sfx_sfx_puzzle_resuelto_afx
    call play_SFX_with_high_priority

    ; call exit-pktd script:
    ld hl, exit_pktd
    jp executeRuleScript_internal


custom_pktd_button_press_tv:
    ld a, (game_state_variable_tv_puzzle)
    or a
    jr z, custom_pktd_button_press_tv_0
    cp 2
    jr z, custom_pktd_button_press_tv_1
    cp 4
    jr z, custom_pktd_button_press_tv_2
    cp 6
    jr z, custom_pktd_button_press_tv_3
    ret

custom_pktd_button_press_tv_0:
    ld ix, game_state_variable_tv_puzzle
    ld iyl, 1
    ld hl, pktd_history
    ld de, pktd_solution_tv_0
    ld b, 12
    jr custom_pktd_button_press_solution_loop

custom_pktd_button_press_tv_1:
    ld ix, game_state_variable_tv_puzzle
    ld iyl, 3
    ld hl, pktd_history
    ld de, pktd_solution_tv_1
    ld b, 8
    jr custom_pktd_button_press_solution_loop

custom_pktd_button_press_tv_2:
    ld ix, game_state_variable_tv_puzzle
    ld iyl, 5
    ld hl, pktd_history
    ld de, pktd_solution_tv_2
    ld b, 11
    jr custom_pktd_button_press_solution_loop

custom_pktd_button_press_tv_3:
    ld ix, game_state_variable_tv_puzzle
    ld iyl, 7
    ld hl, pktd_history
    ld de, pktd_solution_tv_3
    ld b, 9
    jr custom_pktd_button_press_solution_loop

