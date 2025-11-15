; Original source code: https://github.com/AugustoRuiz/WYZTracker
; - Converted to Zilog z80 notation by Santiago Ontañón with https://github.com/santiontanon/mdlz80optimizer
; - ayfx SFX support added by Santiago Ontañón

; MSX PSG proPLAYER V 0.47c - WYZ 19.03.2016
; (WYZTracker 2.0 o superior)

; CARACTERISTICAS
; 5 OCTAVAS:		O[2-6]=60 NOTAS
; 4 LONGITUDES DE NOTA: L[0-3]+PUNTILLO

; LOS DATOS QUE HAY QUE VARIAR :
; * BUFFER DE SONIDO DONDE SE DECODIFICA TOTALMENTE EL ARCHIVO MUS
; * Nº DE CANCION.
; * TABLA DE CANCIONES
;______________________________________________________


sound_update:
INICIO:    call ROUT

    ld hl, PSG_REG
    ld de, PSG_REG_SEC
    ld bc, 14
    ldir
    call REPRODUCE_SONIDO
    jp PLAY
; CALL	REPRODUCE_EFECTO
; RET



;REPRODUCE EFECTOS DE SONIDO

REPRODUCE_SONIDO:

    ld hl, INTERR
    bit 2, (hl)  ;ESTA ACTIVADO EL EFECTO?
    ret z
    ld hl, (PUNTERO_SONIDO)
    ld a, (hl)
    cp #ff
    jr z, FIN_SONIDO
    ld de, (SFX_L)
    ld (de), a
    inc hl
    ld a, (hl)
    rrca
    rrca
    rrca
    rrca
    and 00001111b
    ld de, (SFX_H)
    ld (de), a
    ld a, (hl)
    and 00001111b
    ld de, (SFX_V)
    ld (de), a

    inc hl
    ld a, (hl)
    ld b, a
    bit 7, a  ;09.08.13 BIT MAS SIGINIFICATIVO ACTIVA ENVOLVENTES
    jr z, NO_ENVOLVENTES_SONIDO
    ld a, #12
    ld (de), a
    inc hl
    ld a, (hl)
    ld (PSG_REG_SEC + 11), a
    inc hl
    ld a, (hl)
    ld (PSG_REG_SEC + 12), a
    inc hl
    ld a, (hl)
    cp 1
    jr z, NO_ENVOLVENTES_SONIDO  ;NO ESCRIBE LA ENVOLVENTE SI SU VALOR ES 1
    ld (PSG_REG_SEC + 13), a


NO_ENVOLVENTES_SONIDO:

    ld a, b
    and #7f  ; RES	7,A
; AND	A
    jr z, NO_RUIDO
    ld (PSG_REG_SEC + 6), a
    ld a, (SFX_MIX)
    jr SI_RUIDO
NO_RUIDO:    xor a
    ld (PSG_REG_SEC + 6), a
    ld a, 10111000b
SI_RUIDO:    ld (PSG_REG_SEC + 7), a

    inc hl
    ld (PUNTERO_SONIDO), hl
    ret
FIN_SONIDO:    ld hl, INTERR
    res 2, (hl)
    ld a, (ENVOLVENTE_BACK)  ;NO RESTAURA LA ENVOLVENTE SI ES 0
    and a
    jr z, FIN_NOPLAYER
;xor	a ; ***
    ld (PSG_REG_SEC + 13), a  ;08.13 RESTAURA LA ENVOLVENTE TRAS EL SFX

FIN_NOPLAYER:    ld a, 10111000b
    ld (PSG_REG_SEC + 7), a

    ret



;VUELCA BUFFER DE SONIDO AL PSG

ROUT:    ld a, (PSG_REG + 13)
    and a  ;ES CERO?
    jr z, NO_BACKUP_ENVOLVENTE
    ld (ENVOLVENTE_BACK), a  ;08.13 / GUARDA LA ENVOLVENTE EN EL BACKUP
    xor a

NO_BACKUP_ENVOLVENTE:
    ld hl, PSG_REG_SEC
LOUT:
 CALL WRITEPSGHL
 inc hl
 INC A
 CP 13
 JR NZ,LOUT
 LD A,(HL)
 AND A
 RET Z
 LD A,13
 CALL WRITEPSGHL
 XOR A
 LD (PSG_REG+13),A
 LD (PSG_REG_SEC+13),A
 RET


;; A = REGISTER
;; (HL) = VALUE
WRITEPSGHL:
    push bc
        ld c, a
        push af
            ld a, (hl)
            call WRTPSG_PKT
        pop af
    pop bc
    ret

;INICIA EL SONIDO NUMERO [A]

INICIA_SONIDO:  ;CP	8		;SFX SPEECH
;JP	Z,SLOOP		;

    ld hl, TABLA_SONIDOS
    call EXT_WORD
    ld (PUNTERO_SONIDO), de
    ld hl, INTERR
    set 2, (hl)
    ret

;PLAYER INIT

PLAYER_INIT:    ld hl, BUFFER_CANAL_A  ;RESERVAR MEMORIA PARA BUFFER DE SONIDO!!!!!
    ld (CANAL_A), hl  ;RECOMENDABLE $10 O MAS BYTES POR CANAL.

    ld hl, BUFFER_CANAL_B
    ld (CANAL_B), hl

    ld hl, BUFFER_CANAL_C
    ld (CANAL_C), hl

    ld hl, BUFFER_CANAL_P
    ld (CANAL_P), hl

;PLAYER OFF

StopPlayingMusic:
PLAYER_OFF:    xor a  ;***** IMPORTANTE SI NO HAY MUSICA ****
    ld (INTERR), a
    ;LD	[FADE],A		;solo si hay fade out

clear_PSG_volume:
CLEAR_PSG_BUFFER:
    call wyz_reset_psg_reg_buffers
    jp ROUT

sound_update_music_muted:
    call ROUT
    ; jp wyz_reset_psg_reg_buffers

wyz_reset_psg_reg_buffers:
    xor a
    ld hl, PSG_REG
    ld de, PSG_REG + 1
    ld bc, 14
    ld (hl), a
    ldir

    ld a, 10111000b  ; **** POR SI ACASO ****
    ld (PSG_REG + 7), a

    ld hl, PSG_REG
    ld de, PSG_REG_SEC
    ld bc, 14
    ldir
    ret


;CARGA UNA CANCION
;IN:[A]=N� DE CANCION

CARGA_CANCION:    ld hl, INTERR  ;CARGA CANCION

    set 1, (hl)  ;REPRODUCE CANCION
    ; ld hl, SONG
    ld hl, MUSIC_current_song
    inc a
    ld (hl), a  ;N� A



;DECODIFICAR
;IN-> INTERR 0 ON
;     SONG

;CARGA CANCION SI/NO

DECODE_SONG:    
    ; ld a, (SONG)
    ld a, (MUSIC_current_song)
    dec a

;LEE CABECERA DE LA CANCION
;BYTE 0=TEMPO

    ld hl, WYZ_SONG_TABLE
    call EXT_WORD
    ld a, (de)
    ld (TEMPO), a
    dec a
    ld (TTEMPO), a

;HEADER BYTE 1
;[-|-|-|-|  3-1 | 0  ]
;[-|-|-|-|FX CHN|LOOP]

    inc de  ;LOOP 1=ON/0=OFF?
    ld a, (de)
    bit 0, a
    jr z, NPTJP0
    ld hl, INTERR
    set 4, (hl)



;SELECCION DEL CANAL DE EFECTOS DE RITMO

NPTJP0:    and 00000110b
    rra
;LD	[SELECT_CANAL_P],A

    push de
    ld hl, TABLA_DATOS_CANAL_SFX
    call EXT_WORD
    push de
    pop ix
    ld e, (ix + 0)
    ld d, (ix + 1)
    ld (SFX_L), de

    ld e, (ix + 2)
    ld d, (ix + 3)
    ld (SFX_H), de

    ld e, (ix + 4)
    ld d, (ix + 5)
    ld (SFX_V), de

    ld a, (ix + 6)
    ld (SFX_MIX), a
    pop hl

    inc hl  ;2 BYTES RESERVADOS
    inc hl
    inc hl

