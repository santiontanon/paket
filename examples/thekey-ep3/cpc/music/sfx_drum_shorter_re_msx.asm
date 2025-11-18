	db 7, #98  ; A: tone, B: tone, C: tone+noise
	db 10, #0b  ; C volume
	db 6, #18  ; noise freq
	db 4, #aa, 5, 1  ; C freq (D2): #01aa
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
