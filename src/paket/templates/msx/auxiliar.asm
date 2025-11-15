;-----------------------------------------------
; From: http://www.z80st.es/downloads/code/ (author: Konamiman)
; GETSLOT:  constructs the SLOT value to then call ENSALT
; input:
; a: slot
; output:
; a: value for ENSALT
GETSLOT:    
    and #03             ; Proteccion, nos aseguramos de que el valor esta en 0-3
    ld  c,a             ; c = slot de la pagina
    ld  b,0             ; bc = slot de la pagina
    ld  hl,#FCC1            ; Tabla de slots expandidos
    add hl,bc               ; hl -> variable que indica si este slot esta expandido
    ld  a,(hl)              ; Tomamos el valor
    and #80             ; Si el bit mas alto es cero...
    jr  z,GETSLOT_EXIT            ; ...nos vamos a @@EXIT
    ; --- El slot esta expandido ---
    or  c               ; Slot basico en el lugar adecuado
    ld  c,a             ; Guardamos el valor en c
    inc hl              ; Incrementamos hl una...
    inc hl              ; ...dos...
    inc hl              ; ...tres...
    inc hl              ; ...cuatro veces
    ld  a,(hl)              ; a = valor del registro de subslot del slot donde estamos
    and #0C             ; Nos quedamos con el valor donde esta nuestro cartucho
GETSLOT_EXIT:     
    or  c               ; Slot extendido/basico en su lugar
    ret                 ; Volvemos

;-----------------------------------------------
; From: http://www.z80st.es/downloads/code/
; SETPAGES32K:  BIOS-ROM-YY-ZZ   -> BIOS-ROM-ROM-ZZ (SITUA PAGINA 2)
SETPAGES32K:    ; --- Posiciona las paginas de un megarom o un 32K ---
    ld  a,#C9               ; Codigo de RET
    ld  (SETPAGES32K_NOPRET),a            ; Modificamos la siguiente instruccion si estamos en RAM
SETPAGES32K_NOPRET:   
    nop                     ; No hacemos nada si no estamos en RAM
    ; --- Si llegamos aqui no estamos en RAM, hay que posicionar la pagina ---
    call RSLREG             ; Leemos el contenido del registro de seleccion de slots
    rrca                    ; Rotamos a la derecha...
    rrca                    ; ...dos veces
    call GETSLOT            ; Obtenemos el slot de la pagina 1 ($4000-$BFFF)
    ld  h,#80               ; Seleccionamos pagina 2 ($8000-$BFFF)
    jp  ENASLT              ; Posicionamos la pagina 2 y volvemos


;-----------------------------------------------
; clears a memory area with 0
; input:
; - hl: memory area
; - bc: amount to clear
clear_memory:
    xor a
clear_memory_to_a:
    ld d, h
    ld e, l
    inc de
    ld (hl), a
    dec bc
    ldir
    ret
    
    
;-----------------------------------------------
; Multiply "de" by "a", output is:
; - multiplication result in "hl"
; Code borrowed from: http://sgate.emt.bme.hu/patai/publications/z80guide/part4.html
mul8:                            ; this routine performs the operation HL=DE*A
    ld hl, 0                        ; HL is used to accumulate the result
    ld b, 8                         ; the multiplier (A) is 8 bits wide
Mul8Loop:
    rrca                           ; putting the next bit into the carry
    jp nc,mul8skip                 ; if zero, we skip the addition (jp is used for speed)
    add hl, de                      ; adding to the product if necessary
mul8skip:
    sla e                          ; calculating the next auxiliary product by shifting
    rl d                           ; DE one bit leftwards (refer to the shift instructions!)
    djnz Mul8Loop
    ret


;-----------------------------------------------
; I don't like redefining a BIOS function, but this is so that I can unify the
; CPC and MSX code, making arguments the same for both.
; input:
; - C = register number
; - A = register data
; Values obtained from cbios source code: https://cbios.sourceforge.net/
PSG_REGS: equ #A0  ; PSG register write port
PSG_DATA: equ #A1  ; PSG value write port
WRTPSG_PKT:
    di
    push af
        ld a, c
        out (PSG_REGS), a
    pop af
    out (PSG_DATA), a
    ei
    ret


;-----------------------------------------------
; input:
; - hl: ptr to the text line
change_text_line_color:
    push hl
        ld de, general_buffer
        ld bc, 1
        call LDIRMV
    pop hl
    ld a, (general_buffer)
    cp COLOR_WHITE*16
    jr z, change_current_dialogue_option_color_grey
    ld a, COLOR_WHITE*16
    jr change_current_dialogue_option_color_set
change_current_dialogue_option_color_grey:
    ld a, COLOR_DARK_BLUE*16
change_current_dialogue_option_color_set:
    ld bc, GUI_WIDTH*8
    jp FILVRM


;-----------------------------------------------
; A couple of useful macros for adding 16 and 8 bit numbers

ADD_HL_A: MACRO 
    add a, l
    ld l, a
    jr nc, $ + 3
    inc h
    ENDM


ADD_DE_A: MACRO 
    add a, e
    ld e, a
    jr nc, $ + 3
    inc d
    ENDM    


ADD_HL_A_VIA_BC: MACRO
    ld b, 0
    ld c, a
    add hl, bc
    ENDM