;BUSCA Y GUARDA INICIO DE LOS CANALES EN EL MODULO MUS (OPTIMIZAR****************)
;A�ADE OFFSET DEL LOOP

    push hl  ;IX INICIO OFFSETS LOOP POR CANAL
    pop ix

    ld de, #0008  ;HASTA INICIO DEL CANAL A
    add hl, de


    ld (PUNTERO_P_DECA), hl  ;GUARDA PUNTERO INICIO CANAL
    ld e, (ix + 0)
    ld d, (ix + 1)
    add hl, de
    ld (PUNTERO_L_DECA), hl  ;GUARDA PUNTERO INICIO LOOP

    call BGICMODBC1
    ld (PUNTERO_P_DECB), hl
    ld e, (ix + 2)
    ld d, (ix + 3)
    add hl, de
    ld (PUNTERO_L_DECB), hl

    call BGICMODBC1
    ld (PUNTERO_P_DECC), hl
    ld e, (ix + 4)
    ld d, (ix + 5)
    add hl, de
    ld (PUNTERO_L_DECC), hl

    call BGICMODBC1
    ld (PUNTERO_P_DECP), hl
    ld e, (ix + 6)
    ld d, (ix + 7)
    add hl, de
    ld (PUNTERO_L_DECP), hl


;LEE DATOS DE LAS NOTAS
;[|][|||||] LONGITUD\NOTA

INIT_DECODER:    ld de, (CANAL_A)
    ld (PUNTERO_A), de
    ld hl, (PUNTERO_P_DECA)
    call DECODE_CANAL  ;CANAL A
    ld (PUNTERO_DECA), hl

    ld de, (CANAL_B)
    ld (PUNTERO_B), de
    ld hl, (PUNTERO_P_DECB)
    call DECODE_CANAL  ;CANAL B
    ld (PUNTERO_DECB), hl

    ld de, (CANAL_C)
    ld (PUNTERO_C), de
    ld hl, (PUNTERO_P_DECC)
    call DECODE_CANAL  ;CANAL C
    ld (PUNTERO_DECC), hl

    ld de, (CANAL_P)
    ld (PUNTERO_P), de
    ld hl, (PUNTERO_P_DECP)
    call DECODE_CANAL  ;CANAL P
    ld (PUNTERO_DECP), hl

    ret

;BUSCA INICIO DEL CANAL

BGICMODBC1:    ld e, #3f  ;CODIGO INSTRUMENTO 0
BGICMODBC2:    xor a  ;BUSCA EL BYTE 0
    ld b, #ff  ;EL MODULO DEBE TENER UNA LONGITUD MENOR DE $FF00 ... o_O!
    cpir

    dec hl
    dec hl
    ld a, e  ;ES EL INSTRUMENTO 0??
    cp (hl)
    inc hl
    inc hl
    jr z, BGICMODBC2

    dec hl
    dec hl
    dec hl
    ld a, e  ;ES VOLUMEN 0??
    cp (hl)
    inc hl
    inc hl
    inc hl
    jr z, BGICMODBC2
    ret

;DECODIFICA NOTAS DE UN CANAL
;IN [DE]=DIRECCION DESTINO
;NOTA=0 FIN CANAL
;NOTA=1 SILENCIO
;NOTA=2 PUNTILLO
;NOTA=3 COMANDO I

DECODE_CANAL:    ld a, (hl)
    and a  ;FIN DEL CANAL?
    jr z, FIN_DEC_CANAL
    call GETLEN

    cp 00000001b  ;ES SILENCIO?
    jr nz, NO_SILENCIO
    or #40  ; SET	6,A
    jr NO_MODIFICA

NO_SILENCIO:    cp 00111110b  ;ES PUNTILLO?
    jr nz, NO_PUNTILLO
    or a
    rrc b
    xor a
    jr NO_MODIFICA

NO_PUNTILLO:    cp 00111111b  ;ES COMANDO?
    jr nz, NO_MODIFICA
    bit 0, b  ;COMADO=INSTRUMENTO?
    jr z, NO_INSTRUMENTO
    ld a, 11000001b  ;CODIGO DE INSTRUMENTO
    ld (de), a
    inc hl
    inc de
    ldi  ; LD	A,[HL]		;N� DE INSTRUMENTO
; LD	[DE],A
; INC	DE
; INC	HL
    ldi  ; LD	A,[HL]		;VOLUMEN RELATIVO DEL INSTRUMENTO
; LD	[DE],A
; INC	DE
; INC	HL
    jr DECODE_CANAL

NO_INSTRUMENTO:    bit 2, b
    jr z, NO_ENVOLVENTE
    ld a, 11000100b  ;CODIGO ENVOLVENTE
    ld (de), a
    inc de
    inc hl
    ldi  ; LD	A,[HL]
; LD	[DE],A
; INC	DE
; INC	HL
    jr DECODE_CANAL

