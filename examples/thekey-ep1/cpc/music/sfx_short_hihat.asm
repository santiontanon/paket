    include "sound-constants.asm"

  db  7,#1c    ;; noise in channel C, and tone in channels B and A
  db 10,#05    ;; volume
  db  6+MUSIC_CMD_TIME_STEP_FLAG,#04    ;; noise frequency
  db 10+MUSIC_CMD_TIME_STEP_FLAG,#08    ;; volume
  db 10+MUSIC_CMD_TIME_STEP_FLAG,#0b    ;; volume
  db  7,#38    ;; SFX all channels to tone
  db 10,#00         ;; channel 3 volume to silence
  db SFX_CMD_END  