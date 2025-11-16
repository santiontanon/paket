demo_over_text:
    db 9, 71, 10, 26, 33, 0, 106, 48, 10, 39
time_played_text:
    db 19, 121, 18, 10, 26, 35, 33, 0, 8, 10, 0, 35, 2, 39, 44, 18, 8, 2, 166, 0


;-----------------------------------------------
game_ending:
	call clear_screen_pretty
	
	; try to center it:
	ld de,VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*(N_REGULAR_TEXT_ROWS-1) + SCREEN_WIDTH_IN_BYTES/2 - 6
	ld hl,demo_over_text
	ld iyl,#88
	call draw_sentence

	; synthesize the final text:
	ld hl,time_played_text
	ld de,general_buffer
	ld a,(time_played_text)
	inc a
	ld b,0
	ld c,a
	ldir

	; we disable the interrupts, so that the game time does not increase while we are doing this:
	di
		; hours:
		ld a,(time_hours)
		or a
		jr z,game_ending_minutes
		call game_ending_add_number_to_string
		ld a,(numbers_and_colon_text+11)	; colon
		ld (de),a
		inc de

game_ending_minutes:
		; minutes:
		ld a,(time_minutes)
		or a
		jr z,game_ending_seconds
		call game_ending_add_number_to_string
		ld a,(numbers_and_colon_text+11)	; colon
		ld (de),a
		inc de

game_ending_seconds:
		; seconds:
		ld a,(time_seconds)
		call game_ending_add_number_to_string
	ei

	; calculate new length:
	ex de,hl
	ld bc,general_buffer+1
	xor a
	sbc hl,bc
	ld a,l
	ld (general_buffer),a

	ld hl,general_buffer
	push hl
		call sentence_width_in_bytes
		ld hl,VIDEO_MEMORY + SCREEN_WIDTH_IN_BYTES*N_REGULAR_TEXT_ROWS + SCREEN_WIDTH_IN_BYTES/2
		srl a	; width of the sentence in bytes/2
		ld b,0
		ld c,a
		xor a
		sbc hl,bc
		ex de,hl
	pop hl
	ld iyl,#88
	call draw_sentence

	; wait for space:
	call wait_for_space

	call clear_screen_pretty
    ld sp,VIDEO_MEMORY  
	jp game_start


game_ending_add_number_to_string:
	ld hl,numbers_and_colon_text+1
	; tens:
game_ending_add_number_to_string_loop:
	cp 10
	jp m,game_ending_add_number_to_string_found
	sub 10
	inc hl
	jr game_ending_add_number_to_string_loop
game_ending_add_number_to_string_found:
	ldi

	; units:
	ld hl,numbers_and_colon_text+1
	ADD_HL_A
	ldi

	ret