NO_ENVOLVENTE:    bit 1, b
    jr z, NO_MODIFICA
    ld a, 11000010b  ;CODIGO EFECTO
    ld (de), a
    inc hl
    inc de
    ld a, (hl)
    call GETLEN

NO_MODIFICA:    ld (de), a
    inc de
    xor a
    djnz NO_MODIFICA
    or #81  ; SET	7,A
; SET	0,A
    ld (de), a
    inc de
    inc hl
    ret  ;** JR	    DECODE_CANAL

FIN_DEC_CANAL:    or #80  ; SET	7,A
    ld (de), a
    inc de
    ret

GETLEN:    ld b, a
    and 00111111b
    push af
    ld a, b
    and 11000000b
    rlca
    rlca
    inc a
    ld b, a
    ld a, 10000000b
DCBC0:    rlca
    djnz DCBC0
    ld b, a
    pop af
    ret





;PLAY __________________________________________________


PLAY:    ld hl, INTERR  ;PLAY BIT 1 ON?
    bit 1, (hl)
    ret z
;TEMPO
    ld hl, TTEMPO  ;CONTADOR TEMPO
    inc (hl)
    ld a, (TEMPO)
    cp (hl)
    jr nz, PAUTAS
    ld (hl), 0

;INTERPRETA
    ld iy, PSG_REG
    ld ix, PUNTERO_A
    ld bc, PSG_REG + 8
    call LOCALIZA_NOTA
    ld iy, PSG_REG + 2
    ld ix, PUNTERO_B
    ld bc, PSG_REG + 9
    call LOCALIZA_NOTA
    ld iy, PSG_REG + 4
    ld ix, PUNTERO_C
    ld bc, PSG_REG + 10
    call LOCALIZA_NOTA
    ld ix, PUNTERO_P  ;EL CANAL DE EFECTOS ENMASCARA OTRO CANAL
    call LOCALIZA_EFECTO

;PAUTAS

PAUTAS:    ld iy, PSG_REG + 0
    ld ix, PUNTERO_P_A
    ld hl, PSG_REG + 8
    call PAUTA  ;PAUTA CANAL A
    ld iy, PSG_REG + 2
    ld ix, PUNTERO_P_B
    ld hl, PSG_REG + 9
    call PAUTA  ;PAUTA CANAL B
    ld iy, PSG_REG + 4
    ld ix, PUNTERO_P_C
    ld hl, PSG_REG + 10
    jp PAUTA  ;PAUTA CANAL C





;LOCALIZA NOTA CANAL A
;IN [PUNTERO_A]

;LOCALIZA NOTA CANAL A
;IN [PUNTERO_A]

LOCALIZA_NOTA:    ld l, (ix + PUNTERO_A - PUNTERO_A)  ;HL=[PUNTERO_A_C_B]
    ld h, (ix + PUNTERO_A - PUNTERO_A + 1)
    ld a, (hl)
    and 11000000b  ;COMANDO?
    cp 11000000b
    jr nz, LNJP0

;BIT[0]=INSTRUMENTO

COMANDOS:    ld a, (hl)
    bit 0, a  ;INSTRUMENTO
    jr z, COM_EFECTO

    inc hl
    ld a, (hl)  ;N� DE PAUTA
    inc hl
    ld e, (hl)

    push hl  ;;TEMPO ******************
    ld hl, TEMPO
    bit 5, e
    jr z, NO_DEC_TEMPO
    dec (hl)
NO_DEC_TEMPO:    bit 6, e
    jr z, NO_INC_TEMPO
    inc (hl)
NO_INC_TEMPO:    res 5, e  ;SIEMPRE RESETEA LOS BITS DE TEMPO
    res 6, e
    pop hl

    ld (ix + VOL_INST_A - PUNTERO_A), e  ;REGISTRO DEL VOLUMEN RELATIVO
    inc hl
    ld (ix + PUNTERO_A - PUNTERO_A), l
    ld (ix + PUNTERO_A - PUNTERO_A + 1), h
    ld hl, TABLA_PAUTAS
    call EXT_WORD
    ld (ix + PUNTERO_P_A0 - PUNTERO_A), e
    ld (ix + PUNTERO_P_A0 - PUNTERO_A + 1), d
    ld (ix + PUNTERO_P_A - PUNTERO_A), e
    ld (ix + PUNTERO_P_A - PUNTERO_A + 1), d
    ld l, c
    ld h, b
    res 4, (hl)  ;APAGA EFECTO ENVOLVENTE
    xor a
    ld (PSG_REG_SEC + 13), a
    ld (PSG_REG + 13), a
