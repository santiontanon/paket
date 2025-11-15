;-----------------------------------------------
; updates the 'keyboard_line_state' and 'keyboard_line_state_prev' buffers
update_keyboard_buffers:
    ld de, keyboard_line_state
    ld hl, keyboard_line_state_prev
    ld bc, keyboard_line_clicks
    ld a, 4
update_keyboard_buffers_loop:
    push af
        ld a, (de)
        xor (hl)
        and (hl)
        ld (bc), a
        ld a, (de)
        ld (hl), a
    pop af
    inc hl
    inc de
    inc bc
    dec a
    jr nz, update_keyboard_buffers_loop

    ld a,#08
    call SNSMAT
    ld (keyboard_line_state), a  ; RIGHT, DOWN, UP, LEFT, DEL, INS, HOME, SPACE

    ld a,#07
    call SNSMAT
    ld (keyboard_line_state + 1), a    ; RET, SELECT, BS, STOP, TAB, ESC, F5, F4

    ld a,#03
    call SNSMAT
    ld (keyboard_line_state + 2), a    ; J, I, H, G, F, E, D, C

    ld a,#06
    call SNSMAT
    ld (keyboard_line_state + 3), a    ; F3, F2, F1, CODE, CAPS, GRAPH, CTRL, SHIFT
    
    call read_joystick

;-----------------------------------------------
update_music_muted_toggle:
    ld a, (keyboard_line_clicks + 3)
    bit 5, a  ; 'F1' key
    ret z
    ld a, (MUSIC_muted)
    xor #01
    ld (MUSIC_muted), a
    jp clear_PSG_volume


;-----------------------------------------------
; Reads the joystick status, and updates the corresponding keyboard_line_state to treat it as if it was the keyboard
read_joystick:   
    ld a,15 ; read the joystick 1 status:
    call RDPSG
    and #bf
    ld e,a
    ld a,15
    call WRTPSG
    dec a
    call RDPSG  ; a = -, -, B, A, right, left, down, up
    ; convert the joystick input to keyboard input
    ld c,a
    ; arrows/space:
    ld hl,keyboard_line_state
    ld a,(hl)
    
    rr c
    jr c,read_joystick_noUp
    and #df
read_joystick_noUp:

    rr c
    jr c,read_joystick_noDown
    and #bf
read_joystick_noDown:

    rr c
    jr c,read_joystick_noLeft
    and #ef
read_joystick_noLeft:

    rr c
    jr c,read_joystick_noRight
    and #7f
read_joystick_noRight:

    rr c
    jr c,read_joystick_noA
    and #fe
read_joystick_noA:

    ld (hl),a   ; we add the joystick input to the keyboard input

    ; tab (button 2):
    inc hl
    ld a,(hl)
    rr c
    jr c,read_joystick_noB
    and #f7
read_joystick_noB:
    ld (hl),a   ; we add the joystick input to the keyboard input

    ret


;-----------------------------------------------
; Waits until the player presses space
wait_for_space:
    call update_keyboard_buffers
    ld a, (keyboard_line_clicks)
    bit 0, a
    ret nz
    halt

IF MUSIC_TYPE_TSV == 1
    ; See if we want to load a new song:
    call song_load_request_check
ENDIF

    jr wait_for_space
