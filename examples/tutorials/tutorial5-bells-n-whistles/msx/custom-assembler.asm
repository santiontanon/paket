BIOS_FILVRM: equ #0056

my_assembler_function1:
	; Write some pixels in the top-left corner of the screen
	ld a, #ff
	ld hl, #2000
	ld bc, 1
	call BIOS_FILVRM
	ret


my_assembler_function2:
	; Write some pixels in the left part of the screen
	ld a, #ff
	ld hl, #2800
	ld bc, 1
	call BIOS_FILVRM
	ret
