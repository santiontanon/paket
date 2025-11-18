	db 7, #98  ; A: tone, B: tone, C: tone+noise
	db 10, #0b  ; C volume
	db 6, #18  ; noise freq
	db 4, 180, 5, #04  ; C freq (G1#)  4, 180
	db #80  ; MUSIC_CMD_SKIP
	db 7, #b8
	db #80  ; MUSIC_CMD_SKIP
	db #80  ; MUSIC_CMD_SKIP
	db #80  ; MUSIC_CMD_SKIP
	db 10, #08
	db #80  ; MUSIC_CMD_SKIP
	db #80  ; MUSIC_CMD_SKIP
	db 10, #00
	db 7, #b8
	db 0x89  ; SFX_CMD_END
