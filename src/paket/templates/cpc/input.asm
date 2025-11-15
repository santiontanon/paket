;-----------------------------------------------
; updates the 'keyboard_line_state' and 'keyboard_line_state_prev' buffers
update_keyboard_buffers:
    ld de, keyboard_line_state
    ld hl, keyboard_line_state_prev
    ld bc, keyboard_line_clicks
    ld a, 6
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
    jr nz,update_keyboard_buffers_loop

    ld a, #40
    call read_keyboard_line
    ld (keyboard_line_state), a  ; F Dot, ENTER, F3, F6, F9, DOWN, RIGHT, UP

    ld a, #41
    call read_keyboard_line
    ld (keyboard_line_state + 1), a  ; F0, F2, F1, F5, F8, F7, COPY, LEFT

    ld a, #42
    call read_keyboard_line
    ld (keyboard_line_state + 2), a  ; CONTROL, \, SHIFT, F4, ], RETURN, [, CLR

    ld a, #44
    call read_keyboard_line
    ld (keyboard_line_state + 3), a  ; ,, M, K, L, I, O, 9, 0

    ld a, #45
    call read_keyboard_line
    ld (keyboard_line_state + 4), a  ; SPACE, N, J, H, Y, U, 7, 8

    ld a, #48
    call read_keyboard_line
    ld (keyboard_line_state + 5), a  ; Z, CAPSLOCK, A, TAB, Q, ESC, 2, 1

    call read_joystick


;-----------------------------------------------
update_music_muted_toggle:
    ld a, (keyboard_line_clicks + 3)
    bit 6, a  ; 'M' key
    ret z
    ld a, (MUSIC_muted)
    xor #01
    ld (MUSIC_muted), a
    jp clear_PSG_volume


;-----------------------------------------------
; Reads the joystick status and translates to keyboard input
read_joystick:
    
    ld a, #49  ; read joystick status
    call read_keyboard_line  ; &49   DEL, Joy C, Joy B, Joy A, Joy1 right, Joy1 left, Joy1 down, Joy1 up
    ld c, a

    ; down, up:
    ld hl, keyboard_line_state
    ld a, (hl)

    rr c
    jr c, read_joystick_noUp
    and #fe
read_joystick_noUp:

    rr c
    jr c, read_joystick_noDown
    and #fb
read_joystick_noDown:
    ld (hl), a

    ; left:
    inc hl
    ld a, (hl)  ; keyboard_line_state + 1
    rr c
    jr c, read_joystick_noLeft
    and #fe
read_joystick_noLeft:
    ld (hl), a

    ; right:
    dec hl
    ld a, (hl)  ; keyboard_line_state
    rr c
    jr c, read_joystick_noRight
    and #fd
read_joystick_noRight:
    ld (hl), a

    ; space:
    inc hl
    inc hl
    inc hl
    inc hl
    ld a, (hl)  ; keyboard_line_state + 4
    rr c
    jr c, read_joystick_noA
    and #7f
read_joystick_noA:
    ld (hl), a

    ; TAB:
    inc hl
    ld a, (hl)  ; keyboard_line_state + 5
    rr c
    jr c, read_joystick_noB
    and #ef
read_joystick_noB:
    ld (hl), a
    ret


;-----------------------------------------------
; Reads a keyboard row
; code from: http://www.cpcwiki.eu/index.php/Programming:Keyboard_scanning
; input:
; - a: keyboard line
read_keyboard_line:
    di
    ld d, 0
    ld bc, #f782  ; PPI port A out / C out 
    out (c), c 
    ld bc, #f40e  ; Select Ay reg 14 on ppi port A 
    out (c), c 
    ld bc, #f6c0  ; This value is an AY index (R14) 
    out (c), c 
    out (c), d  ; Validate!! out (c),0
    ld bc, #f792  ; PPI port A in / C out 
    out (c), c 
    dec b 
    out (c), a  ; Send KbdLine on reg 14 AY through ppi port A
    ld b, #f4  ; Read ppi port A 
    in a, (c)  ; e.g. AY R14 (AY port A) 
    ld bc, #f782  ; PPI port A out / C out 
    out (c), c 
    dec b  ; Reset PPI Write 
    out (c), d  ; out (c),0
    ei
    ret


;-----------------------------------------------
; Waits until the player presses space
wait_for_space:
    call update_keyboard_buffers
    ld a, (keyboard_line_clicks + 4)
    bit 7, a
    ret nz
    halt
    
IF MUSIC_TYPE_TSV == 1
    ; See if we want to load a new song:
    call song_load_request_check
ENDIF

    jr wait_for_space


