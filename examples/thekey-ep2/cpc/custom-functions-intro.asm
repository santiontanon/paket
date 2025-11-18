;-----------------------------------------------
screen_shake:
    ld b, 5
screen_shake_loop:
    push bc
        call offset_screen_up_4pixels
        ld bc, 20
        call wait_bc_halts
        call offset_screen_up_0pixels
        ld bc, 20
        call wait_bc_halts
    pop bc
    djnz screen_shake_loop
    ret


;-----------------------------------------------
offset_screen_up_4pixels:
    di
    ld bc, #bc05
    out (c), c
    ld bc, #bd00 + 4
    out (c), c
    ld bc, #bc04
    out (c), c
    ld bc, #bd00 + 37
    out (c), c
    ei
    ret


;-----------------------------------------------
offset_screen_up_0pixels:
    di
    ld bc, #bc05
    out (c), c
    ld bc, #bd00 + 0
    out (c), c
    ld bc, #bc04
    out (c), c
    ld bc, #bd00 + 38
    out (c), c
    ei
    ret

