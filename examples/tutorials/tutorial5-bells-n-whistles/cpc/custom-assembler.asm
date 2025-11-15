my_assembler_function1:
	; Write some pixels in the top-left corner of the screen
	ld a, #ff
	ld (#c000), a
	ret

my_assembler_function2:
	; Write some pixels in the the left part of the game area
	ld a, #ff
	ld (#c200), a
	ret
