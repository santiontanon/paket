;------------------------------------------------------------------------
; PAKETDsk v1
; Amstrad CPC disk file load/save library for the PAKET engine
; Santiago Ontañón, 2024
;
; PAKETDSK is an assembler library to interface with the disk drive in Amstrad CPC computers,
; without using the firmware. It was created by starting from an AMSDOS disassemly, and extracting
; those functions that were necessary for loading and saving files to disk, and then annotating
; the whole source code to understand what each function did.
;
; All the original memory areas used by the firmware in fixed addresses have been moved to the
; "paketdsk-ram.asm" file, so that you can place them wherever it is more convenient for your
; application/game.
; 
; How to use:
; - include "paketdsk.asm" (this file), which contains all the assembler routines.
; - include "paketdsk-ram.asm" in your RAM variables area.
; - As soon as your program starts, and before the firmware memory areas have been overwriten,
;   disable interrupts, and call "paketdsk_setup".
; - After "paketdsk_setup" returns and before you enable interrupts again is a good time to
;   install your own custom interrupt routine.
; - In your custom interrupt, all you need to do is to call "paketdsk_interrupt_execute_tickers"
;   at each VSYNC. See "readfile.asm" or "dsktest.asm" for example custom interrupts.
; - After that, you can now just load and save files to disk by calling:
;   - paketdsk_load_file_from_disk
;   - paketdsk_save_file_to_disk
; - The assembler syntax being used is standard Zilog notation, so that this can be used in almost
;   any modern Z80 assembler. No local labels, nor fancy features of modern Z80 assemblers have been
;   used other than IF/ELSE/ENDIF statements, to maximize compatibility. If IF/ELSE/ENDIF statements
;   are not supported by your assembler, just comment the parts that you don't want out.
; - See paketdsk-internal.asm for implementation notes, and explanations about the memory structures
;   used to manage AMSDOS disk files.
;
; Version history:
; - Version v1: (space used: code: 3612, ram: 1244, total: 4856)
;   - removed address prefixes to all labels, since I started modifying methods to simplify, and
;     old addresses started loosing their meaning.
;   - paketdsk_check_if_drive_is_not_already_open_for_reading and
;     paketdsk_check_if_drive_is_not_already_open_for_writing, have been removed, as the only useful
;     thing they did was to call 'paketdsk_store_return_address_in_case_of_an_error'.
;   - removed 'paketdsk_normalize_file_name_into_FCB_and_clear_extent_info', so that it's easier to
;     see that paketdsk_cas_in_open and paketdsk_cas_out_open start similarly (and saves a few bytes)
;   - removed 'paketdsk_normalize_filename_or_error' as it was only used once.
;   - removed 'paketdsk_set_directory_buffer_ptr', which was just called once.
;   - removed the need to keep 'AMSDOS_RAM2_start' in iy, to simplify memory addressing, and save space.
;   - simplified the ticker mechanism, since we don't need all the list logic (there is only one ticker).
; - Version v0: (space used: code: 4124, ram: 1346, total: 5470)
;   - contains exactly the routines used by the AMSDOS firmware, with only the following modifications.
;   - create symbol names for all functions.
;   - add constant definitions for all the numerical constants.
;   - remove unnecessary code (including all the error messaging printing, as in PAKETDSK
;     funtions just set the carry flag for success and reset it for failure, like AMSDOS does,
;     but there are no printed messages to screen).
;   - all the memory areas have been moved to paketdsk-ram.asm, so that they can be placed anywhere.
;   - other than that, this file contains pretty much exactly the AMSDOS routines with little to no
;     modification.


;------------------------------------------------------------------------
; Flags to turn on/off different functionalities of PAKETDSK 
PAKETDSK_SUPPORT_FORMATTING: equ 0  ; adds 31 extra bytes
PAKETDSK_SUPPORT_IBM_FORMAT: equ 0  ; adds 17 extra bytes
PAKETDSK_SUPPORT_ENCRYPTED_BASIC: equ 0  ; adds 101 extra bytes


;------------------------------------------------------------------------
paketdsk_start_of_paketdsk:


;------------------------------------------------------------------------
; Addresses where things are when the firmware is active:
AMSDOS_RAM1_bios_start: equ #be40
AMSDOS_RAM1_bios_end: equ #be80
AMSDOS_RAM2_bios_start: equ #a700
AMSDOS_RAM2_bios_ptr: equ #be7d


