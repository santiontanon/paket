    db  7,#98    ;; noise in channel C, and tone in channels B and A
    db 10,#08    ;; volume
    db 5, 8, 4, #6b ;; G#
    db  6,#18    ;; noise frequency
    db #80  ; MUSIC_CMD_SKIP
    ;db 5, #03, 4, #20
    db  7,#b8
    db #80  ; MUSIC_CMD_SKIP
    db 10,#09
    db 4,#40
    db #80  ; MUSIC_CMD_SKIP
    db 4,#60
    db #80  ; MUSIC_CMD_SKIP
    db 10,#07
    db 4,#80
    db #80  ; MUSIC_CMD_SKIP
    db 4,#a0
    db #80  ; MUSIC_CMD_SKIP
    db 10,#05
    db 4,#c0
    db #80  ; MUSIC_CMD_SKIP
    db 4,#e0
    db #80  ; MUSIC_CMD_SKIP
    db 10,#00         ;; channel 3 volume to silence
    db 7,#b8          ;; SFX all channels to tone
    db 0x89  ; SFX_CMD_END