;LD	[ENVOLVENTE_BACK],A		;08.13 / RESETEA EL BACKUP DE LA ENVOLVENTE
    jr LOCALIZA_NOTA

COM_EFECTO:    bit 1, a  ;EFECTO DE SONIDO
    jr z, COM_ENVOLVENTE

    inc hl
    ld a, (hl)
    inc hl
    ld (ix + PUNTERO_A - PUNTERO_A), l
    ld (ix + PUNTERO_A - PUNTERO_A + 1), h
    jp INICIA_SONIDO

COM_ENVOLVENTE:    bit 2, a
    ret z  ;IGNORA - ERROR

    inc hl
    ld a, (hl)  ;CARGA CODIGO DE ENVOLVENTE
    ld (ENVOLVENTE), a
    inc hl
    ld (ix + PUNTERO_A - PUNTERO_A), l
    ld (ix + PUNTERO_A - PUNTERO_A + 1), h
    ld l, c
    ld h, b
    ld (hl), 00010000b  ;ENCIENDE EFECTO ENVOLVENTE
    jr LOCALIZA_NOTA


LNJP0:    ld a, (hl)
    inc hl
    bit 7, a
    jr z, NO_FIN_CANAL_A  ;
    bit 0, a
    jr z, FIN_CANAL_A

FIN_NOTA_A:    ld e, (ix + CANAL_A - PUNTERO_A)
    ld d, (ix + CANAL_A - PUNTERO_A + 1)  ;PUNTERO BUFFER AL INICIO
    ld (ix + PUNTERO_A - PUNTERO_A), e
    ld (ix + PUNTERO_A - PUNTERO_A + 1), d
    ld l, (ix + PUNTERO_DECA - PUNTERO_A)  ;CARGA PUNTERO DECODER
    ld h, (ix + PUNTERO_DECA - PUNTERO_A + 1)
    push bc
    call DECODE_CANAL  ;DECODIFICA CANAL
    pop bc
    ld (ix + PUNTERO_DECA - PUNTERO_A), l  ;GUARDA PUNTERO DECODER
    ld (ix + PUNTERO_DECA - PUNTERO_A + 1), h
    jp LOCALIZA_NOTA

FIN_CANAL_A:    ld hl, INTERR  ;LOOP?
    bit 4, (hl)
    jr nz, FCA_CONT
    pop af
    jp PLAYER_OFF


FCA_CONT:    ld l, (ix + PUNTERO_L_DECA - PUNTERO_A)  ;CARGA PUNTERO INICIAL DECODER
    ld h, (ix + PUNTERO_L_DECA - PUNTERO_A + 1)
    ld (ix + PUNTERO_DECA - PUNTERO_A), l
    ld (ix + PUNTERO_DECA - PUNTERO_A + 1), h
    jr FIN_NOTA_A

NO_FIN_CANAL_A:    ld (ix + PUNTERO_A - PUNTERO_A), l  ;[PUNTERO_A_B_C]=HL GUARDA PUNTERO
    ld (ix + PUNTERO_A - PUNTERO_A + 1), h
    and a  ;NO REPRODUCE NOTA SI NOTA=0
    jr z, FIN_RUTINA
    bit 6, a  ;SILENCIO?
    jr z, NO_SILENCIO_A
    ld a, (bc)
    and 00010000b
    jr nz, SILENCIO_ENVOLVENTE

    xor a
    ld (bc), a  ;RESET VOLUMEN DEL CORRESPODIENTE CHIP
    ld (iy + 0), a
    ld (iy + 1), a
    ret

SILENCIO_ENVOLVENTE:
    ld a, #ff
    ld (PSG_REG + 11), a
    ld (PSG_REG + 12), a
    xor a
    ld (PSG_REG + 13), a
    ld (iy + 0), a
    ld (iy + 1), a
    ret