;------------------------------------------------------------------------
; This method extracts all the info from the firmware, and puts it into the
; PAKETDSK equivalent memory areas. It should be called before the firmware areas
; have been overwritten, and with interrupts disabled.
; 
; Once this method returns, from taht point on, you can feel free to kick the
; firmware away, and use all the memory (it would also be a good idea to install
; your custom interrupt routine before enabling interrupts again).
; 
; input:
; - a: drive
paketdsk_setup:
    ld hl, (AMSDOS_RAM2_bios_ptr)  ; #a700
    ld (hl), a

    ; Init the disk memory areas:
    xor a
    ld (execute_ticker_flag), hl
    ld hl, AMSDOS_RAM1_bios_start + 4  ; + 4, since we are skipping some variables that were not used
    ld de, AMSDOS_RAM1_start
    ld bc, AMSDOS_RAM1_end - AMSDOS_RAM1_start
    ldir
    ld hl, (AMSDOS_RAM2_bios_ptr)
    ld de, AMSDOS_RAM2_start
    ld bc, 1200  ; amount of data we need to copy from the firmware
    ldir

    ; update ptrs:
    ld hl, AMSDOS_RAM2_start + #00e4
    ld (memory_sector_read_buffer_ptr), hl
    ld hl, AMSDOS_work_RAM_sector_buffer  ; + #02b0
    ld (memory_sector_buffer_ptr), hl
    ld (memory_sector_buffer_ptr_2), hl
    ld hl, AMSDOS_work_RAM_directory_record_buffer
    ld (AMSDOS_work_RAM_DPH_drive_a + 8), hl
    ld (AMSDOS_work_RAM_DPH_drive_b + 8), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_a
    ld (AMSDOS_work_RAM_DPH_drive_a + 10), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_b
    ld (AMSDOS_work_RAM_DPH_drive_b + 10), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_a + #0019  ; ptr to checksums (CSA)
    ld (AMSDOS_work_RAM_DPH_drive_a + 12), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_b + #0019  ; ptr to checksums (CSA)
    ld (AMSDOS_work_RAM_DPH_drive_b + 12), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_a + #0029  ; ptr to allocation table (ALT)
    ld (AMSDOS_work_RAM_DPH_drive_a + 14), hl
    ld hl, AMSDOS_work_RAM_XDPB_drive_b + #0029  ; ptr to allocation table (ALT)
    ld (AMSDOS_work_RAM_DPH_drive_b + 14), hl
    ret


;------------------------------------------------------------------------
; Loads a file from disk. The file is loaded to the address specified in 
; the file header.
; 
; input:
; - b: length of filename
; - hl: address of filename
; - iy: ptr to a default load address in case file has no header
; output:
; - carry: success
; - no carry: error
paketdsk_load_file_from_disk:
    ; We switch to mode 0, since during disk operations, sometimes interrupts
    ; are disabled for a while, and split mode cannot be sustained.
    push bc
    push hl
        call switch_to_mode0_safe
    pop hl
    pop bc
    ld a, (current_4000_page)
    ld c, a
    push bc
        ld a, PAKETDSK_PAGE
        SET_128K_PAGE_4000_A
        call paketdsk_load_file_from_disk_internal
    pop bc
    push af
        ld a, c
        SET_128K_PAGE_4000_A
        call switch_to_split_mode1_mode0
    pop af
    ret


paketdsk_load_file_from_disk_internal:
    push iy
    pop de
    call paketdsk_cas_in_open
    ret nc
    ex de, hl  ; load file to location stored in the file header
    call paketdsk_cas_in_direct
    ret nc
    jp paketdsk_cas_in_close


;------------------------------------------------------------------------
; Saves a file to disk
; 
; input:
; - b: length of filename
; - de: data size
; - hl: address of filename
; - ix: data to save
; output:
; - carry: success
; - no carry: error
paketdsk_save_file_to_disk:
    ; We switch to mode 0, since during disk operations, sometimes interrupts
    ; are disabled for a while, and split mode cannot be sustained.
    push bc
    push hl
        call switch_to_mode0_safe
    pop hl
    pop bc
    ld a, (current_4000_page)
    ld c, a
    push bc
        ld a, PAKETDSK_PAGE
        SET_128K_PAGE_4000_A  ; we do not need to restore the previous page, as this is 
                              ; always called from the interrupt, which already restores the page.
        call paketdsk_save_file_to_disk_internal
    pop bc
    push af
        ld a, c
        SET_128K_PAGE_4000_A
        call switch_to_split_mode1_mode0
    pop af
    ret

paketdsk_save_file_to_disk_internal:
    push ix
    push de
        call paketdsk_cas_out_open
    pop de
    pop hl
    ret nc  ; error
    ld b, h
    ld c, l
    ld a, 2  ; binary data
    call paketdsk_cas_out_direct
    ret nc
    jp paketdsk_cas_out_close    


;------------------------------------------------------------------------
; Execute tickers.
; - You should call this method in each VSYNC interrupt from your custom interrupt routine. 
;
; Ticker block is 4 bytes:
; - 2 bytes: countdown to excution
; - 2 bytes: count reset (value that the count will reset to after execution)
paketdsk_interrupt_execute_tickers:
    ld a, (execute_ticker_flag)
    or a
    ret z  ; if there are no tickers, return
    ld bc, (memory_ticker_block)  ; bc = count
    ld a, b
    or c
    ret z  ; ticker was already executed, skip
    dec bc
    ld a, b
    or c
    jr nz, paketdsk_interrupt_execute_tickers__update_count  ; count has not yet reached 0
    ; execute ticker!
    ld a, PAKETDSK_PAGE
    SET_128K_PAGE_4000_A  ; we do not need to restore the previous page, as this is 
                          ; always called from the interrupt, which already restores the page.
    call paketdsk_motor_ticker
    ld bc, (memory_ticker_block + 2)  ; bc = count reset
paketdsk_interrupt_execute_tickers__update_count:
    ld (memory_ticker_block), bc
    ret


;------------------------------------------------------------------------
; Internal functions that are not part of the public API
;    include "paketdsk-internal.asm"


paketdsk_end_of_paketdsk:
