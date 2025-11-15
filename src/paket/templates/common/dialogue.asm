;-----------------------------------------------
; Clears all the text and initializes the scroll
clear_text_area_for_scroll:
    xor a
    ld (text_scrolling_next_row), a
    ; ld (scrolled_at_least_once), a
    call clear_command_preview_text_whole_line
    jp clear_text_area


;-----------------------------------------------
; starts a dialogue with a game object/character.
; input:
; - hl: dialogue
handle_dialogue:
    push hl
        ; remove the pointer from the screen, and initialize the text area for dialogue:
        call clear_pointer
        call clear_text_area_for_scroll
        ; set 3rd color to purple for dialogue:
        ; ld hl, text_area_palette + 2
        ; ld (hl), 88
        ; ld a, 1
        ; ld (scrolled_at_least_once), a    ; we mark this, so that in the first state we do not scroll up
    pop hl
    ld d, h
    ld e, l
    ; Here:
    ; - "de" has the pointer to the whole dialogue
    ; - "hl" has the pointer to the current state
handle_dialogue_init_state:
    push de
        ; init the dialogue variables:
        ld de, dialogue_goto_state
        xor a
        ld b, 2 + MAX_DIALOGUE_OPTIONS
handle_dialogue_init_state_loop:
        ld (de), a
        inc de
        djnz handle_dialogue_init_state_loop

        ; execute state scripts
        inc hl  ; skip state ID
        inc hl  ; skip state size
        call executeRuleScript_internal

        ; If a "goto-dialogue-state" script has been executed, then go to it directly
        ld a, (dialogue_goto_state)
        or a
        jr nz, handle_dialogue_goto_state

        ; if we have not scrolled at least once (and we are not on the start state), scroll up:
        ; ld a, (scrolled_at_least_once)
        ; or a
        ; jr nz, handle_dialogue_init_state_loop_already_scrolled
        ; call scroll_text_area_up
; handle_dialogue_init_state_loop_already_scrolled:

        ; Otherwise, if there are options to choose from, start the loop to choose from them:
        ; highlight the current option:
        ld c, 0  ; <-- currently selected option
        call change_current_dialogue_option_color
        ld c, 0  ; <-- currently selected option (again, since previous call would have cleared it)
        ; "c" contains the current option throughout this loop:
handle_dialogue_choice_loop:
        halt
        push bc
            call game_update_while_in_scripts
        pop bc

        ld a, (keyboard_line_clicks + PLATFORM_KEYBOARD_LINE_UP)
        bit PLATFORM_KEYBOARD_BIT_UP, a
        jr nz, handle_dialogue_choice_up
IF PLATFORM_KEYBOARD_LINE_DOWN != PLATFORM_KEYBOARD_LINE_UP
        ld a, (keyboard_line_clicks + PLATFORM_KEYBOARD_LINE_DOWN)
ENDIF
        bit PLATFORM_KEYBOARD_BIT_DOWN, a
        jr nz, handle_dialogue_choice_down
IF PLATFORM_KEYBOARD_LINE_SPACE != PLATFORM_KEYBOARD_LINE_DOWN
        ld a, (keyboard_line_clicks + PLATFORM_KEYBOARD_LINE_SPACE)
ENDIF
        bit PLATFORM_KEYBOARD_BIT_SPACE, a
        jr nz, handle_dialogue_choice_space
        jr handle_dialogue_choice_loop
handle_dialogue_choice_space:
    
        ; clear text, and draw selected option at the top:
        push bc
            call clear_text_area_for_scroll
            ld de, current_action_text_buffer
        pop bc
        ld b, 0
        ld hl, dialogue_option_extended_text_ids
        add hl, bc
        add hl, bc
        push bc
            ld c, (hl)
            inc hl
            ld a, (hl)        
            call get_text_from_bank
            ld hl, current_action_text_buffer
            ld iyl, TEXT_COLOR_WHITE
            call draw_multi_line_sentence_scrolling
        pop bc

        ; get the next state:
        ld b, 0
        ld hl, dialogue_option_target_states
        add hl, bc
        ld a, (hl)  ; target state

handle_dialogue_goto_state:
    pop de
    ; if the state to go to is #ff, end the dialogue, otherwise, find the state pointer, and jump back to handle_dialogue_init_state
    cp #ff
    jr z, handle_dialogue_exit

    ; otherwise, find the state:
    ld h, d
    ld l, e
handle_dialogue_goto_state_loop:
    cp (hl)
    jr z, handle_dialogue_init_state
    inc hl
    ld b, 0
    ld c, (hl)
    add hl, bc
    jr handle_dialogue_goto_state_loop

handle_dialogue_choice_up:
    ld a, c
    or a
    jr z, handle_dialogue_choice_loop
    push bc
        call change_current_dialogue_option_color
    pop bc
    dec c
    push bc
        call change_current_dialogue_option_color
    pop bc
    jr handle_dialogue_choice_loop


handle_dialogue_choice_down:
    ld a, (dialogue_n_options)
    dec a
    cp c
    jr z, handle_dialogue_choice_loop
    push bc
        call change_current_dialogue_option_color
    pop bc
    inc c
    push bc
        call change_current_dialogue_option_color
    pop bc
    jr handle_dialogue_choice_loop

handle_dialogue_exit:
IF PLATFORM_STORE_LAST_ACTION_LENGTH = 1
    ld a, SCREEN_WIDTH_IN_BYTES
    ld (current_action_text_last_length_in_bytes), a
ENDIF
    call clear_command_preview_text
    jp clear_text_area


;-----------------------------------------------
; Changes the color of option # "c". If it was grey, it makes it white, and viceversa
change_current_dialogue_option_color:
    ld a, (text_scrolling_next_row)
    ld hl, dialogue_n_options
    sub (hl)
    add a, c
    ld hl, PLATFORM_PTR_TO_FIRST_DIALOGUE_OPTION_COLOR
    or a
    jr z, change_current_dialogue_option_color_ptr_loop_done
    ld bc, SCREEN_WIDTH_IN_BYTES
change_current_dialogue_option_color_ptr_loop:
    add hl, bc
    dec a
    jr nz, change_current_dialogue_option_color_ptr_loop
change_current_dialogue_option_color_ptr_loop_done:
    jp change_text_line_color