NO_SILENCIO_A:    ld (ix + REG_NOTA_A - PUNTERO_A), a  ;REGISTRO DE LA NOTA DEL CANAL
    call NOTA  ;REPRODUCE NOTA
    ld l, (ix + PUNTERO_P_A0 - PUNTERO_A)  ;HL=[PUNTERO_P_A0] RESETEA PAUTA
    ld h, (ix + PUNTERO_P_A0 - PUNTERO_A + 1)
    ld (ix + PUNTERO_P_A - PUNTERO_A), l  ;[PUNTERO_P_A]=HL
    ld (ix + PUNTERO_P_A - PUNTERO_A + 1), h
FIN_RUTINA:    ret


;LOCALIZA EFECTO
;IN HL=[PUNTERO_P]

LOCALIZA_EFECTO:    ld l, (ix + 0)  ;HL=[PUNTERO_P]
    ld h, (ix + 1)
    ld a, (hl)
    cp 11000010b
    jr nz, LEJP0

    inc hl
    ld a, (hl)
    inc hl
    ld (ix + 0), l
    ld (ix + 1), h
    jp INICIA_SONIDO


LEJP0:    inc hl
    bit 7, a
    jr z, NO_FIN_CANAL_P  ;
    bit 0, a
    jr z, FIN_CANAL_P
FIN_NOTA_P:    ld de, (CANAL_P)
    ld (ix + 0), e
    ld (ix + 1), d
    ld hl, (PUNTERO_DECP)  ;CARGA PUNTERO DECODER
    push bc
    call DECODE_CANAL  ;DECODIFICA CANAL
    pop bc
    ld (PUNTERO_DECP), hl  ;GUARDA PUNTERO DECODER
    jp LOCALIZA_EFECTO

FIN_CANAL_P:    ld hl, (PUNTERO_L_DECP)  ;CARGA PUNTERO INICIAL DECODER
    ld (PUNTERO_DECP), hl
    jr FIN_NOTA_P

NO_FIN_CANAL_P:    ld (ix + 0), l  ;[PUNTERO_A_B_C]=HL GUARDA PUNTERO
    ld (ix + 1), h
    ret

; PAUTA DE LOS 3 CANALES
; IN:[IX]:PUNTERO DE LA PAUTA
;    [HL]:REGISTRO DE VOLUMEN
;    [IY]:REGISTROS DE FRECUENCIA

; FORMATO PAUTA
;	    7	 6     5     4	 3-0			    3-0
; BYTE 1 [LOOP|OCT-1|OCT+1|ORNMT|VOL] - BYTE 2 [ | | | |PITCH/NOTA]

PAUTA:    bit 4, (hl)  ;SI LA ENVOLVENTE ESTA ACTIVADA NO ACTUA PAUTA
    ret nz

    ld a, (iy + 0)
    ld b, (iy + 1)
    or b
    ret z


    push hl

PCAJP4:    ld l, (ix + 0)
    ld h, (ix + 1)
    ld a, (hl)

    bit 7, a  ;LOOP / EL RESTO DE BITS NO AFECTAN
    jr z, PCAJP0
    and 00011111b  ;M�XIMO LOOP PAUTA [0,32]X2!!!-> PARA ORNAMENTOS
    rlca  ;X2
    ld d, 0
    ld e, a
    sbc hl, de
    ld a, (hl)

PCAJP0:    bit 6, a  ;OCTAVA -1
    jr z, PCAJP1
    ld e, (iy + 0)
    ld d, (iy + 1)

    and a
    rrc d
    rr e
    ld (iy + 0), e
    ld (iy + 1), d
    jr PCAJP2

PCAJP1:    bit 5, a  ;OCTAVA +1
    jr z, PCAJP2
    ld e, (iy + 0)
    ld d, (iy + 1)

    and a
    rlc e
    rl d
    ld (iy + 0), e
    ld (iy + 1), d




PCAJP2:    ld a, (hl)
    bit 4, a
    jr nz, PCAJP6  ;ORNAMENTOS SELECCIONADOS

    inc hl  ;______________________ FUNCION PITCH DE FRECUENCIA__________________
    push hl
    ld e, a
    ld a, (hl)  ;PITCH DE FRECUENCIA
    ld l, a
    and a
    ld a, e
    jr z, ORNMJP1

    ld a, (iy + 0)  ;SI LA FRECUENCIA ES 0 NO HAY PITCH
    add a, (iy + 1)
    and a
    ld a, e
    jr z, ORNMJP1


    bit 7, l
    jr z, ORNNEG
    ld h, #ff
    jr PCAJP3
ORNNEG:    ld h, 0

