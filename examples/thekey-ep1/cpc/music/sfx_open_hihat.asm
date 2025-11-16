    include "sound-constants.asm"

  db  7,#1c    ;; noise in channel C, and tone in channels B and A
  db 10,#0a    ;; volume
  db  6+MUSIC_CMD_TIME_STEP_FLAG,#01    ;; noise frequency
  db MUSIC_CMD_SKIP
  db 10+MUSIC_CMD_TIME_STEP_FLAG,#08    ;; volume
  db MUSIC_CMD_SKIP
  db 10+MUSIC_CMD_TIME_STEP_FLAG,#06    ;; volume
  db MUSIC_CMD_SKIP,MUSIC_CMD_SKIP,MUSIC_CMD_SKIP
  db MUSIC_CMD_SKIP,MUSIC_CMD_SKIP,MUSIC_CMD_SKIP,MUSIC_CMD_SKIP
  db  7,#38    ;; SFX all channels to tone
  db 10,#00         ;; channel 3 volume to silence
  db SFX_CMD_END  