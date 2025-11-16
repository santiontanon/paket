    include "sound-constants.asm"

    db  7,#98    ;; noise in channel C, and tone in channels B and A
    db 10,#0b    ;; volume
    db 5, 2, 4, #fa ;; RE3
    db  6+MUSIC_CMD_TIME_STEP_FLAG,#18    ;; noise frequency
    ;db MUSIC_CMD_SKIP
    db 5, #03, 4, #20
    db  7+MUSIC_CMD_TIME_STEP_FLAG,#b8
    db 10,#09
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#40
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#60
    db 10,#07
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#80
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#a0
    db 10,#05
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#c0
    db 4+MUSIC_CMD_TIME_STEP_FLAG,#e0
    db 10,#00         ;; channel 3 volume to silence
    db 7,#b8          ;; SFX all channels to tone
    db SFX_CMD_END 