PCAJP3:    ld e, (iy + 0)
    ld d, (iy + 1)
    adc hl, de
    ld (iy + 0), l
    ld (iy + 1), h
    jr ORNMJP1


PCAJP6:    inc hl  ;______________________ FUNCION ORNAMENTOS__________________

    push hl
    push af
    ld a, (ix + REG_NOTA_A - PUNTERO_P_A)  ;RECUPERA REGISTRO DE NOTA EN EL CANAL
    ld e, (hl)  ;
    adc a, e  ;+- NOTA
    call TABLA_NOTAS
    pop af


ORNMJP1:    pop hl

    inc hl
    ld (ix + 0), l
    ld (ix + 1), h
PCAJP5:    pop hl
    ld b, (ix + VOL_INST_A - PUNTERO_P_A)  ;VOLUMEN RELATIVO
    add a, b
    jp p, PCAJP7
    ld a, 1  ;NO SE EXTIGUE EL VOLUMEN
PCAJP7:    and 00001111b  ;VOLUMEN FINAL MODULADO
    ld (hl), a
    ret



;NOTA : REPRODUCE UNA NOTA
;IN [A]=CODIGO DE LA NOTA
;   [IY]=REGISTROS DE FRECUENCIA


NOTA:    ld l, c
    ld h, b
    bit 4, (hl)
    ld b, a
    jr nz, EVOLVENTES
    ld a, b
TABLA_NOTAS:    ld hl, DATOS_NOTAS  ;BUSCA FRECUENCIA
    call EXT_WORD
    ld (iy + 0), e
    ld (iy + 1), d
    ret




;IN [A]=CODIGO DE LA ENVOLVENTE
;   [IY]=REGISTRO DE FRECUENCIA

EVOLVENTES:    ld hl, DATOS_NOTAS
;SUB	12
    rlca  ;X2
    ld d, 0
    ld e, a
    add hl, de
    ld e, (hl)
    inc hl
    ld d, (hl)

    push de
    ld a, (ENVOLVENTE)  ;FRECUENCIA DEL CANAL ON/OFF
    rra
    jr nc, FRECUENCIA_OFF
    ld (iy + 0), e
    ld (iy + 1), d
    jr CONT_ENV

FRECUENCIA_OFF:    ld de, #0000
    ld (iy + 0), e
    ld (iy + 1), d
;CALCULO DEL RATIO (OCTAVA ARRIBA)
CONT_ENV:    pop de
    push af
    push bc
    and 00000011b
    ld b, a
;INC	B

;AND	A			;1/2
    rr d
    rr e
CRTBC0:  ;AND	A			;1/4 - 1/8 - 1/16
    rr d
    rr e
    djnz CRTBC0
    ld a, e
    ld (PSG_REG + 11), a
    ld a, d
    and 00000011b
    ld (PSG_REG + 12), a
    pop bc
    pop af  ;SELECCION FORMA DE ENVOLVENTE

    rra
    and 00000110b  ;$08,$0A,$0C,$0E
    add a, 8
    ld (PSG_REG + 13), a
    ld (ENVOLVENTE_BACK), a
    ret


;EXTRAE UN WORD DE UNA TABLA
;IN:[HL]=DIRECCION TABLA
;   [A]= POSICION
;OUT[DE]=WORD

EXT_WORD:    ld d, 0
    rlca
    ld e, a
    add hl, de
    ld e, (hl)
    inc hl
    ld d, (hl)
    ret

;TABLA DE DATOS DEL SELECTOR DEL CANAL DE EFECTOS DE RITMO

TABLA_DATOS_CANAL_SFX:

    dw SELECT_CANAL_A, SELECT_CANAL_B, SELECT_CANAL_C


;BYTE 0:SFX_L
;BYTE 1:SFX_H
;BYTE 2:SFX_V
;BYTE 3:SFX_MIX

SELECT_CANAL_A:    dw PSG_REG_SEC + 0, PSG_REG_SEC + 1, PSG_REG_SEC + 8
    db 10110001b

SELECT_CANAL_B:    dw PSG_REG_SEC + 2, PSG_REG_SEC + 3, PSG_REG_SEC + 9
    db 10101010b

SELECT_CANAL_C:    dw PSG_REG_SEC + 4, PSG_REG_SEC + 5, PSG_REG_SEC + 10
    db 10011100b


;-----------------------------------------------
; - hl: sfx pointer.
play_SFX_with_high_priority:
    di
	ld (SFX_player_pointer), hl
	ld a, 1
	ld (SFX_player_active), a
    ei
    ret


;-----------------------------------------------
; SFX format (taken form the ayfxedit documentation):
; Every frame encoded with a flag byte and a number of bytes, 
; which is vary depending from value change flags.
; - bit0..3  Volume
; - bit4     Disable T
; - bit5     Change Tone
; - bit6     Change Noise
; - bit7     Disable N
; When the bit5 set, two bytes with tone period will follow; when the bit6 set, 
; a single byte with noise period will follow; when both bits are set, first two 
; bytes of tone period, then single byte with noise period will follow. When none 
; of the bits are set, next flags byte will follow.
; End of the effect is marked with byte sequence #D0, #20. Player should detect it 
; before outputting it to the AY registers, by checking noise period value to be equal #20.
;-----------------------------------------------
; Plays a sound effect on PSG channel B
; hl: sfx pointer
play_ayfx:
    ld hl, (SFX_player_pointer)
    ld a, (PSG_REG_SEC + 7) ; Register 7	
; 	ld a,(SFX_player_registers+3)  ; Register 7
    and #db ; activate tone/noise by default (channel C) 11011011
    ; and #ed ; activate tone/noise by default (channel B) 11101101
    ld d, a  ; d will maintain the value to be written in R7
    ld a, (hl)
    inc hl
    cp #d0
    jr z, play_ayfx_end_check
play_ayfx_continue1:
    ; volume:
    push af
            and #0f
            ld (SFX_player_registers+4),a  ; SFX channel volume
    pop af
    bit 4,a
    jr z,play_ayfx_continue2
play_ayfx_disable_tone:
    set 1,d  ; 0: channel A, 1: channel B, 2: channel C
play_ayfx_continue2:
    bit 7,a
    jr z,play_ayfx_continue3
play_ayfx_disable_noise:
    set 4,d  ; 3: channel A, 4: channel B, 5: channel C
play_ayfx_continue3:
    bit 5,a
    jr z,play_ayfx_continue4
    push af
            ; since there is tone period, we activate the tone in the SFX channel
; 		res 1,d  ; 0: channel A, 1: channel B, 2: channel C
            ld a,(hl)
            inc hl
            ld (SFX_player_registers+0),a  ; (channel period 1)
            ld a,(hl)
            inc hl
            ld (SFX_player_registers+1),a  ; (channel period 2)
    pop af
play_ayfx_continue4:
    bit 6,a
    jr z,play_ayfx_continue5
; 	push af
            ; since there is noise frequency, we activate the noise in the SFX channel
; 		res 4,d  ; 3: channel A, 4: channel B, 5: channel C
            ld a,(hl)
            inc hl
            ld (SFX_player_registers+2),a  ; register 6 (noise tone)
; 	pop af
play_ayfx_continue5:
    ; write register 7:
    ld a,d
    ld (SFX_player_registers+3),a  ; register 7 (which channels are active)
    ld (SFX_player_pointer),hl
    ; overwrite wyz player registers:
    ld hl,SFX_player_registers
    ; Channel A:
; 	ld de,PSG_REG_SEC
; 	ldi  ; R0
; 	ldi  ; R1
; 	inc de
; 	inc de
; 	inc de
; 	inc de
; 	ldi  ; R6
; 	ldi  ; R7
; 	ldi  ; R8

    ; Channel B:
;    ld de,PSG_REG_SEC+2
;    ldi  ; R2
;    ldi  ; R3
;    inc de
;    inc de
;    ldi  ; R6
;    ldi  ; R7
;    inc de
;    ldi  ; R9

    ; Channel C:
 	ld de,PSG_REG_SEC+4
 	ldi  ; R4
 	ldi  ; R5
 	ldi  ; R6
 	ldi  ; R7
 	inc de
 	inc de
 	ldi  ; R10
    ret

play_ayfx_end_check:
    ld a,(hl)
    cp #20
    jr z,play_ayfx_end
    dec hl
    ld a,(hl)
    inc hl
    jr play_ayfx_continue1

play_ayfx_end:
    ld a,d
    set 1,a  ; 0: channel A, 1: channel B, 2: channel C
    set 4,a  ; 3: channel A, 4: channel B, 5: channel C
    ld (SFX_player_registers+3),a  ; register 7 (which channels are active)
    xor a
    ld (SFX_player_active),a
    ret

