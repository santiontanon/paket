;------------------------------------------------------------------------
; Amstrad CPC disk file load/save library for the PAKET engine
; Santiago Ontañón, 2024
;
; Internal functions that should probably not be called by the user of the PAKETDSK library.
; 
; Original disasembly, which was the starting point for this file, was obtained from here:
; - https://cpcrulez.fr/coding_src-list-disassembly_of_amsdos_rom.htm
;
; Additional information obtained from:
; - AMSDOS memory map: https://www.cpcwiki.eu/index.php/AMSDOS_Memory_Map
; - FDC: https://www.cpcwiki.eu/index.php/765_FDC#FDC_Status_Registers
; 
; Terms:
; - XDPB: eXtended Disk Parameter Block
;       - This is a set of configuration parameters that configure a drive (e.g. number of
;       records per track, fillbyte for formatting, etc.)
;       - Additionally, it also stores the current track the drive header is on, checksums for
;       the 16 directory records (to detect disk changes), a block allocation table (that indicates
;       the blocks used by the file in the current catalog) and a couple of flags to tell the code
;       whether it should recalibrate the drive, or detect the format automatically.
;       - There are two XDPBs stored in AMSDOS work RAM, one for drive A and one for drive B, each being
;       64 bytes in size.
; - DPH: Disk Parameter Header
;       - Complements the XDPB and contains informaiton such as current sector, current directory number,
;       or a pointer to the corresponding XDPB.
; - Directory Entry / Catalog:
;       - 32 bytes describing a file in disk (files larger than 16K, require multiple catalogs, as each can
;       only account for 16K).
;       - It stores the filename, user, filesize, and block IDs where the records of this file are in the disk.
;       - For the disk records that contain catalog entries, there are 4 entries per record (32 * 4 = 128 bytes).
;       - At most 8 records are devoted to catalogs in a file (2 blocks), so there can be at most 64 files in a
;       disk (or less, if any file is larger than 16K).
; - FCB: File Control Block:
;       - This is a 36 byte structure that contains a catalog + 1 prefix byte (drive) and 3 suffix bytes (num accessed records).
; - FDC: Floppy Disk Controller
;       - The hardware in the Amstrad CPC that controls the disk drive. We send commands to it in order to
;       interface with the drive.
; 
; Notes:
; - a floppy has 2 sides
; - each side has a series of tracks
; - each track has a number of records/sectors/blocks
; - record: 128 bytes
; - block: a group of records (1KB in size)
; - sector: smallest physically addressable unit in the disk (512 bytes in CPC).
; - "directory entry" == "catalog"?: 32 byte structure storing a file user, filename and where 16KB of data are.
;                                    Files larger than 16KB, need multiple catalogs.
; - There are no "directories" in a disk, it's just a flat list of files. The only grouping that can be done
;   is by "user" (with user "#e5" reserved for erased files).
; - The firmware used an interesting procedure to write files (which PAKETDSK respects):
;   - given a FILENAME.EXTENSION file we want to save
;   - It first looks if there are any FILENAME.$$$ files, and deletes them.
;   - It then writes the file we want, but named FILENAME.$$$
;   - Then, if there are any files called FILENAME.BAK, it deletes them
;   - If there are any FILENAME.EXTENSION existing, it renames them as FILENAME.BAK
;   - Finally, it renames FILENAME.$$$ to FILENAME.EXTENSION
; 
; Datastructures (info from https://www.cpcwiki.eu/index.php/AMSDOS_Memory_Map, with some corrections):
; 
; Extended Disk Parameter Block (XDPB, CSA, ALT) for Drive A/B:
;  #00  2 SPT   Records per Track (CPM/Data = 36, IBM = 32)
;  #02  1 BSH   Block Shift (3) (records per block = 1 SHL N)
;  #03  1 BLM   Block Mask  (7) (records per block minus 1)
;  #04  1 EXM   Extent Mask (0)
;  #05  2 DSM   Max Block Number  (CPM = 170, Data = 179, IBM = 155)
;  #07  2 DRM   Max Dir Entries-1 (63)
;  #09  2 AL    Directory Allocation Table (11000000b,00000000b = Block 0,1)
;  #0B  2 CKS   Checksum Area Size (Size of the "CSA" field) (#0010)
;  #0D  2 OFF   Track Offset (Size of Bootstrap) (CPM =2, Data = 0, IBM = 1)
;  #0F  1 FSC   First Sector on Track (CPM = #41, Data = #C1, IBM = #01)
;  #10  1 PST   Physical Sectors per Track (CPM/Data=9, IBM=8)
;  #11  1 GPS   GAP3 for Sector Read/Write (#2A)
;  #12  1 GPT   GAP3 for Track Formatting  (#52)
;  #13  1 FLB   Fillbyte for Track Formatting (#E5)
;  #14  1 BPS   Bytes per Sector (2=512) (#80 SHL N, Shift amount or so?)
;  #15  1 RPS   Records per Sector (4)
;  #16  1 Current Track
;  #17  1 Recalibrate Track 0 Flag
;  #18  1 Flag Login on any access ? (re-detect CPM/Data format?)
;  #19 16 CSA   Checksums (directory record chksums, for sensing disk-changes)
;  #29 23 ALT   Block Allocation Table (23x8bit = 184bit) (only 180bit used)
;                   - This table contains 180bits: each bit corresponds to a block.
;                   - If it's 1, block is used, if it's 0, block is not used.
;
; Disk Parameter Header (DPH) for Drive A/B:
;  #00  2 XLT    Skew Factor Translation (physical-to-logical sector) (not used)
;  #02  2 TRACK  Current Directory Number (should be Current Track, misused as DIRNUM by AMSDOS)
;  #04  2 SECTOR Current Sector
;  #06  2 DIRNUM Current Directory Number (unused)
;  #08  2 DIRBUF Pointer to Directory Buffer (unused in PAKETDsk, as it always holds the same value)
;  #0A  2 DPB    Pointer to XDPB Drive Param Block
;  #0C  2 CSV    Pointer to CSA Checksums
;  #0E  2 ALV    Pointer to ALT Allocation Table
;
; File Header for OPENIN/OPENOUT:
;  #00  1 Access Mode (1 = CHAR, 2 = DIRECT)
;  #01  2 Pointer to 2K work buffer (for OPENIN, it's used as the default load address for files that have no header)
;  #03  2 Pointer to current CHAR in 2K work buffer
;  #05 16 User Number and Filename (padded with 00h) (last 4 bytes unused)
;  #15  1 Block Number (whatever, can be zero)
;  #16  1 Last Block   (whatever, can be zero)
;  #17  1 File Type    (#00:  BASIC, #01: encrypted BASIC, #02: binary). I think file types that end in #6 correspond to "files without a header".
;  #18  2 Data Length  (whatever, can be zero)
;  #1A  2 Load Address in memory
;  #1C  1 First Block           FFh
;  #1D  2 Filesize (excluding the 80h-byte header)
;  #1F  2 Entrypoint (for executable binary files)
;  #21 36 Unused
;  #45  3 24bit Filepos in CHARs, or 16bit Filesize if non-ASCII file
;  #48  2 Checksum accross [05h..47h]
;
; Extended File Control Block (FCB) for OPENIN/OPENOUT:
;  #00  1 Drive Number (#00 = A, #01 = B, #ff = Not Open)
;  #01  1 User Number (from 0 to 255, USER 229, #E5, is for deleted files).
;  #02  8 Filename  (padded with #20)
;  #0a  3 Extension (padded with #20. Read Only flag on byte 9 bit 7. Hidden flag on byte 10 bit 7. Archive flag on byte 11 bit 7)
;  #0d  1 Extent Number (0 = First directory entry of file, there can be up to 128K bytes (8 logical extents) directly addressed by a single directory entry.)
;  #0e  2 Reserved for system use: if ([#0e] >> 4) != [#0f], paketdsk_get_extent_number_for_next_accessed_record throws an error. But I have always seen these set to 0.
;  #10  1 Number of Records in current Extent
;  #11 16 Block Numbers for current Extent
;  #21  3 Number of previously accessed Records
;
; Catalog structure (a.k.a. "directory entry") (info from https://www.cpcwiki.eu/index.php/765_FDC#The_15_FDC_Commands):
;  #00  1 User Number (from 0 to 255, USER 229, #E5, is for deleted files).
;  #01  8 Filename  (padded with #20)
;  #09  3 Extension (padded with #20. Read Only flag on byte 9 bit 7. Hidden flag on byte 10 bit 7. Archive flag on byte 11 bit 7)
;  #0c  1 Extent Number (0 = First directory entry of file, there can be up to 128K bytes (8 logical extents) directly addressed by a single directory entry.)
;  #0d  2 Reserved for system use (I have always seen these set to 0)
;  #0f  1 Number of Records in current Extent
;  #10 16 Block Numbers for current Extent


;------------------------------------------------------------------------
; XDPB parameter offsets:
AMSDOS_XDPB_offset_records_per_track: equ 0
AMSDOS_XDPB_offset_block_shift: equ 2
AMSDOS_XDPB_offset_block_mask: equ 3
AMSDOS_XDPB_offset_extent_mask: equ 4
AMSDOS_XDPB_offset_max_block_number: equ 5
AMSDOS_XDPB_offset_max_block_number_high: equ 6  ; high byte of AMSDOS_XDPB_offset_max_block_number
AMSDOS_XDPB_offset_last_valid_dir_entry_number: equ 7
AMSDOS_XDPB_offset_directory_allocation_table: equ 9  ; this is a bitmask, indicating which blocks contain the directory in the disk. It's used in 'paketdsk_clear_DPH_block_allocation_table'.
AMSDOS_XDPB_offset_checksum_area_size: equ 11
AMSDOS_XDPB_offset_track_offset: equ 13 
AMSDOS_XDPB_offset_first_sector_on_track: equ 15
AMSDOS_XDPB_offset_formatting_fillbyte: equ #13
AMSDOS_XDPB_offset_records_per_sector: equ #15
AMSDOS_XDPB_offset_current_track: equ #16
AMSDOS_XDPB_offset_calibrated_flag: equ #17
AMSDOS_XDPB_offset_detect_format_flag: equ #18


;------------------------------------------------------------------------
; DPH parameter offsets:
AMSDOS_DPH_offset_skew_factor_translation: equ 0
AMSDOS_DPH_offset_current_directory_number: equ 2  ; should be current track, but AMSDOS misuses it
AMSDOS_DPH_offset_current_sector: equ 4
AMSDOS_DPH_offset_current_directory_number_unused: equ 6
AMSDOS_DPH_offset_directory_buffer_ptr: equ 8  ; points to "AMSDOS_work_RAM_directory_record_buffer"
AMSDOS_DPH_offset_xdpb_ptr: equ 10
AMSDOS_DPH_offset_checksums_ptr: equ 12
AMSDOS_DPH_offset_allocation_table_ptr: equ 14


;------------------------------------------------------------------------
; Extended File Control Block (FCB) for OPENIN/OPENOUT offsets:
AMSDOS_FCB_offset_extent_number: equ #0d
AMSDOS_FCB_offset_num_records_in_current_extent: equ #10
AMSDOS_FCB_offset_num_previously_accessed_records: equ #21


;------------------------------------------------------------------------
; File Header for OPENIN/OPENOUT
AMSDOS_FILEHEADER_offset_2k_buffer_ptr: equ #01
AMSDOS_FILEHEADER_offset_current_byte_in_2k_buffer_ptr: equ #03
AMSDOS_FILEHEADER_offset_filename: equ #05
AMSDOS_FILEHEADER_offset_file_type: equ #17  ; (#00: BASIC, #01: encrypted BASIC, #02: ninary)
AMSDOS_FILEHEADER_offset_data_length: equ #18
AMSDOS_FILEHEADER_offset_load_address: equ #1a
AMSDOS_FILEHEADER_offset_first_block: equ #1c
AMSDOS_FILEHEADER_offset_file_size_1: equ #1d
AMSDOS_FILEHEADER_offset_execution_entry_point: equ #1f
AMSDOS_FILEHEADER_offset_file_size_2: equ #45  ; filesize in bytes is stored twice for some reason in the header (see: https://www.cpcwiki.eu/index.php/AMSDOS_Memory_Map#File_Header_for_OPENIN.2FOPENOUT)


;------------------------------------------------------------------------
; This method is useful so that we can do a "call (hl)"
paketdsk_jp_hl:
    jp (hl)


;------------------------------------------------------------------------
; setup XDPB and get address of drive's DPH (Disk Parameter Header)
;
; input:
; - c: drive index
; - e: #ff if the drive matches that of openin/openout control blocks. And 0 if it does not.
; output:
; - hl: DPH ptr
; - carry clear: error
; - carry set: hl = address of drive's XDPB
; - XDPB is updated to reflect format
paketdsk_setup_XDPB:
    ld a, c
    cp 2
    ld hl, 0
    ret nc  ; quit if drive index is >=2
    ; drive is 0 or 1
    ld a, e
    rra  ; transfer bit 0 into carry
    jr c, paketdsk_setup_XDPB__format_detected  ; force

    ld e, c  ; e = drive
    ld a, AMSDOS_XDPB_offset_detect_format_flag
    call paketdsk_get_xdpb_parameter_value  ; get XDPB parameter by index
    or a  ; test detect format flag
    jr nz, paketdsk_setup_XDPB__format_detected  ; != 0: do not detect format     
    ; detect format using "read id"
    push hl
        call paketdsk_detect_format_from_disk_and_setup_xdpb  ; detect format on disk and setup XDPB
    pop hl
    ret nc

paketdsk_setup_XDPB__format_detected:
    ld a, c
    ld (memory_drive), a

    ; get offset of DPH in AMSDOS work ram
    ld hl, AMSDOS_work_RAM_DPH_drive_a
    or a
    ret z
    ld hl, AMSDOS_work_RAM_DPH_drive_b
    ret


;------------------------------------------------------------------------
; Writes the current sector, and then sets 'memory_track' to 0
paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track:
    call paketdsk_flush_record_buffer_to_disk_if_necessary
    ld a, 0
    ld (memory_track), a
    ret     


;------------------------------------------------------------------------
; Inits drive params 3 (if c == 2)
; Checks there are records left (and drive params 1 match drive params 3)
; if there are records left ahd match, Increments sector/track.
; Reads sector if needed
; copy read buffer to current record buffer
; flushes directory buffer if necessary (if c == 1)
;
; input:
; - c: if c == 2, it inits the drive params 3
;      if c == 1, it skips drive params 3 init, but flushes directory buffer if necessary
;      if c == 0, neither is done
paketdsk_inc_sector_track_and_flush_record_buffer:
	push bc
    	ld a, c
    	cp 2
    	call z, paketdsk_init_n_records_left_and_drive_params_3
    	call paketdsk_check_there_are_records_left_and_drive_params_3_match_1
    	call c, paketdsk_inc_record_sector_and_track_3  ; preserves carry
    	call paketdsk_read_sector_if_not_done_before  ; if "carry" just flush directory, if "no carry", read if not done before.
	pop bc
	ret nc

	call paketdsk_copy_read_buffer_to_current_record_buffer
	dec c
	scf
	call z, paketdsk_flush_record_buffer_to_disk_if_necessary
	ret nc
	ld a, 0
	ret     


;--------------------------------------------------------------------------
paketdsk_read_sector_and_copy_to_buffer:
    xor a
    ld (memory_n_records_left), a
    call paketdsk_read_sector_if_not_done_before
    call paketdsk_copy_current_record_to_read_buffer
    ret nc
    ld a, 0
    ret     


;------------------------------------------------------------------------
paketdsk_FDC_execute_getid_command:
    ld bc, #fb7e  ; bc = I/O address of FDC main status register
    ld a, #4a  ; read id
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, e  ; drive 
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    jp paketdsk_get_FDC_response_and_restore_stack_if_error


;------------------------------------------------------------------------
; Detect format on disk and setup XDPB
; To do this, this method:
; - spins up the drive if it was not on
; - send the "read id" command to the FDC on the current track
; - if successful, we can read the format from the "result phase data" returned by the FDC
;
; input:
; - e: drive
paketdsk_detect_format_from_disk_and_setup_xdpb:
    call paketdsk_spin_disk_motor_up_if_off  ; spin up drive motor
    ld a, AMSDOS_XDPB_offset_current_track
    call paketdsk_get_xdpb_parameter_value
    ld d, a  ; d = current track
    ld c, #10
    ld hl, paketdsk_FDC_execute_getid_command  ; read id function
    call paketdsk_execute_function_with_retry  ; execute function with retry
    ret nc
    ; "read id" succeeded, we can get the format from the FDC response.
    ld a, (memory_FDC_response + 5)  ; R from result phase data
    ; jr paketdsk_set_xdpb_format


;------------------------------------------------------------------------
; Initializes the XDPB with the given format
;
; input:
; - a: id of format
;   #41 (SYSTEM/VENDOR)
;   #c1 (DATA)
;   #01 (IBM)
; - - e: drive (e == 1 means drive 1, e != 1, means drive 0)
paketdsk_set_xdpb_format:
    push af
        ; copy the XDPB values for the SYSTEM format to the current format pointer:
        xor a  ; XDPB: SPT
        call paketdsk_get_xdpb_parameter_address ; get address of XDPB parameter
        push hl
            ex de, hl
            ld hl, system_format_xdpb_values  ; full XPDB (setup for SYSTEM format)
            ld bc, #16
            ldir
        pop hl
    pop af
    and #c0
    cp #40  ; if we wanted system format, then we are done
    scf     
    ret z
    
    ld de, data_format_spec  ; definition for DATA format
IF PAKETDSK_SUPPORT_IBM_FORMAT = 1
    cp #c0  ; DATA FORMAT
    jr z, paketdsk_set_xdpb_format__format_set

    ld de, ibm_format_spec ; definition for IBM format
paketdsk_set_xdpb_format__format_set:
ENDIF
    ; Overwrite XDPB config with the DATA/IBM overrides:
    ld a, (de)
    inc de
    ld (hl), a  ; records per track (low)
    inc hl
    ld a, (de)
    inc de
    ld (hl), a  ; records per track (high)
    ld bc, 4
    add hl, bc
    ld a, (de)
    inc de
    ld (hl), a  ; number of blocks (high)
    inc hl
    ld a, (de)
    inc de
    ld (hl), a  ; number of blocks (high)
    ld bc, 7
    add hl, bc
    ex de, hl
    ld bc, 6
    ldir  ; rest of the config
    scf     
    ret     


;------------------------------------------------------------------------
; initial value for the XDPB for using the SYSTEM format:
system_format_xdpb_values:
    dw #0024  ; records_per_Track
    db #03  ; block shift
    db #07  ; block mask
    db #00  ; extent mask
    dw #00aa  ; max block number
    dw #003f  ; last valid dir entry number
    dw #00c0  ; directory allocation table
    dw #0010  ; checksum area size
    dw #0002  ; track offset
    db #41  ; first sector on track
    db #09  ; physical sectors per track
    db #2a  ; GAP3 for Sector Read/Write
    db #52  ; GAP3 for Track Formatting
    db #e5  ; Fillbyte for Track Formatting
    db #02  ; Bytes per Sector (2 = 512)
    db #04  ; Records per Sector
    db #00  ; Current Track
    db #00  ; calibrated flag
    db #00  ; detect format flag


;------------------------------------------------------------------------
; Format overrides:
IF PAKETDSK_SUPPORT_IBM_FORMAT = 1
; IBM format
ibm_format_spec:
    dw #0020, #009b, #0001  ; records per track, number of blocks, track offset
    db #01, #08, #2a, #50  ; first sector id, sectors per track, GAP3 for Sector Read/Write, GAP3 for Track Formatting
ENDIF
; DATA format
data_format_spec:
    dw #0024, #00b3, #0000  ; records per track, number of blocks, track offset
    db #c1, #09, #2a, #52  ; first sector id, sectors per track, GAP3 for Sector Read/Write, GAP3 for Track Formatting


;-----------------------------------------------------------------------
; Gets the drive/track/sector id/sector ptr from 'memory_drive_2', and
; then calls the BIOS function to write a sector.
paketdsk_flush_record_buffer_to_disk_if_necessary:
    ld hl, memory_read_write_sector_flag
    ld (hl), 0  ; init to 0
    dec hl  ; hl = memory_directory_record_buffer_can_be_flushed_flag
    ld a, (hl)  
    or a  ; do we need to flush the directory buffer?
    scf  ; set carry
    ret z  ; return if we do not
    
    inc (hl)
    call paketdsk_get_drive_track_sector_and_buffer_from_copy_2  ; generate sector ID, drive, track and address of sector buffer
    ; jp paketdsk_bios_write_sector


;------------------------------------------------------------------------
; BIOS: WRITE SECTOR
;
; input:
; - e: drive
; - d: track
; - c: sector id
; - hl: ptr of the data to write (will be stored in "memory_sector_buffer_ptr_2")
paketdsk_bios_write_sector:
    ld a, #45 ; write data
IF PAKETDSK_SUPPORT_FORMATTING = 1
    jr paketdsk_bios_format_sector__entry_point
ENDIF


;------------------------------------------------------------------------
; BIOS: FORMAT TRACK
; 
; input:
; - hl: ptr of the data to write (will be stored in "memory_sector_buffer_ptr_2")
; - e: drive & side
; - d: track
; - c: sector id
IF PAKETDSK_SUPPORT_FORMATTING = 1
paketdsk_bios_format_sector:
    ld a, #4d  ; "format track" command (mfm)
paketdsk_bios_format_sector__entry_point:
ENDIF
    call paketdsk_spin_disk_motor_up_if_off
    ld b, #11
    call paketdsk_bios_read_sector__entry_point
    ld a, (memory_formatting_delay)
paketdsk_bios_format_sector__wait_loop:
    dec a
    inc bc
    inc bc
    inc bc
    jr nz, paketdsk_bios_format_sector__wait_loop
    ret


;------------------------------------------------------------------------
; BIOS: READ SECTOR
;
; input:
; - hl: buffer
; - e: drive
; - d: track
; - c: sector id
;
; NOTES:
; - H parameter is forced to 0
; - N parameter comes from XDPB
; - R parameter defined by user
; - only 1 sector read at a time
; - C parameter defined by user (must be valid track number)
; - double density only
; - "read data" only + skip
paketdsk_bios_read_sector:
    call paketdsk_spin_disk_motor_up_if_off
    ld a, #66  ; read data (mfm, skip)
    ld b, #10
paketdsk_bios_read_sector__entry_point:
    ld (memory_sector_buffer_ptr), hl
    ld h, a  ; fdc command code
    ld l, c  ; R = sector id
    ld (memory_desired_track_sector_number), hl
    ld c, b
    ld hl, paketdsk_execute_read_write_or_format  ; execute "read data", "write data" or "format track" command
    jp paketdsk_execute_function_with_retry  ; execute function with retry


;------------------------------------------------------------------------
; execute "read data", "write data" or "format track" command
;
; input:
; - e: drive and side
; - d: C parameter (a valid track number)
paketdsk_execute_read_write_or_format:
    ld hl, (memory_desired_track_sector_number)  ; l = R parameter, h = FDC command code
    ld bc, #fb7e  ; BC = I/O address for FDC main status register
    ld a, h  ; fdc command code
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, e  ; drive and side
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
IF PAKETDSK_SUPPORT_FORMATTING = 1
    ld a, h
    cp #4d  ; "format track"  command?
    jr nz, paketdsk_execute_read_or_write  ; "read data" or "write data"
    ; "format track" command
    ld a, #14  ; N parameter
    call paketdsk_send_FDC_xdpb_parameter  ; write XDPB parameter to FDC
    ld a, #10  ; SC parameter
    call paketdsk_send_FDC_xdpb_parameter  ; write XDPB parameter to FDC
    ld a, #12  ; GPL parameter
    call paketdsk_send_FDC_xdpb_parameter  ; write XDPB parameter to FDC
    ld a, AMSDOS_XDPB_offset_formatting_fillbyte  ; D parameter
    call paketdsk_get_xdpb_parameter_value  ; get XDPB parameter by index
    jr paketdsk_execute_read_or_write__continue            
ENDIF


;------------------------------------------------------------------
; Executes either "read data" or "write data" commands.
;
; input:
; - d: C parameter
; - l: R parameter
paketdsk_execute_read_or_write:
    ; "read data" or "write data" command
    ld a, d  ; C parameter
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    xor a  ; H parameter
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, l  ; R parameter
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, #14  ; N parameter
    call paketdsk_send_FDC_xdpb_parameter  ; write XDPB parameter to FDC
    ld a, l  ; EOT parameter
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, #11  ; GPL parameter
    call paketdsk_send_FDC_xdpb_parameter  ; write XDPB parameter to FDC
    ld a, #ff  ; DTL parameter
paketdsk_execute_read_or_write__continue:
    call paketdsk_send_last_FDC_command_byte_and_transfer_data ; send last byte of command and transfer execution data

    ei      
    call paketdsk_get_FDC_response_and_restore_stack_if_error_from_fcd_response
    ret c
    ret nz

    ld a, (memory_FDC_response + 1)  ; FDG status reguster 1
    add a, a
    ret c  ; return if "end-of-track" bit is set (in this case, errors from read/write are ok)
    xor a  ; nc: error!
    ret


;------------------------------------------------------------------------
; send last byte of command and transfer execution data
;
; input:
; - a: command byte
; - h: first command byte (contains FDC command code)
paketdsk_send_last_FDC_command_byte_and_transfer_data:
    di  ; disable interrupts (prevents overrun condition)
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    ld a, h  ; get FDC command code
    ld hl, (memory_sector_buffer_ptr)  ; address of buffer to transfer data to/from 
    cp #66  ; write data command?
    jr nz, paketdsk_execution_phase_write_data__wait  ; fdc: write data in execution phase
    jr paketdsk_execution_phase_read_data__wait  ; fdc: read data in execution phase


;------------------------------------------------------------------------
; fdc: read data in execution phase
; quits if data is ready and execution phase has ended
;
; input:
; - hl: ptr to write to
; - bc: I/O address of FDC main status register
paketdsk_execution_phase_read_data:
    inc c  ; BC = I/O address for FDC data register
    in a, (c)  ; read from FDC data register
    ld (hl), a  ; write to RAM
    dec c  ; BC = I/O address for FDC main status register
    inc hl  ; increment RAM pointer
paketdsk_execution_phase_read_data__wait:
    ; start here
    in a, (c)  ; read FDC main status register
    jp p, paketdsk_execution_phase_read_data__wait  ; data ready?
    and #20  ; execution phase active?
    jr nz, paketdsk_execution_phase_read_data  ; go to transfer byte
    ; execution phase over
    ret     


;------------------------------------------------------------------------
; fdc: write data in execution phase
; quits if data is ready and execution phase has ended
;
; input:
; - hl: ptr to read from
; - bc: I/O address of FDC main status register
paketdsk_execution_phase_write_data:
    inc c  ; BC = I/O address for FDC data register
    ld a, (hl)  ; read from RAM
    out (c), a  ; write to FDC data register
    dec c  ; BC = I/O address for FDC main status register
    inc hl  ; increment RAM pointer
paketdsk_execution_phase_write_data__wait:
    ; start here
    in a, (c)  ; read main status register
    jp p, paketdsk_execution_phase_write_data__wait  ; data ready?
    and #20  ; execution phase active?
    jr nz, paketdsk_execution_phase_write_data  ; go to transfer byte
    ; execution phase over
    ret     


;------------------------------------------------------------------------
; execute function with retry
;
; input:
; - hl: function to execute
; - e: drive & side
; - d: track
; - c: message code if error
paketdsk_execute_function_with_retry:
    ld a, (memory_retry_count)  ; retry count
    ld b, a
paketdsk_execute_function_with_retry__loop:
    call paketdsk_move_to_track_and_try_command
    ret c  ; return is successful
    ret z
    ld a, b
    and #04  ; every 16 retries, we recalibrate:
    jr z, paketdsk_execute_function_with_retry__reset_recalibrate_flag

    push de
        ld d, 39
        call paketdsk_bios_move_to_track__motor_already_on  ; move to track 39
    pop de
    jr paketdsk_execute_function_with_retry__loop  ; try command again
paketdsk_execute_function_with_retry__reset_recalibrate_flag:
    push hl
        ; reset the "recalibrated" flag, so that the drive gets recalibrated before the next retry:
        ld a, AMSDOS_XDPB_offset_calibrated_flag
        call paketdsk_get_xdpb_parameter_address
        ld (hl), 0
    pop hl
    jr paketdsk_execute_function_with_retry__loop  ; try command again


;-------------------------------------------------------------
; Moves to the desired track and tries to execute a command.
; if failure will step up to higher or lower track and try the command again.
;
; input:
; - b: retry count
; - d: current track
; - There might other arguments as required by the command to tyy.
paketdsk_move_to_track_and_try_command:
    call paketdsk_move_to_track_and_execute_function  ; move to track, and execute function
    ret c
    ret z
    
    call paketdsk_clear_FDC_interrupt  ; clear fdc interrupt

    ; try command again
    call paketdsk_move_to_track_and_execute_function  ; move to track and execute function
    ret c
    ret z

    ; attempt step to higher track...
    ld a, d  ; get current track
    cp 39  ; CPC single sided disks have 40 tracks, so 39 is the last track
    dec b  ; preserves carry flag
    jr nc, paketdsk_move_to_track_and_try_command__in_lower_track  ; we are already at track 39, move to a lower track and then try again
    inc b
    ; if not at track 39, do step to higher track 
    inc d
    call paketdsk_bios_move_to_track__motor_already_on  ; move to track
    dec d

    ; try command again
    call paketdsk_move_to_track_and_execute_function  ; move to track and execute function
    ret c
paketdsk_move_to_track_and_try_command__in_lower_track:
    ret z  ; if we were at track 39, or the try to a higher track failed, return
    ld a, d  ; get track number
    or a  ; are we in track 0?
    jr nz, paketdsk_move_to_track_and_try_command__in_lower_track_continue  ; if not at track zero, do step to lower track and try command again

    ; at track zero; can't step to lower track
    dec b ; decrement retry count
    ret     

paketdsk_move_to_track_and_try_command__in_lower_track_continue:
    ; do step to lower track
    dec d
    call paketdsk_bios_move_to_track__motor_already_on  ; move to track
    inc d
    ; jr paketdsk_move_to_track_and_execute_function


;-------------------------------------------------------------
; Moves to a given track ('d'), and executes a function ('hl'). If it fails, it decrements the retry count in 'b'.
;
; input:
; - hl: function to execute
; - d: track
; - e: drive & side
; - b: retry count
; output:
; - carry set - function executed with no errors
; - carry clear & zero clear - try again
; - carry clear & zero set - decrement retry count
paketdsk_move_to_track_and_execute_function:
    call paketdsk_bios_move_to_track__motor_already_on  ; move to track
    push hl
    push bc
        call paketdsk_jp_hl
    pop bc
    pop hl
    ret c
    jr nz, paketdsk_move_to_track_and_execute_function         
    dec b  ; decrement the number of retries we have left
    ret


;------------------------------------------------------------------------
; BIOS: MOVE TRACK
;
; input:
; - e: drive
; - d: track
; output:
; - carry flag set if successful
paketdsk_bios_move_to_track:
    call paketdsk_spin_disk_motor_up_if_off
paketdsk_bios_move_to_track__motor_already_on:
    push hl
    push de
    push bc
        ld a, (memory_retry_count)  ; retry count
        ld b, a
        ld a, AMSDOS_XDPB_offset_calibrated_flag
        call paketdsk_get_xdpb_parameter_address  ; get address of XDPB parameter
        ld a, (hl)
        or a
        jr nz, paketdsk_bios_move_to_track__send_request
paketdsk_bios_move_to_track__recalibrate:
        ; drive is not aligned, send the recalibrate command, before asking it to move to the desired track:
        push bc
            ld bc, #fb7e ; BC = I/O address of FDC main status register
            ld a, #07 ; recalibrate command
            call paketdsk_send_FDC_command_byte  ; fdc: send command byte
            ld a, e
            call paketdsk_send_FDC_command_byte  ; fdc: send command byte
            ld a, #28
            call paketdsk_wait_and_send_interrupt_status_command
            jr nc, paketdsk_bios_move_to_track__failure
            ld a, #16  ; XDPB: current track
            call paketdsk_get_xdpb_parameter_address ; get address of XDPB parameter
            ld (hl), 0
            inc hl
            ld (hl), #ff
        pop bc
paketdsk_bios_move_to_track__send_request:
        dec hl
        ld a, (hl)
        sub d  ; are we already in the desired track?
        jr z, paketdsk_bios_move_to_track__success
        ; Ask the drive to move to the desired track:
        push bc
            ld bc, #fb7e  ; BC = I/O address of FDC main status register
            ld a, #0f  ; seek command
            call paketdsk_send_FDC_command_byte  ; fdc: send command byte
            ld a, e
            call paketdsk_send_FDC_command_byte  ; fdc: send command byte
            ld a, d
            call paketdsk_send_FDC_command_byte  ; fdc: send command byte
            ; calculate how many tracks we need to move (to get how much we need to wait)
            sub (hl)
            jr nc, paketdsk_bios_move_to_track__delay_computed
            ld a, (hl)
            sub d
paketdsk_bios_move_to_track__delay_computed:
            ; here 'a' contains how much do we need to wait (how many tracks we need to move)
            ld (hl), d
            call paketdsk_wait_and_send_interrupt_status_command
paketdsk_bios_move_to_track__failure:
        pop bc
        jr c, paketdsk_bios_move_to_track__success
        jr nz, paketdsk_bios_move_to_track__recalibrate
        dec b  ; failed, decrease the retey count
        jp z, paketdsk_restore_stack_stop_motor_and_get_error_if_any  ; we are out of retries
        call paketdsk_clear_FDC_interrupt  ; clear fdc interrupt
        jr paketdsk_bios_move_to_track__recalibrate
paketdsk_bios_move_to_track__success:
    pop bc
    pop de
    pop hl
    scf
    ret


;------------------------------------------------------------------------
; Waits some amount of time (controlled by "a"), and then sends an "interrupt status command".
;
; input:
; - a: number of times to wait for a track_settle_delay.
paketdsk_wait_and_send_interrupt_status_command:
    push af
        ld a, (memory_track_settle_delay)
        call paketdsk_delay_in_milliseconds  ; delay
    pop af
    dec a
    jr nz, paketdsk_wait_and_send_interrupt_status_command

    ld a, (memory_delay)
    call paketdsk_delay_in_milliseconds
    ld a, #08  ; sense interrupt status command
    call paketdsk_send_FDC_command_byte  ; fdc: send command byte
    jp paketdsk_get_FDC_response_and_restore_stack_if_error


;------------------------------------------------------------------------
; delay in milliseconds
;
; input:
; - a: amount of delay (in milliseconds)
paketdsk_delay_in_milliseconds:
paketdsk_delay_in_milliseconds__loop:
    push af
        ld a, #f6
paketdsk_delay_in_milliseconds__loop2:
        dec a
        jr nz, paketdsk_delay_in_milliseconds__loop2
    pop af
    dec a
    jr nz, paketdsk_delay_in_milliseconds__loop
    ret     


;------------------------------------------------------------------------
; Initializes (memory_n_records_left) to the number of records per block,
; and copies drive/track/record from memory_drive to memory_drive_3
paketdsk_init_n_records_left_and_drive_params_3:
	ld hl, memory_drive
	ld e, (hl)  ; get current drive
	ld a, AMSDOS_XDPB_offset_block_mask  ; (block mask == number of records per block - 1)
	call paketdsk_get_xdpb_parameter_value
	inc a  ; a = number of records per block
	ld de, memory_n_records_left
	ld (de), a
	inc de  ; de = memory_drive_3
	ld bc, 3
	ldir  ; copy drive, track, sector to 'memory_drive_3'
	ret


;-------------------------------------------------------------
; output:
; - carry: memory_n_records_left > 0 and
;          memory_drive drive/track/record match with memory_drive_3
; - no carry: if memory_n_records_left == 0 or there is a difference.
;             in this case memory_n_records_left is reset to 0.
paketdsk_check_there_are_records_left_and_drive_params_3_match_1:
	ld de, memory_n_records_left
	ld a, (de)
	or a
	ret z
    ; check that drive/track/sector in 'memory_drive'
    ; are the same as in 'memory_drive_3':
	inc de  ; de = memory_drive_3
	ld hl, memory_drive
	ld b, 3
paketdsk_check_there_are_records_left_and_drive_params_3_match_1__loop:
	ld a, (de)
	xor (hl)
	jr nz, paketdsk_check_there_are_records_left_and_drive_params_3_match_1__difference  ; jump if there is a difference
	inc de
	inc hl
	djnz paketdsk_check_there_are_records_left_and_drive_params_3_match_1__loop
	scf     
	ret
paketdsk_check_there_are_records_left_and_drive_params_3_match_1__difference:
	xor a
	ld (memory_n_records_left), a
	ret     


;-------------------------------------------------------------
; Decrements the number of records left,
; increments the current sector, and updates current track if necessary
paketdsk_inc_record_sector_and_track_3:
	push af
    	ld hl, memory_n_records_left
    	dec (hl)
    	inc hl  ; hl = memory_drive_3
    	ld e, (hl)
    	inc hl
    	inc hl  ; hl = memory_sector_3
    	inc (hl)  ; increment the current sector
    	xor a  ; a = AMSDOS_XDPB_offset_records_per_track
    	call paketdsk_get_xdpb_parameter_value  ; get XDPB parameter by index
    	cp (hl)  ; check if we have reached past the last sector
    	jr nz, paketdsk_inc_record_sector_and_track_3__track_set
    	ld (hl), 0  ; reset sector count
    	dec hl  ; hl = memory_track_3
    	inc (hl) ; increment current track
paketdsk_inc_record_sector_and_track_3__track_set:
	pop af
	ret     


;-------------------------------------------------------------
; This method:
; - checks if we have already read/written this sector
; - if we have, we are done
; - otherwise, it flushes the directory buffer and reads it (depending on "carry", the final read might be skipped)
;
; input:
; - carry: skip reading (just flush directory, and update read_write flag)
; - no carry: reading 
paketdsk_read_sector_if_not_done_before:
    push af
        call paketdsk_check_if_we_have_already_read_this_sector_from_copy_2
        jr c, paketdsk_read_sector_if_not_done_before__sector_read_or_written  ; if we have, we are done
        call paketdsk_flush_record_buffer_to_disk_if_necessary
    pop bc
    ret nc  ; if there was an error flushing the directory buffer, return

    push bc
        call paketdsk_copy_drive_track_record_to_copy_2
    pop af
    jr c, paketdsk_read_sector_if_not_done_before__sector_read

    call paketdsk_get_drive_track_sector_and_buffer_from_copy_2
    call paketdsk_bios_read_sector
paketdsk_read_sector_if_not_done_before__sector_read:
    push af
        sbc a, a
        ld (memory_read_write_sector_flag), a  ; set to #ff upon a successful read
    pop af
    ret

paketdsk_read_sector_if_not_done_before__sector_read_or_written:
    pop af
    scf     
    ret


;-----------------------------------------------------------------------
; This method checks if we had already read a sector and that the values in (memory_drive)
; match those in (memory_drive_2), for drive, track and sector/record.
; output:
; - c: we had read it before (match)
; - no carry, z: we have not read it yet
; - no carry, nz: we read it, but it was on a different drive/track/sector
paketdsk_check_if_we_have_already_read_this_sector_from_copy_2:
    ld a, (memory_read_write_sector_flag)
    or a
    ret z  ; we have not read it yet, return

    ld bc, memory_drive
    ld hl, memory_drive_2
    ld e, (hl)
    ld a, (bc)
    xor (hl)  ; check if (memory_drive) == (memory_drive_2)
    ret nz  ; no match!

    inc bc
    inc hl
    ld a, (bc)  ; check if (memory_track) == (memory_track_2)
    xor (hl)
    ret nz  ; no match!

    ; since (memory_sector_2) is in sectors, and (memory_record) is in records, we need to translate first:
    call paketdsk_translate_records_2_to_sectors
    xor (hl)  ; check it mactches with the position in sectors in (memory_sector_2)
    ret nz  ; no match!
    scf  ; all matches, set carry flag to indicate we had already read this sector before
    ret     


;-----------------------------------------------------------------------
; Copies the drive/track/record from (memory_drive) to (memory_drive_2),
; translating from records to sectors (since 'memory_drive_2' stores position in sectors).
paketdsk_copy_drive_track_record_to_copy_2:
    ld bc, memory_drive
    ld hl, memory_drive_2
    ld a, (bc)
    ld (hl), a
    ld e, a
    inc hl
    inc bc
    ld a, (bc)
    ld (hl), a
    call paketdsk_translate_records_2_to_sectors
    ld (hl), a
    ret   


;-----------------------------------------------------------------------
; Gets the current value of memory_record, and assuming it is in "records",
; translates it to "sectors". 
;
; input:
; - bc: ptr to position in records
; - hl: ptr to position in sectors
; output:
; - bc/hl incremented
; - a: position in (bc + 1) translated to sectors
paketdsk_translate_records_2_to_sectors:
    inc bc  ; point to record
    inc hl  ; point to sector
    ld a, AMSDOS_XDPB_offset_records_per_sector
    call paketdsk_get_xdpb_parameter_value
    ld d, a  ; d = records per sector
    ld a, (bc)  ; a = record for drive 1
paketdsk_translate_records_2_to_sectors__loop:
    srl d
    ret c  ; when we return, a will be the record divided by the number of records per sector
    srl a
    jr paketdsk_translate_records_2_to_sectors__loop


;------------------------------------------------------------------------
; Get sector ID, drive, track and address of sector buffer
;
; output:
; - c: sector ID
; - e: drive
; - d: track
; - hl: ptr to sector buffer
paketdsk_get_drive_track_sector_and_buffer_from_copy_2:
    ld de, (memory_drive_2)  ; e = drive, d = track
    ld a, AMSDOS_XDPB_offset_first_sector_on_track    
    call paketdsk_get_xdpb_parameter_value  ; get XDPB parameter by index
    ld hl, memory_sector_2  ; current sector index
    add a, (hl)  ; add first sector ID value
    ld c, a  ; C = final sector ID
    ld hl, AMSDOS_work_RAM_sector_buffer
    ret


;------------------------------------------------------------------------
; Also sets (memory_directory_record_buffer_can_be_flushed_flag) to #ff
paketdsk_copy_read_buffer_to_current_record_buffer:
	push hl
	push de
	push bc
	push af
    	ld a, #ff
    	ld (memory_directory_record_buffer_can_be_flushed_flag), a  ; mark that we can flush the record buffer to disk
    	call paketdsk_get_record_and_read_buffer_ptrs
    	ldir  ; copy from read buffer to record buffer
    	jr paketdsk_copy_current_record_to_read_buffer__done


;-----------------------------------------------------------------------
paketdsk_copy_current_record_to_read_buffer:
    push hl
    push de
    push bc
    push af
        call paketdsk_get_record_and_read_buffer_ptrs
        ex de, hl
        ldir  ; copy from record buffer to directory buffer
paketdsk_copy_current_record_to_read_buffer__done:
    pop af
    pop bc
    pop de
    pop hl
    ret     


;-----------------------------------------------------------------------
; output:
; - de: ptr to record inside sector buffer
; - hl: read buffer ptr
; - bc: record size
paketdsk_get_record_and_read_buffer_ptrs:
    ld hl, memory_drive
    ld e, (hl)  ; drive
    ld a, AMSDOS_XDPB_offset_records_per_sector
    call paketdsk_get_xdpb_parameter_value
    dec a  ; make it into a "records per sector '& mask'"
    inc hl
    inc hl
    and (hl)  ; (memory_record)  ; a = record within the current sector
    ld de, #0080  ; record size
    ld hl, AMSDOS_work_RAM_sector_buffer - #0080
    inc a
paketdsk_get_record_and_read_buffer_ptrs__loop:
    add hl, de
    dec a
    jr nz, paketdsk_get_record_and_read_buffer_ptrs__loop
    ex de, hl  ; de = ptr to the record within the sector buffer
    ld hl, (memory_sector_read_buffer_ptr)
    ld bc, #0080  ; record size
    ret     


;------------------------------------------------------------------------
paketdsk_get_FDC_response_and_restore_stack_if_error:
    call paketdsk_get_FDC_response  ; fdc get result phase
    ret c

    ld a, (memory_FDC_response)  ; get result phase data byte: fdc status register 0 
    and #08  ; isolate "not ready" flag 
    ret z
    jp paketdsk_restore_stack_stop_motor_and_get_error_if_any  ; "not ready" flag is set: disk missing


;------------------------------------------------------------------------
; - Gets the response from the FDC
; - If there is an error -> restore stack, stop motor, and return
; - Otherwise, it gets the status of the fdg result phase, and if error -> restore stack, stop motor, and return
; - Otherwise, just return.
paketdsk_get_FDC_response_and_restore_stack_if_error_from_fcd_response:
    call paketdsk_get_FDC_response_and_restore_stack_if_error
    ret c
    ret nz

    ld a, (memory_FDC_response + 1)  ; get result phase data byte: fdc status register 1
    and #02  ; isolate "not writeable" flag
    ret z
    jp paketdsk_restore_stack_stop_motor_and_get_error_if_any  ; "not writeable" flag is set:  therefore drive is write protected


;------------------------------------------------------------------------
; Floppy Disk Controller (FDC): get result phase
;
; Read result phase of fdc command.
;
; input:
; - bc: I/O address of FDC main status register
; 
; output:
; - D: number of bytes read in result phase
; - A: command result code (bit 7 and 6 of fdc status register 0)
; - zero clear and carry clear if condition was not "command completed successfully"
; - zero set and carry set if condition was "command completed successfully"
; - memory_result_phase_received_n_bytes: number of bytes received in result phase
; - memory_FDC_response: buffer for result phase data
paketdsk_get_FDC_response:
    push hl
    push de
        ld d, 0  ; initialise count of result bytes received
        ld hl, memory_FDC_response  ; buffer for result phase data
        push hl
paketdsk_get_FDC_response__loop:
            in a, (c)  ; read FDC main status register
            cp #c0  ; "data ready" & "data direction from fdc to cpu"
            jr c, paketdsk_get_FDC_response__loop
            
            inc c  ; BC = I/O address of FDC data register
            in a, (c)  ; read data from FDC data register
            dec c  ; BC = I/O address of FDC main status register
            ld (hl), a  ; store result byte in buffer
            inc hl  ; increment buffer point
            inc d  ; increment count of result bytes received
            
            ld a, 5
paketdsk_get_FDC_response__wait:
            dec a
            jr nz, paketdsk_get_FDC_response__wait

            ; is FDC busy 
            ; - if set, FDC has not completed command and furthur result bytes are
            ; available to be read,
            ; - if clear, FDC has completed command and all result bytes have been
            ; read by CPU

            in a, (c)  ; read FDC main status register
            and #10  ; "FDC busy"?
            jr nz, paketdsk_get_FDC_response__loop
        
        pop hl
        ; HL = start of result phase data buffer
        ld a, (hl)  ; read first status byte (FDC status register 0)
        and #c0  ; isolate execution status
        dec hl
        ld (hl), d  ; store count
    pop de
    pop hl
    ret nz
    
    scf     
    ret     


;------------------------------------------------------------------------
; clear fdc interrupt
paketdsk_clear_FDC_interrupt:
    push bc
        ld bc, #fb7e  ; BC = I/O address of FDC main status register
paketdsk_clear_FDC_interrupt__loop:
        ld a, 8  ; sense interrupt status
        call paketdsk_send_FDC_command_byte  ; fdc: send command byte 
        call paketdsk_get_FDC_response
        cp #80  ; "invalid"?
        jr nz, paketdsk_clear_FDC_interrupt__loop
    pop bc
    ret     


;------------------------------------------------------------------------
; write XDPB parameter to FDC. It first gets the parameter from the XDPB, and then sends it to the
; floppy disk controller.
;
; input:
; - bc: I/O address of FDC main status register
; - a: XDPB parameter index
paketdsk_send_FDC_xdpb_parameter:
    call paketdsk_get_xdpb_parameter_value  ; get XDPB parameter by index
    ; jr paketdsk_send_FDC_command_byte


;------------------------------------------------------------------------
; fdc: send command byte
;
; input:
; - bc: I/O address of FDC main status register
; - a: data byte to write to FDC
paketdsk_send_FDC_command_byte:
    push af
        push af
paketdsk_send_FDC_command_byte__wait:
            ; fdc ready to accept data?
            in a, (c)  ; read FDC main status register
            add a, a  ; transfer bit 7 ("data ready") to carry
            jr nc, paketdsk_send_FDC_command_byte__wait
            ; data direction to fdc?
            add a, a  ; transfer bit 6 ("data direction") to carry
            jr nc, paketdsk_send_FDC_command_byte__continue         
            ; conditions not met: fail
        pop af
    pop af
    ret     
paketdsk_send_FDC_command_byte__continue:
        ; conditions match to write command byte to fdc
        pop af
        inc c  ; BC = I/O address for FDC data register
        out (c), a  ; write data to FDC data register
        dec c  ; BC = I/O address for FDC main status register
        ; delay
        ld a, 5
paketdsk_send_FDC_command_byte__delay:
        dec a
        nop     
        jr nz, paketdsk_send_FDC_command_byte__delay
    pop af  ; success
    ret     


;------------------------------------------------------------------------
; Note: What this method does with the stack is horrible. It might have been "acceptable" back in the day,
;       although I doubt it. This is not the way to code things.
paketdsk_spin_disk_motor_up_if_off:
    ld (memory_sector_buffer_ptr_2), hl
    ex (sp), hl  ; hl = return address, and (memory_sector_buffer_ptr_2) goes to the stack
    push de
    push bc
    ld (memory_temporary_stack), sp
    push hl  ; push the return address again
    ld hl, paketdsk_restore_stack_stop_motor_and_get_error_if_any
    ex (sp), hl  ; hl = return address (and paketdsk_restore_stack_stop_motor_and_get_error_if_any goes to the stack, so that the stack is fixed again after we are done here)
    push hl  ; push the return address again
    push de
    push bc
    push af
        call paketdsk_delete_motor_ticker  ; delete ticker

        ; is motor already on?
        ld a, (memory_motor_on_off)  ; get motor state flag
        or a
        jr nz, paketdsk_spin_disk_motor_up_if_off__motor_on
        
        ; motor wasn't already on, switch it on
        ld bc, #fa7e ; motor on
        ld a, 1
        out (c), a

        ; install ticker
        ld de, (memory_motor_spinup_delay)
        call paketdsk_install_motor_on_off_ticker

        ; wait until motor flag signals drive is on
paketdsk_spin_disk_motor_up_if_off__wait_loop:
        ld a, (memory_motor_on_off)
        or a
        jr z, paketdsk_spin_disk_motor_up_if_off__wait_loop

paketdsk_spin_disk_motor_up_if_off__motor_on:
    pop af
    pop bc
    pop de
    ld hl, (memory_sector_buffer_ptr_2)
    ret


;------------------------------------------------------------------------
paketdsk_restore_stack_stop_motor_and_get_error_if_any:
    ld sp, (memory_temporary_stack)
    push af
        ld de, (memory_motor_spin_down_delay)
        call paketdsk_install_motor_on_off_ticker  ; "turn motor off" ticker
    pop af
    pop bc
    pop de
    pop hl  ; hl = memory_sector_buffer_ptr_2
    ld a, 0
    ret c
    ; An error occurred:
    ld hl, memory_FDC_response
    ld a, (hl)
    and #08  ; "not ready flag" of FDC status register 0
    inc hl
    or (hl)  ; or with FDC status register 1 (which contains error flags)
    or #40
    dec hl
    dec hl  ; hl = memory_result_phase_received_n_bytes
    ret     


;------------------------------------------------------------------------
; install motor on/off ticker
;
; input:
; - de: initial value for counter
paketdsk_install_motor_on_off_ticker:
    di
        ld a, 1
        ld (execute_ticker_flag), a
        ld (memory_ticker_block), de  ; initial counter
        ld bc, 0
        ld (memory_ticker_block + 2), bc  ; reset count
    ei
    ret


;------------------------------------------------------------------------
; drive motor ticker function (this is what is executed at the VSYNC interrupt when the counter reaches 0)
paketdsk_motor_ticker:
    ld hl, memory_motor_on_off
    ld a, (hl)
    cpl     
    ld (hl), a  ; change motor flag state
    or a  ; new state is off?
    jr z, paketdsk_turn_motor_off  ; turn off motor, set flag, and delete ticker
    ; new state is on. 


;------------------------------------------------------------------------
; Removes the motor ticker function from the set of tickers to be executed by the interrupt.
paketdsk_delete_motor_ticker:
    xor a
    ld (execute_ticker_flag), a
    ret


;------------------------------------------------------------------------
; turn off motor
paketdsk_turn_motor_off:
    ; delete ticker
    call paketdsk_delete_motor_ticker

    ; turn off drive motor
    ld a, 0
    ld bc, #fa7e
    out (c), a

    ; set disk motor flag
    xor a
    ld (memory_motor_on_off), a
    ret     


;------------------------------------------------------------------------
; get XDPB parameter by index
;
; input:
; - a: XDPB parameter index
; - e: drive
; output:
; - a: parameter data
; - hl, de preserved
; - Flags corrupt
paketdsk_get_xdpb_parameter_value:
    push hl
        call paketdsk_get_xdpb_parameter_address
        ld a, (hl)
    pop hl
    ret     


;------------------------------------------------------------------------
; get address of XDPB parameter
;
; input:
; - a: XDPB parameter index
; - e: drive (e == 1 means drive 1, e != 1, means drive 0)
; output:
; - hl: address of XDPB parameter data
; - de preserved
; - flags corrupt
paketdsk_get_xdpb_parameter_address:
    push de
        ld hl, AMSDOS_work_RAM_XDPB_drive_a
        dec e
        ld de, #0040 ; size of XDPB data
        jr nz, paketdsk_get_xdpb_parameter_address__drive_0
        add hl, de  ; HL = XDPB for drive 1
paketdsk_get_xdpb_parameter_address__drive_0:
        ld e, a
        add hl, de  ; add offset of XDPB parameter index
    pop de
    ret     


;------------------------------------------------------------------------
; convert character to upper case 
;
; input:
; - a: character
; output:
; - a: character converted to uper case
char_to_upper_case:
    cp 'a'
    ret c
    cp 'z'
    ret nc
    add a, #e0
    ret     


;------------------------------------------------------------------------
; clear memory
;
; input:
; - de: address
; - bc: length
clear_memory_de:
    xor a
    ld (de), a
    inc de
    dec bc
    ld a, b
    or c
    jr nz, clear_memory_de
    ret     


;------------------------------------------------------------------------
; calculate return address (in case of an error)
paketdsk_store_return_address_in_case_of_an_error:
    push hl
        ld hl, 4
        add hl, sp
        ld (AMSDOS_work_RAM_return_address), hl
    pop hl
    ret     


;------------------------------------------------------------------------
paketdsk_setup_XDPB_for_drive_in_openin_FCB:
    push af
        ld a, (AMSDOS_work_RAM_openin_FCB)  ; drive number: #00: A, #01: B, #FF: not open
        jr paketdsk_setup_XDPB_for_drive_in_openout_FCB__entry_point


;------------------------------------------------------------------------
paketdsk_setup_XDPB_for_drive_in_openout_FCB:
	push af
    	ld a, (AMSDOS_work_RAM_openout_FCB)
paketdsk_setup_XDPB_for_drive_in_openout_FCB__entry_point:
        cp #ff  ; is the drive open?
        jr z, paketdsk_return_with_error  ; not open!
        call paketdsk_setup_XDPB_for_drive
    pop af
    ret     


;--------------------------------------------------------------------------
paketdsk_return_with_error:
    cp a  ; nc


;--------------------------------------------------------------------------
; get stored return address
paketdsk_restore_return_address:
    ld hl, (AMSDOS_work_RAM_return_address)
    ld sp, hl
    ret     


;------------------------------------------------------------------------------
; setup XDPB for drive
;
; input:
; - bc: ptr the the filename buffer, pointing at the drive byte
paketdsk_setup_XDPB_for_drive_in_bc:
    ld a, (bc)  ; drive number
    inc bc


;------------------------------------------------------------------------------
; setup XDPB for drive
;
; input:
; - a: drive number
paketdsk_setup_XDPB_for_drive:
    push hl
    push de
    push bc
    push af
        ld c, a
        ld e, #ff
        ld a, (AMSDOS_work_RAM_openin_FCB)  ; drive number
        cp c
        jr z, paketdsk_setup_XDPB_for_drive__drive_number_set
        ld a, (AMSDOS_work_RAM_openout_FCB)  ; drive number
        cp c
        jr z, paketdsk_setup_XDPB_for_drive__drive_number_set
        ld e, 0
paketdsk_setup_XDPB_for_drive__drive_number_set:
        ; here: e = #ff if the drive matches that in openin or openout control block.
        ;       e = #00 otherwise.
        ; This is because if it's not one of the drives we had open, we need to re-generate the
        ; block allocation table. When this flag is set, it indicates that the block allocation table
        ; in the XDPB is up to date.
        push de
        push bc
            call paketdsk_setup_XDPB  ; setup XDPB, and get address of drive's DPH (in hl)
        pop bc
        pop de
        
        ld a, h  ; offset is 0?
        or l
        jp z, paketdsk_return_with_error  ; display "Bad command" and quit command

        ; store address of drive's DPH, drive and default drive flag
        ld (AMSDOS_work_RAM_DPH_ptr), hl
        ld a, c
        ld (AMSDOS_work_RAM_active_drive), a
        ld a, e
        ld (AMSDOS_work_RAM_default_drive_flag), a
    pop af
    pop bc
    pop de
    pop hl
    ret


;------------------------------------------------------------------------
; input:
; - de: default load address
; - bc: ptr to the FCB + 1 (pointing at the user)
; output:
; - hl: points at user / filename in the file header
paketdsk_create_openin_fileheader:
    ld hl, AMSDOS_work_RAM_openin_FILEHEADER
    call paketdsk_create_openout_fileheader__entry_point
    push hl
        ld hl, AMSDOS_work_RAM_openin_FILEHEADER + AMSDOS_FILEHEADER_offset_file_size_2 + 2
        ld (hl), #80  ; filesize = 128
    pop hl
    ret     


;------------------------------------------------------------------------
; input:
; - de: address of 2k buffer
; - bc: AMSDOS_work_RAM_tmp_filename_buffer + 1 (pointing at the user)
; output:
; - hl: points at user / filename in the file header
paketdsk_create_openout_fileheader:
    ld hl, AMSDOS_work_RAM_openout_FILEHEADER
paketdsk_create_openout_fileheader__entry_point:
    push bc
        push de
            ld (hl), 0  ; access mode (anything != 1 is "direct mode")
            inc hl
            ld (hl), e  ; address of 2k buffer
            inc hl
            ld (hl), d
            inc hl
            ld (hl), e  ; address of 2k buffer
            inc hl
            ld (hl), d
            inc hl
            push hl
                push bc
                    ld bc, #0045
                    ex de, hl
                    call clear_memory_de  ; 69 bytes in the openout file header ptr (the rest of the fileheader wich is 74 bytes)
                pop bc
                ld h, b  ; hl = user / filename ptr
                ld l, c
            pop de
            push de
                ld bc, 12
                ldir  ; copy user (1 byte) + filename (8 + 3 bytes).
            pop hl  ; hl = openout file header ptr + 5 (pointing at user number and filename)
        pop de  ; de = address of 2k buffer
        push hl
            ld bc, 18
            add hl, bc  ; hl = openout file header ptr + AMSDOS_FILEHEADER_offset_file_type
            ld (hl), #16  ; file type initialization. This is overwritten later. When opening a file, it's overwritten with the file header (if it exists), and when saving it's overwritten to a #02 in this codebase. Only when opening a file without header, it stays as #16.
            inc hl
            inc hl
            inc hl
            ld (hl), e  ; load address in memory
            inc hl
            ld (hl), d
            inc hl
            ld (hl), #ff  ; first block
        pop hl  ;  hl = openout file header ptr + 5 (pointing at user number and filename)
    pop bc
    ret     


;------------------------------------------------------------------------
; generate AMSDOS file header checksum
;
; input:
; - hl: address of header + 5 (pointing at filename)
; output:
; - de: generated checksum
; - hl: address of header + #48 (ptr to checksum)
paketdsk_generate_header_16bit_checksum:
    push hl
        ld hl, 0  ; initialise checksum
        ld d, h
        ld b, #43  ; number of bytes
paketdsk_generate_header_16bit_checksum__loop:
        ex (sp), hl  ; get address from stack
        ld a, (hl)  ; get byte
        inc hl  ; increment pointer
        ex (sp), hl  ; store address back to stack
        ld e, a
        add hl, de  ; update checksum
        djnz paketdsk_generate_header_16bit_checksum__loop
        ex de, hl  ; de = checksum
    pop hl  ; gets the address where the checksum will be stored (which has been updated by the 'ex (sp), hl' above)
    ret     


;------------------------------------------------------------------------
; generate and store checksum
;
; input:
; - hl: address of file header + 5 (pointing at filename)
paketdsk_update_header_checksum_and_write_first_file_record:
	push hl
    	call paketdsk_generate_header_16bit_checksum  ; generate checksum, and 'hl' now points to where it should be stored.
    	ld (hl), e  ; store checksum
    	inc hl
    	ld (hl), d
	pop hl
    push hl
        ld de, AMSDOS_work_RAM_openout_FCB
        call paketdsk_clear_FCB_previously_accessed_records
        call paketdsk_get_file_next_record_index
        ex de, hl  ; de = index of the first record of the file
    pop hl
    ld c, 0
    jp c, paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer
    jp paketdsk_return_with_error  ; "Bad command"


;------------------------------------------------------------------------
; BIOS: CAS IN OPEN
;
; input:
; - b: filename length in characters
; - hl: address of filename
; - de: address of 2K buffer
; output:
; - a: file type
paketdsk_cas_in_open:
    call paketdsk_store_return_address_in_case_of_an_error
    push de
        call paketdsk_initialize_filename_buffer  ; bc = filename buffer ptr
        call paketdsk_setup_XDPB_for_drive_in_bc

        ; Note: here the firmware had some code in case an extension was not specified,
        ;       which I have removed. Extensions should always be specified for any normal use.
        call paketdsk_check_if_filename_exists
        jp nc, paketdsk_return_with_error  ; "<filename> not found"
    pop de
    ; If we are here, the directory entry matching the filename has been copied to the 
    ; openin FCB.
    call paketdsk_create_openin_fileheader
    push hl
	    ld de, AMSDOS_work_RAM_openin_FCB
	    dec bc
	    ld a, (bc)
	    ld (de), a
	    
	    call paketdsk_clear_FCB_previously_accessed_records
	    
	    ld hl, AMSDOS_work_RAM_tmp_record_buffer
	    call paketdsk_read_next_record_from_file_into_the_read_buffer  ; reads 1st record into 'AMSDOS_work_RAM_tmp_record_buffer'
	    jr nc, paketdsk_cas_in_open__continue  ; if we could not load the first record, jump
	    ; We could load the first record, check if it's a header:
	    push hl
	    push de
	        call paketdsk_generate_header_16bit_checksum  ; generate file header checksum
	        call ld_word_from_hl  ; hl = stored checksum
	        call cp_hl_de
	    pop de
	    pop hl
	    jr nz, paketdsk_cas_in_open__no_header  ; if the checksum does not match, assume there is no header

		; file has a header
	    ld de, AMSDOS_work_RAM_openin_FILEHEADER + 5
	    ld bc, #0045
	    ldir  ; This overwrites the whole header (including the load address), extepd the first 5 bytes
	    jr paketdsk_cas_in_open__continue

paketdsk_cas_in_open__no_header:
        ; file doesn't have a header (unread the record we had already read)
    	call paketdsk_clear_FCB_previously_accessed_records
paketdsk_cas_in_open__continue
    pop hl
    ; HL = address of in-memory file header
    push hl
        ld hl, AMSDOS_work_RAM_openin_FILEHEADER + AMSDOS_FILEHEADER_offset_load_address
	    ld e, (hl)
	    inc hl
	    ld d, (hl)  ; de = load address (from header)
	    inc hl
	    inc hl
	    ld c, (hl)
	    inc hl
	    ld b, (hl)  ; bc = filesize (excluding header) (from header)
    pop hl  ; HL = address of buffer containing header
    scf  ; carry true
    sbc a, a  ; zero false
    ld a, (AMSDOS_work_RAM_openin_FILEHEADER + AMSDOS_FILEHEADER_offset_file_type)  ; a = file type (from header)
    ret     


;------------------------------------------------------------------------
; BIOS: CAS OUT OPEN
; 
; input:
; - hl: address of filename
; - b: length of filename
paketdsk_cas_out_open:
    ld de, 0  ; Note, the firmware asked for an additional parameter (in "de") with a "2k work buffer", 
              ; however, the saving code does actually not use it, and just uses it as default load address, that
              ; is later overwritten to "address of file". So, we just set this to 0
	call paketdsk_store_return_address_in_case_of_an_error
	push de
        call paketdsk_initialize_filename_buffer  ; bc = AMSDOS_work_RAM_tmp_filename_buffer
        ld hl, 13  ; 2 initial bytes + 8 name + 3 extension
        add hl, bc
        ld (hl), #ff  ; extent number
        inc hl
        inc hl
        ld (hl), #ff  ; number of records in current extent
		call paketdsk_setup_XDPB_for_drive_in_bc  ; bc += 1 as a side effect
	pop de
    ; here bc = AMSDOS_work_RAM_tmp_filename_buffer + 1 (pointing at the user)
	call paketdsk_create_openout_fileheader  ; hl = user / filename ptr
	push hl
		call paketdsk_replace_extension_with_dollars
		call paketdsk_flush_directory_buffer_and_delete_matching_files
		ld h, b
		ld l, c
		dec hl  ; make hl point at the drive
		ld de, AMSDOS_work_RAM_openout_FCB
		ld bc, 13  ; copy drive, user and filename/extension (with .$$$ extension)
		ldir 
		ld bc, 23
		call clear_memory_de ; clear the rest of the FCB
	pop hl
	scf  
	sbc a, a  ; z flag set, carry is preserved
	ret     


;------------------------------------------------------------------------
; BIOS: CAS IN DIRECT
;
; input:
; - hl: load address
; output:
; - hl: execution address
paketdsk_cas_in_direct:
    call paketdsk_store_return_address_in_case_of_an_error
    call paketdsk_setup_XDPB_for_drive_in_openin_FCB
    push hl
        ld hl, AMSDOS_work_RAM_openin_FILEHEADER
        ld a, (hl)
        cp 1  ; reading in character mode?
        jp z, paketdsk_return_with_error
        ld (hl), 2  ; read in direct mode

        ld de, AMSDOS_FILEHEADER_offset_file_size_2
        add hl, de
        ld e, (hl)
        inc hl
        ld d, (hl)  ; de = file size in bytes
    pop hl
    ; here:
    ; - hl: ptr to load the file
    ; - de: file size
    push de
        push hl
            ex de, hl
            ld a, 7
            call shift_hl_right_by_a  ; hl /= 128
            ld b, h
            ld c, l  ; bc: file size in records
        pop hl
        call paketdsk_read_file_into_memory_buffer
    pop de
    jr nc, paketdsk_cas_in_direct__done_reading
    ld a, e
    and #7f
    jr z, paketdsk_cas_in_direct__done_reading
    ; are there any left over bytes in the last record (< 128 bytes)
    push af
    push hl
        ld hl, AMSDOS_work_RAM_tmp_record_buffer
        push hl
            ld bc, 1  ; just one record
            call paketdsk_read_file_into_memory_buffer
        pop hl
    pop de
    pop bc
    jr nc, paketdsk_cas_in_direct__done_reading
    ; copy them from the temporary memory buffer to their target memory position:
    ld c, b
    ld b, 0
    ldir
paketdsk_cas_in_direct__done_reading:
    ; load execution address, and return:
    ld hl, AMSDOS_work_RAM_openin_FILEHEADER + AMSDOS_FILEHEADER_offset_execution_entry_point
    scf     
    sbc a, a
    jp ld_word_from_hl


;-----------------------------------------------------------------------------
; Reads a whole file into memory
;
; input:
; - hl: tmp record buffer ptr
; - bc: filesize in sectors
paketdsk_read_file_into_memory_buffer__loop:
    call paketdsk_read_next_record_from_file_into_the_read_buffer
    ret nc

IF PAKETDSK_SUPPORT_ENCRYPTED_BASIC == 1
    ld de, AMSDOS_work_RAM_openin_FILEHEADER + AMSDOS_FILEHEADER_offset_file_type
    ld a, (de)
    rra  ; is it encrypted BASIC?
    call c, paketdsk_encrypt_decrypt_record_buffer
ENDIF
    ld de, #0080
    add hl, de
    dec bc
paketdsk_read_file_into_memory_buffer:
    ld a, b
    or c
    jr nz, paketdsk_read_file_into_memory_buffer__loop
    scf
    ret     


;------------------------------------------------------------------------
; CAS OUT DIRECT
;
; input:
; - hl: load address / ptr to actual data
; - de: length
; - bc: execution address
; - a: type
paketdsk_cas_out_direct:
    call paketdsk_store_return_address_in_case_of_an_error
	call paketdsk_setup_XDPB_for_drive_in_openout_FCB
	push af
		push hl
			push de
				ld hl, AMSDOS_work_RAM_openout_FILEHEADER
				ld a, (hl)  ; a = access mode
				cp 1
				jp z, paketdsk_return_with_error  ; if acccess mode is "CHAR", return with error
				ld (hl), 2  ; set access mode to "DIRECT"
                ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_execution_entry_point + 1
				ld (hl), b
				dec hl
				ld (hl), c  ; save execution entry point
			pop bc  ; bc = length
			dec hl  
			ld (hl), b
			dec hl  ;  here hl is "header + AMSDOS_FILEHEADER_offset_file_size_1"
			ld (hl), c  ; save length in bytes
            ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_file_size_2 + 1
			ld (hl), b
			dec hl
			ld (hl), c  ; save length in bytes (this seems to be stored twice in the fileheader, for some reason)
            ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_data_length
			ld (hl), c
			inc hl
			ld (hl), b  ; save data length
		pop bc  ; bc = load address
		inc hl  ; hl = header + AMSDOS_FILEHEADER_offset_load_address
		ld (hl), c
		inc hl
		ld (hl), b  ; save load address
        ld hl, AMSDOS_work_RAM_openout_FILEHEADER + 1
		ld (hl), c
		inc hl
		ld (hl), b  ; ptr to 2k work buffer overwriten to load address
	pop af
	ld de, AMSDOS_FILEHEADER_offset_file_type - 2
	add hl, de  ; hl = header + AMSDOS_FILEHEADER_offset_file_type
	ld (hl), a  ; save file type (= 2)
paketdsk_cas_out_direct__flush_content_in_FILEHEADER_to_disk:
	ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_first_block
	ld a, (hl)  ; a = first block
	or a
	jr z, paketdsk_cas_out_direct__first_block_set
    ; The first block is not 0.
    ; Here, the firmware checked if the file type ended in #6, and if so, it did not skip
    ; this one block. I removed the code, since in this codebase, filetype is always "#02", but could
    ; that indicate that filetypes ending in #6 correspond to fiels without a header?
	ld de, AMSDOS_work_RAM_openout_FCB
	call paketdsk_inc_num_previously_accessed_records_in_FCB
	call paketdsk_inc_num_records_in_current_extent_in_FCB
paketdsk_cas_out_direct__first_block_set:
	ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_data_length  ; = #00b2
	push hl
		ld e, (hl)
		inc hl
		ld d, (hl)  ; de = data length
		ld bc, -AMSDOS_FILEHEADER_offset_data_length
		add hl, bc
		call ld_word_from_hl  ; LD HL,(HL)  ; hl = ptr to 2k work buffer (which was overwriten with load address)
		push hl
            ; The following call will only write directory entries to disk if the file is larger than 16K.
            ; Basically, if the file needs “n” directory entries, this will generate “n-1”, and the final
            ; one is generated in paketdsk_cas_out_close.
			call paketdsk_write_file_from_memory_buffer
		pop bc  ; bc = ptr to 2k work buffer (which was overwriten with load address)
	pop hl
	ld (hl), 0
	inc hl
	ld (hl), 0  ; set data length to 0
	inc hl
	inc hl
	inc hl
	ld (hl), 0  ; set first block to 0
	ld de, -(AMSDOS_FILEHEADER_offset_first_block - AMSDOS_FILEHEADER_offset_current_byte_in_2k_buffer_ptr)
	add hl, de  ; hl = header + AMSDOS_FILEHEADER_offset_current_byte_in_2k_buffer_ptr
	ld (hl), c
	inc hl
	ld (hl), b  ; set ptr to current byte in 2k buffer (which was overwriten with load address)
	scf  
	sbc a, a  ; preserve carry, set z flag
	ret     


;-----------------------------------------------------------------------------
; Writes a file of size 'de' from the memory buffer to disk
;
; If the file needs “n” directory entries, this will generate “n-1”, and the final
; one is generated in paketdsk_cas_out_close.
;
; input:
; - de: file size in bytes
; - hl: ptr to the memory buffer
paketdsk_write_file_from_memory_buffer:
	push de
    	ld a, 7
    	ex de, hl
        	call shift_hl_right_by_a  ; hl /= 128
    	ex de, hl
    	ld b, d
    	ld c, e  ; bc = file size in records
    	call paketdsk_write_file_from_memory_buffer__loop_pre_entry_point
	pop bc
    ; is there a left-over amount of bytes (< 128) for a final record?
	ld a, c
	and #7f
	ret z

	ld c, a
	ld b, 0
	ld de, AMSDOS_work_RAM_tmp_record_buffer
	push de
    	ldir
    	ld a, #1a  ; add one additional byte at the end of the file
    	ld (de), a
	pop hl
	inc bc  ; bc = 1
paketdsk_write_file_from_memory_buffer__loop_pre_entry_point:
	jr paketdsk_write_file_from_memory_buffer__loop_entry_point
paketdsk_write_file_from_memory_buffer__loop:
	push hl
IF PAKETDSK_SUPPORT_ENCRYPTED_BASIC == 1
    	ld de, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_file_type
    	ld a, (de)  ; a = file type
    	rra     
    	jr nc, paketdsk_write_file_from_memory_buffer__decrypted
        ; encrypted record, decrypt:
    	push bc
        	ld de, AMSDOS_work_RAM_tmp_record_buffer
        	push de
            	ld bc, 128
            	ldir
        	pop hl
    	pop bc
    	call paketdsk_encrypt_decrypt_record_buffer
ENDIF
paketdsk_write_file_from_memory_buffer__decrypted:
    	call paketdsk_write_next_record_to_file_from_the_tmp_record_buffer
	pop hl
	ld de, 128
	add hl, de
	dec bc  ; decrease the number of records to save
paketdsk_write_file_from_memory_buffer__loop_entry_point:
    ; are we done writing records?
	ld a, b
	or c
	jr nz, paketdsk_write_file_from_memory_buffer__loop
	ret     


;------------------------------------------------------------------------
; BIOS: CAS IN CLOSE
paketdsk_cas_in_close:
    call paketdsk_store_return_address_in_case_of_an_error
    call paketdsk_setup_XDPB_for_drive_in_openin_FCB
    call paketdsk_turn_motor_off
    ld a, #ff
    ld (AMSDOS_work_RAM_openin_FCB), a  ; set drive to #ff
    jr paketdsk_cas_out_close__done


;------------------------------------------------------------------------
; BIOS: AS OUT ABANDON
cas_out_abandon:
	call paketdsk_store_return_address_in_case_of_an_error
    call paketdsk_setup_XDPB_for_drive_in_openout_FCB
	ld de, AMSDOS_work_RAM_openout_FCB + 1
	xor a
	call paketdsk_update_block_allocation_table_for_current_extent
	dec de  ; de = ptr to file control block (FCB)
	ld a, #ff
	ld (de), a  ; drive number in the FCB = #ff
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track
	jr paketdsk_cas_out_close__done


;------------------------------------------------------------------------
; BIOS: CAS OUT CLOSE
paketdsk_cas_out_close:
	ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_file_size_2  ; = #00df
    ; check if the file size is 0
	ld a, (hl)
	inc hl
	or (hl)
	inc hl
	or (hl)
	jr z, cas_out_abandon  ; if file size is 0, abandon
	call paketdsk_store_return_address_in_case_of_an_error
    call paketdsk_setup_XDPB_for_drive_in_openout_FCB
	call paketdsk_cas_out_direct__flush_content_in_FILEHEADER_to_disk
	ld de, AMSDOS_work_RAM_openout_FCB
	push de
        ; This generates the final directory entry for a file (if the file needed more than one
        ; because it is larger than 16K, the previous ones were generated during paketdsk_cas_out_direct).
    	call paketdsk_init_next_available_directory_from_FCB
        ; - Here the firmware had some code to autogenerate a file extension if not present.
        ;   I've removed that, as we should always specify the extension.
        ; - Also, it checked if the file type ended in #6, and if so, it did not call
        ;   the 'paketdsk_update_header_checksum_and_write_first_file_record', reinforcing my hypothesis
        ;   that filetypes ending in #6 are files without a header. I have also removed that
        ;   code, as filetype is always #02 in this codebase.
        ld hl, AMSDOS_work_RAM_openout_FILEHEADER + AMSDOS_FILEHEADER_offset_filename
        call paketdsk_update_header_checksum_and_write_first_file_record  ; generate and store checksum
	pop bc  ; bc = openout control block ptr
	ld a, #ff
	ld (bc), a  ; set drive to #ff, which means "not open"
	inc bc
	call paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed
paketdsk_cas_out_close__done:
    scf
    sbc a, a
    ret     


;----------------------------------------------------------------
; Closes any file open in the current drive
paketdsk_close_file_in_current_drive:
    xor a
    ld (AMSDOS_work_RAM_default_drive_flag), a
    ld a, (AMSDOS_work_RAM_active_drive)
    ld h, a
    ld de, AMSDOS_work_RAM_openin_FCB
    call paketdsk_close_file_in_drive
    ld de, AMSDOS_work_RAM_openout_FCB
    ; jr paketdsk_close_file_in_drive


;----------------------------------------------------------------
; input:
; - h: active drive
; - de: openin or openout FCB ptr
paketdsk_close_file_in_drive:
    ld a, (de)  ; drive number
    cp h
    ret nz  ; not the current active drive

    ld a, #ff
    ld (de), a  ; mark drive number as "not open"
    inc de
    ret  ; "disk changed, closing <filename>"


IF PAKETDSK_SUPPORT_ENCRYPTED_BASIC == 1
;----------------------------------------------------------------
; This encrypts a 128 byte block. To do so, it uses two keys:
; - one of 11 bytes, and one of 13 bytes
; - 11 and 13 are coprimes, and hence, their combination results in a key of length 11 * 13 = 143, which is longer than 128 bytes,
;   which is enough.
; - encrypting twice, results in decrypting. So, the same encrypt method can also decrypt.
;
; input:
; - hl: ptr to the tmp record buffer to encrypt or decrypt
paketdsk_encrypt_decrypt_record_buffer:
    push hl
    push bc
    push hl
        ld de, #0101  ; d = 1, e = 1 (so that ix and hl will be set below)
        ld b, 128 + 1
        jr paketdsk_encrypt_decrypt_record_buffer__loop_start
paketdsk_encrypt_decrypt_record_buffer__loop:
        ex (sp), hl  ; hl = ptr to tmp record buffer
        ld a, (hl)  ; was "rst #20" in the AMSDOS original
        ex (sp), hl  ; restore 'encryptedbasic_key2' in hl
        ; a = [record buffer ptr] XOR [encryptedbasic_key1] XOR [encryptedbasic_key2]
        xor (hl)
        xor (ix)
        ; update the record buffer ptr data, and move on to the next byte
        ex (sp), hl
        ld (hl), a
        inc hl
        ex (sp), hl
        inc ix
        inc hl
paketdsk_encrypt_decrypt_record_buffer__loop_start:
        dec d
        jr nz, paketdsk_encrypt_decrypt_record_buffer__key1_reset_skip
        ld d, 11  ; length of 'encryptedbasic_key1'
        ld ix, encryptedbasic_key1
paketdsk_encrypt_decrypt_record_buffer__key1_reset_skip:
        dec e
        jr nz, paketdsk_encrypt_decrypt_record_buffer__key2_reset_skip
        ld e, 13  ; length of 'encryptedbasic_key2'
        ld hl, encryptedbasic_key2
paketdsk_encrypt_decrypt_record_buffer__key2_reset_skip:
        djnz paketdsk_encrypt_decrypt_record_buffer__loop
    pop hl
    pop de
    pop hl
    ret     


;----------------------------------------------------------------
; Keys to encrypt BASIC programs:
encryptedbasic_key1:
    db #49, #b1, #36, #f0, #2e, #1e, #06, #2a, #28, #19, #ea
encryptedbasic_key2:
    db #e2, #9d, #db, #1a, #42, #29, #39, #c6, #b3, #c6, #90, #45, #8a
ENDIF


;----------------------------------------------------------------
; extensions list
paketdsk_extension_list_dollars:
    db "$$$"
paketdsk_extension_list_bak:
    db "BAK"


;----------------------------------------------------------------
; "$$$"
;
; input:
; - bc: address of filename
paketdsk_replace_extension_with_dollars:
    push de
        ld de, paketdsk_extension_list_dollars
        jr paketdsk_replace_name_extension


;----------------------------------------------------------------
; "BAK"
;
; input:
; - bc: address of filename
paketdsk_replace_extension_with_bak:
    push de
        ld de, paketdsk_extension_list_bak
        jr paketdsk_replace_name_extension


;----------------------------------------------------------------
; Copies the extension in the openout header to the filename pointed to by hl.
;
; input:
; - bc: address of the filename (user number + 8.3 format)
paketdsk_replace_name_extension_with_the_one_in_openout_header:
	push de
		ld de, AMSDOS_work_RAM_openout_FILEHEADER + 14
        ; jr paketdsk_replace_name_extension


;----------------------------------------------------------------
; replace extension
;
; input:
; - bc: address of filename (user number + 8.3 format)
; - de: pointer to replacement extension
paketdsk_replace_name_extension:
		push hl
		push bc
			ld hl, 9  ; add offset to get pointer to extension (8 + 1, to skip user number)
			add hl, bc

			ld bc, 3  ; length of extension
			ex de, hl
			ldir  ; copy bytes
		pop bc
		pop hl
	pop de
	ret


;----------------------------------------------------------------
; - When we get to this method, we have the file already saved to disk, but with extension .$$$.
; - This method first looks to see if there is already a file with the name of the file we are trying
; to save, and also if there is a .BAK file (same name as target, just with .BAK extension), and then 
; renames the previous as .BAK (deleting the old .BAK if necessary), and having in mind if files are
; read-only, or read-write.
; - It will rename/delete all the directory entries of the files it is dealing with.
;
; input:
; - bc: openout control block ptr + 1
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed:
	ld hl, 12
	add hl, bc  ; hl = ptr to the last character of the filename name (not the extension).
	ld (hl), #ff  ; set the last character of the filename to #ff
	inc hl
	inc hl
	ld (hl), #ff  ; set the middle character of the filename to #ff
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
	push hl  ; hl should be -1 here
    	ld hl, 0
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop:
    	ex (sp), hl  ; hl = last directory entry number
    	call paketdsk_get_next_directory_entry_ptr  ; de = directory entry/catalog ptr, hl++
    	ex (sp), hl  ; directory number back to the stack
    	jr nc, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop_over
    	call paketdsk_replace_extension_with_bak
    	call paketdsk_user_filename_extent_in_control_block_match_catalog
    	jr nc, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_BAK
        ; catalog matches with openout control block ptr name, but with .BAK extension
    	ld h, 1
    	call paketdsk_is_file_read_only  ; get read/write state of file
    	jr c, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_BAK  ; jump if read-only
    	inc h
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_BAK:
        ; here: h = 1 if file is read only, and 2 if it's read/write
    	call paketdsk_replace_name_extension_with_the_one_in_openout_header
    	call paketdsk_user_filename_extent_in_control_block_match_catalog
    	jr nc, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_target
        ; catalog matches with openout ptr name, with the openout header extension
    	ld l, 1
    	call paketdsk_is_file_read_only  ; get read/write state of file
    	jr c, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_target  ; jump if read-only
    	inc l
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__no_target:
        ; here l = 1 if file is read-only, and 2 if it's read/write
    	ld a, h
    	or a
    	jr z, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop
    	ld a, l
    	or a
    	jr z, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop
        ; if we are here, h != 0 and l != 0, which means that we have found both a file with the .BAK extension,
        ; and one with the target extension
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop_over:
	pop af
	ld a, l
	or a
	jr z, paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target_and_target_to_bak
    ; if we are here, there was a file with the target name we are writing:
	dec a
	jr z, paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__existing_target_is_read_only
    ; and it was read/write:
	ld a, h
	or a
	jr z, paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target_and_target_to_bak
    ; there was already a .BAK file:
	dec a
	jr z, paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target
    ; the .BAK file was read/write. Here, we will delete the old .BAK file, rename the file that matches the
    ; target name as .BAK, and then write the target file.
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop2:
	call paketdsk_get_next_directory_entry_ptr
	ret nc

	call paketdsk_rename_previous_file_as_bak
	jr paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__loop2

paketdsk_rename_saved_dollar_file_and_previous_to_BAK_if_needed__existing_target_is_read_only:
    call paketdsk_replace_name_extension_with_the_one_in_openout_header
    ld d, b
    ld e, c
    jp paketdsk_return_with_error  ; "<filename> is read only"


;----------------------------------------------------------------
; - If the catalog contains a file that has the same name as the one we are trying to save, but .BAK, delete the .BAK
; - If the catalog matches the file we are trying to write, nor .$$$, rename the existing file as .BAK
; - If the catalog matches with the same name but .$$$, rename to the name we want.
;
; input:
; - bc: openout control block ptr + 1
; - de: directory entry/catalog ptr
paketdsk_rename_previous_file_as_bak:
    ; renames file in openout control block to .BAK, and if there was a match, remove the file and return
	call paketdsk_replace_extension_with_bak
	call paketdsk_user_filename_extent_in_control_block_match_catalog
	jp c, paketdsk_erase_file_and_return_if_error
paketdsk_rename_previous_file_as_bak__rename_dollar_to_target_and_target_to_bak:
    ; catalog does not match the .BAK file
	call paketdsk_if_catalog_matches_name_with_dollars_rename_to_target_extension
	ret c
    ; catalog does not match the .$$$ file
	call paketdsk_replace_name_extension_with_the_one_in_openout_header
	call paketdsk_user_filename_extent_in_control_block_match_catalog
	ret nc
    ; catalog matches with the original extension:
	push bc
    	ld b, d  ; bc = directory entry/catalog ptr
    	ld c, e
    	call paketdsk_replace_extension_with_bak
    	jr paketdsk_if_catalog_matches_name_with_dollars_rename_to_target_extension__write_directory_buffer  ; write directory buffer and update checksum


;----------------------------------------------------------------
; If the catalog in 'de' matches the name of the file we are trying to write, except that it ends in .$$$,
; rename the .$$$ to the name we want.
;
; input:
; - bc: openout control block ptr + 1
; - de: directory entry/catalog ptr
paketdsk_if_catalog_matches_name_with_dollars_rename_to_target_extension:         
	call paketdsk_replace_extension_with_dollars
	call paketdsk_user_filename_extent_in_control_block_match_catalog
	ret nc
    ; The catalog matches the .$$$ filename
	push bc
    	ld b, d  ; bc = directory entry/catalog ptr
    	ld c, e
    	call paketdsk_replace_name_extension_with_the_one_in_openout_header  ; restore the original extension
paketdsk_if_catalog_matches_name_with_dollars_rename_to_target_extension__write_directory_buffer:
	pop bc
	jp paketdsk_write_directory_buffer_and_update_checksum


;----------------------------------------------------------------
; This is the same as "paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target", 
; except that if there is a file already existing in the disk with the same name as the
; one we are trying to write, it will rename it to .BAK.
;
; input:
; - bc: openout control block ptr + 1
; - de: directory entry/catalog ptr
paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target_and_target_to_bak:
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target_and_target_to_bak__loop:
	call paketdsk_get_next_directory_entry_ptr
	ret nc

	call paketdsk_rename_previous_file_as_bak__rename_dollar_to_target_and_target_to_bak
	jr paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target_and_target_to_bak__loop


;----------------------------------------------------------------
; input:
; - bc: openout control block ptr + 1
; - de: directory entry/catalog ptr
paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target:
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target__loop:
	call paketdsk_get_next_directory_entry_ptr
	ret nc

	call paketdsk_rename_previous_dollar_to_target_or_erase_matching_target
	jr paketdsk_write_current_sector_and_rename_any_matching_dollar_to_target__loop


;----------------------------------------------------------------
; input:
; - bc: openout control block ptr + 1
; - de: directory entry/catalog ptr
paketdsk_rename_previous_dollar_to_target_or_erase_matching_target:
	call paketdsk_if_catalog_matches_name_with_dollars_rename_to_target_extension
	ret c

	call paketdsk_replace_name_extension_with_the_one_in_openout_header
	call paketdsk_user_filename_extent_in_control_block_match_catalog
	jp c, paketdsk_erase_file_and_return_if_error
	ret     


;--------------------------------------------------------------------------
; input:
; - hl: tmp record buffer ptr
paketdsk_read_next_record_from_file_into_the_read_buffer:
    push hl
    push de
    push bc
        push hl
            ld de, AMSDOS_work_RAM_openin_FCB
            call paketdsk_get_file_next_record_index
            jr nc, paketdsk_read_next_record_from_file_into_the_read_buffer__error
            ex de, hl  ; de = index of next record to use; hl = openin control block ptr
            ex (sp), hl  ; hl = tmp record buffer; openin control block ptr goes to the stack
            call paketdsk_read_record_from_current_sector_index
        pop de
        jr paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__inc_accessed_records  ; this increments the number of previously accessed records, and returns
paketdsk_read_next_record_from_file_into_the_read_buffer__error:
        pop hl
    pop bc
    pop de
    pop hl
    or a
    ret     


;--------------------------------------------------------------------------
; input:
; - hl: tmp record buffer ptr
paketdsk_write_next_record_to_file_from_the_tmp_record_buffer:
	push hl
	push de
	push bc
    	push hl
        	ld de, AMSDOS_work_RAM_openout_FCB
        	call paketdsk_get_extent_number_for_next_accessed_record  ; c = extent number, b = 0
        	jr c, paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__extent_ready
        	jp nz, paketdsk_return_with_error  ; "disk full"
            ; We need a new extent:
        	call paketdsk_init_next_available_directory_from_FCB
        	call paketdsk_init_extent_info_in_fcb
paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__extent_ready:
            call paketdsk_next_record_in_current_block_or_new_block
            ld c, 0
            jr c, paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__block_ready
            ; We need to find a new block!
            ; If we are here, hl = ptr to the block number in the current extent (inside the FCB block)
            push de  ; here de = openin/out FCB block ptr
            	ex de, hl
            	   call paketdsk_find_free_block
            	ex de, hl  ; de = block index of the free block that was found
            	jp nc, paketdsk_return_with_error  ; "disk full"
            	ld (hl), e  ; save the index of the free block in the FCB
            	ld a, b  ; here, b = max block number // 256
            	or a
            	jr z, paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__block_number_saved  ; if block numbers are 8bit, no need to update the next byte
            	inc hl
            	ld (hl), d
paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__block_number_saved:
        	pop de
            ; We call this function again, which will give us the record number to use within the new block
        	call paketdsk_next_record_in_current_block_or_new_block
        	ld c, 2
paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__block_ready:
            ; There are still records available in the current block:
        	ex de, hl  ; hl = openout FCB block ptr, de = record index
        	ex (sp), hl  ; put "openout FCB block ptr" in the stack, and get hl = tmp record buffer ptr
        	call paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer
	    pop de  ; recover openout FCB block ptr
    	call paketdsk_inc_num_records_in_current_extent_in_FCB
paketdsk_write_next_record_to_file_from_the_tmp_record_buffer__inc_accessed_records:
        call paketdsk_inc_num_previously_accessed_records_in_FCB
    pop bc
    pop de
    pop hl
    scf     
    ret     


;--------------------------------------------------------------------------
; Get next record index of this file
;
; input:
; - de: openin/out FCB ptr
; output:
; - if carry, z: hl: index of next record to use within the last used block
; - if no carry, z: we need to find a new block, hl: ptr to the block number in the current extent (inside the FCB block)
; - if no carry, nz: disk full
paketdsk_get_file_next_record_index:
    call paketdsk_get_extent_number_for_next_accessed_record
    jr c, paketdsk_get_file_next_record_index__extent_set
    ret nz  ; disk full
    ; We need to move to the next extent.
    call paketdsk_init_extent_info_in_fcb
    push de
        ld b, d
        ld c, e
        inc bc
        push bc
            call paketdsk_flush_directory_buffer_and_find_matching_directory_entry
            ex de, hl  ; hl = directory entry/catalog ptr
        pop de
        call c, ldir32  ; copy the found directory entry/catalog to the openin/out control block
    pop de
paketdsk_get_file_next_record_index__extent_set:
    call c, paketdsk_are_there_records_yet_to_be_accessed_in_the_file
    jp c, paketdsk_next_record_in_current_block_or_new_block  ; if there are, get the next record index
    ret     


;------------------------------------------------------------------------
; Erases a file, and returns if the atempt failed (e.g. the file was read-only).
;
; input:
; - de: address of catalog/directory-entry structure
paketdsk_erase_file_and_return_if_error:
	call paketdsk_erase_file
	jp nc, paketdsk_return_with_error
	ret     


;------------------------------------------------------------------------
; Sets the user of a directory entry to #e5, and clears the block allocation table for
; all the blocks associated with a given directory entry.
;
; input:
; - de: address of catalog/directory-entry structure
paketdsk_erase_file:
	call paketdsk_is_file_read_only  ; get read/write state of file
	ccf     
    ret nc  ; "<filename> is read only"

	xor a
	call paketdsk_update_block_allocation_table_for_current_extent
	ld a, #e5  ; USER #e5 is used for deleted files
	ld (de), a  ; set user to #e5
	jp paketdsk_write_directory_buffer_and_update_checksum


;----------------------------------------------------------------------------
; check if filename exists
; input:
; - bc: filename buffer ptr
; output:
; - carry: success, and the matching directory entry is copied to the openin FCB
; - no carry: failure, no file with that name found.
paketdsk_check_if_filename_exists:
    call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
    call paketdsk_directory_entry_search
    jr nc, paketdsk_check_if_filename_exists__set_default_drive_flag
    ; copy the found directory entry to the FCB (remember the FCB is a directory entry with some prefix and suffix bytes)
    push hl
        ld hl, AMSDOS_work_RAM_openin_FCB + 1
        ex de, hl
        call ldir32  ; copy 32 bytes from HL to DE 
    pop hl
    ld a, (AMSDOS_work_RAM_default_drive_flag)
    or a
    scf     
    ret nz
    ; If default drive flag is not set, we finish reading all the directory entries in the disk,
    ; so that we can finish filling up all the used blocks in the block allocation table of the XDPB.
paketdsk_check_if_filename_exists__loop:
    call paketdsk_get_next_directory_entry_ptr
    jr c, paketdsk_check_if_filename_exists__loop
    scf
paketdsk_check_if_filename_exists__set_default_drive_flag:
    ; Once we finish iterating through all directory entries (whether we found the file or not), we 
    ; set the default drive flag, to indicate that the block allocaiton table is up to date now.
    ld a, #ff
    ld (AMSDOS_work_RAM_default_drive_flag), a
    ret     


;----------------------------------------------------------------------------
; Writes the current sector, and deletes all files that user/filename-match with 
; the info in the openout/in control block.
paketdsk_flush_directory_buffer_and_delete_matching_files:
	call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
paketdsk_flush_directory_buffer_and_delete_matching_files__loop:
	call paketdsk_directory_entry_search
	jr nc, paketdsk_check_if_filename_exists__set_default_drive_flag
	call paketdsk_erase_file_and_return_if_error
	jr paketdsk_flush_directory_buffer_and_delete_matching_files__loop


;----------------------------------------------------------------------------
; Writes the current sector to disk, and set track to 0.
; If we wrote to the default drive, clear the DPH allocation table and set current directory to 0.
; output:
; - hl: -1
paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table:
    push bc
        call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track
    pop bc
    ld hl, -1
    ld a, (AMSDOS_work_RAM_default_drive_flag)
    or a
    ret nz

    push hl
        call paketdsk_clear_XDPB_block_allocation_table
    pop hl
    jp paketdsk_set_DPH_directory_plus_one  ; current directory = 0


;----------------------------------------------------------------------------
; Searches for a directory entry/catalog that matches the info in the openout/in control block.
;
; input:
; - bc: filename buffer ptr
; - hl: directory entry/catalog number - 1
; output:
; - de: directory entry/catalog ptr
; - carry: success
; - no carry/: not found
paketdsk_directory_entry_search:
    call paketdsk_get_next_directory_entry_ptr
    ret nc

    call paketdsk_user_filename_extent_in_control_block_match_catalog
    jr nc, paketdsk_directory_entry_search
    ret     


;----------------------------------------------------------------------------
; Get a pointer to a directory entry/catalog number
; If requesting with "default drive flag" set:
;   - Return if we are past the "directory number" in the DPH, otherwise, just return the next directory entry
; If requesting with "default drive flag" zero:
;   - If the next directory corresponds to a non-erased file, it'll mark all the used blocks of that file as used in the block allocation table.
;   - This is because when default drive flag is zero, we need to rebuild the block allocation table.
;
; input:
; - hl: directory entry/catalog number - 1
; output:
; - de: directory entry/catalog ptr
; - carry: success
; - no carry: error
paketdsk_get_next_directory_entry_ptr:
    inc hl
    ld a, (AMSDOS_work_RAM_default_drive_flag)
    or a
    jr nz, paketdsk_get_next_directory_entry_ptr__default_drive  ; if we are in the default drive, jump
    ; we are not in the default drive (this means the drive is not one in the current DPHs for either openin or openout)
    call paketdsk_get_next_directory_entry_ptr_internal  ; de = directory ptr
    ret nc

    ld a, (de)
    cp #e5
    scf
    ret z  ; if the directory entry is empty or for an erased file, return (carry set)

    call paketdsk_set_DPH_directory_plus_one
    ld a, #ff
    jp paketdsk_update_block_allocation_table_for_current_extent

paketdsk_get_next_directory_entry_ptr__default_drive:
    call paketdsk_cp_hl_to_DPH_directory_number
    ret nc  ; return if hl >= DPH directory number

    jp paketdsk_get_next_directory_entry_ptr_internal


;----------------------------------------------------------------------------
; Only gets the 2 lowest bytes (the number is a 24bit number)
;
; input:
; - de: openin/out control block ptr
; output:
; - hl: previously accessed records
paketdsk_get_number_of_previously_accessed_records_from_FCB:
    ld hl, AMSDOS_FCB_offset_num_previously_accessed_records
    add hl, de
    jp ld_word_from_hl


;----------------------------------------------------------------------------
; Computes the extend number we have to use to add one more record to a file.
;
; input:
; - de: openin/out control block ptr
; output:
; - carry: success, and it is the same extent we currently have pointed to in the FCB.
; - no carry, nz: disk full
; - no carry: z: disk is not full, but the extent to read, is not the one we currently have in the FCB.
paketdsk_get_extent_number_for_next_accessed_record:
    ld hl, AMSDOS_FCB_offset_num_previously_accessed_records + 2
    add hl, de
    ld a, (hl)  ; (NPAR) number of previously accessed records (highest byte, out of 3)
    or a
    ret nz  ; return if number of previously accessed records >= 65536 (disk full!)
    call paketdsk_get_number_of_previously_accessed_records_from_FCB  ; hl = lowest 2 bytes of number of "previously accessed records" in the FCB
    ; There are at most 128 records in an extent (128 bytes * 128 = 16k, which is the size of an extent).
    ld a, h
    rra     
    rra     
    rra     
    rra     
    and #0f
    ld b, a  ; b = high nibble in 'h' (should be 0)
    add hl, hl
    ld a, h
    and #1f
    ld c, a  ; c = (NPAR // 128) % 32  [this is the extent number]
    push bc
        ld hl, AMSDOS_FCB_offset_extent_number + 2
        add hl, de
        ld a, (hl)
        xor b  ; should also be 0
        jr nz, paketdsk_get_extent_number_for_next_accessed_record__failure  ; failure
        ld a, AMSDOS_XDPB_offset_extent_mask
        call paketdsk_read_byte_from_XDPB  ; a = extent mask (should be 0)
        cpl     
        ld b, a  ; b = #ff
        dec hl
        dec hl
        ld a, (hl)  ; a = extent number
        xor c  ; compare with the extent number we had calculated above
        and b  ; b == #ff, so, this should have no effect
        jr nz, paketdsk_get_extent_number_for_next_accessed_record__failure
        scf     
paketdsk_get_extent_number_for_next_accessed_record__failure:
    pop bc  ; b = 0, c = extent number
    sbc a, a  ; preserves carry, sets z
    ret     


;----------------------------------------------------------------------------
; input:
; - de: openin/out control block ptr
; - c: extent number
; - b: value to set FCB[#0f] (zero)
paketdsk_init_extent_info_in_fcb:
    ld hl, AMSDOS_FCB_offset_extent_number
    add hl, de
    ld (hl), c  ; set extent number
    inc hl
    inc hl
    ld (hl), b
    inc hl
    ex de, hl
        ; zero out 17 positions in 'de':
        ; - block numbers in current extent, and
        ; - number of previously accessed records
        ld bc, 17
        call clear_memory_de
    ex de, hl
    ret     


;----------------------------------------------------------------------------
; Compares the number of previously accessed records, with the total number of records in this file.
;
; input:
; - de: openin/out control block ptr
; output:
; - carry: previously accessed records < total
; - no carry: previously accessed records >= total
paketdsk_are_there_records_yet_to_be_accessed_in_the_file:
    push de
        call paketdsk_get_number_of_previously_accessed_records_from_FCB  ; hl = previously accessed records
        ld a, h
        and #0f
        ld h, a
        push hl
            ld hl, AMSDOS_FCB_offset_num_records_in_current_extent
            add hl, de
            ld c, (hl)  ; c = number of records in the current extent
            ld b, 0
            dec hl
            dec hl
            dec hl
            ld h, (hl)  ; h = extent number
            ld l, b
            ld a, 1
            call shift_hl_right_by_a
            add hl, bc  ; hl = number of records in total in the file (current extent plus previous extents)
        pop de
        inc de
        call cp_hl_de
        ccf     
    pop de
    ret     


;----------------------------------------------------------------------------
; Sees if there are still available records in the current block to write to.
;
; input:
; - de: openin/out FCB block ptr
; output:
; - b: max block number // 256
; - if carry, hl: index of next record to use within the last used block
; - it no carry, we need to find a new block, hl: ptr to the block number in the current extent (inside the FCB block)
paketdsk_next_record_in_current_block_or_new_block:
    call paketdsk_get_number_of_previously_accessed_records_from_FCB  ; hl = number of previously accessed records
    ld a, AMSDOS_XDPB_offset_block_mask
    call paketdsk_read_byte_from_XDPB  ; a = Block Mask
    and l
    ld c, a  ; c = record number within the last block
    ld a, AMSDOS_XDPB_offset_block_shift
    call paketdsk_read_byte_from_XDPB  ; a = Block Shift (records per block)
    call shift_hl_right_by_a  ; HL shift right by A
    ld a, AMSDOS_XDPB_offset_max_block_number_high
    call paketdsk_read_byte_from_XDPB  ; a = Max Block Number // 256
    ld b, a  ; b = Max Block Number // 256
    or a  ; can blocks number be >= 256?
    ld a, l  ; a = (number of previously accessed records) >> block shift
    ld hl, 17
    add hl, de  ; hl = ptr to block bumbers for current Extent in the FCB
    jr z, paketdsk_next_record_in_current_block_or_new_block__1byte_blocks
    ; each block number is 2 bytes (so, we can only have 8 blocks, as there ar eonly 16 bytes for this):
    and #07
    add a, a
    add a, l
    ld l, a
    adc a, h
    sub l
    ld h, a  ; hl += 2 * a (ptr to the block number in the current extent)
    push hl
        call ld_word_from_hl  ; LD HL,(HL)
        jr paketdsk_next_record_in_current_block_or_new_block__continue
paketdsk_next_record_in_current_block_or_new_block__1byte_blocks:
    ; each block number is 1 byte:
    and #0f
    add a, l
    ld l, a
    adc a, h
    sub l
    ld h, a  ; hl += a (ptr to the block number in the current extent)
    push hl
        ld l, (hl)
        ld h, 0
paketdsk_next_record_in_current_block_or_new_block__continue:
        ld a, h
        or l
        jr z, paketdsk_next_record_in_current_block_or_new_block__new_block  ; if the resulting block number is 0, we need to find a new block
    ; If we are here, there still records left within the last block, so, we will use it
    pop af
    ld a, AMSDOS_XDPB_offset_block_shift
    call paketdsk_read_byte_from_XDPB  ; a = Block Shift
paketdsk_next_record_in_current_block_or_new_block__record_to_block_loop:  ; shift hl left 'a' times. (to translate from blocks to records)
    add hl, hl
    dec a
    jr nz, paketdsk_next_record_in_current_block_or_new_block__record_to_block_loop
    ld a, c  ; hl += record within this block
    or l
    ld l, a
    scf
    ; if we return here, hl index of the next record to use within the last used block
    ret     
paketdsk_next_record_in_current_block_or_new_block__new_block:
    ; if we return here, we need to find a new block
    ; hl: ptr to the block number in the current extent (inside the FCB block)
    pop hl
    ret     


;--------------------------------------------------------------------------
; input:
; - de: openin/out FCB block ptr
paketdsk_inc_num_records_in_current_extent_in_FCB:
	ld hl, AMSDOS_FCB_offset_num_records_in_current_extent
	add hl, de
	ld a, (hl)  ; a = num records in current extent
	inc (hl)  ; increment the number of records in the current extent
	or a
	ret p
      ; if resulting number is >= 128, set it back to 1, and increment the current extent number
	ld (hl), 1
	dec hl
	dec hl
	dec hl
	inc (hl)  ; increment current extent number
	ret     


;--------------------------------------------------------------------------
; Finds the next available directory ptr, and initializes it with the values in the FCB.
; Then writes the directory buffer to disk, and updates the checksums.
;
; input:
; - de: openout FCB ptr
paketdsk_init_next_available_directory_from_FCB:
	push de
    	push de
        	call paketdsk_get_next_available_directory_ptr  ; de = next available directory ptr
        	ex (sp), hl  ; hl = openin/out FCB ptr
        	inc hl
        	call ldir32  ; initialize the new directory entry/catalog ptr to the values in the FCB ptr
    	pop hl  ; hl = directory number
    	call paketdsk_write_directory_buffer_and_update_checksum
	pop de
	ret     


;--------------------------------------------------------------------------
; Sets the previously accessed records in a control header to 0
; see: https://www.cpcwiki.eu/index.php/AMSDOS_Memory_Map
;
; input:
; - de: ptr to a FCB (File Control Block) (either for OPENIN or OPENOUT)
paketdsk_clear_FCB_previously_accessed_records:
    ld hl, AMSDOS_FCB_offset_num_previously_accessed_records
    add hl, de
    xor a
    ld (hl), a
    inc hl
    ld (hl), a
    inc hl
    ld (hl), a
    ret     


;--------------------------------------------------------------------------
; input:
; - de: ptr to a FCB (File Control Block) (either for OPENIN or OPENOUT)
paketdsk_inc_num_previously_accessed_records_in_FCB:
    ld hl, AMSDOS_FCB_offset_num_previously_accessed_records
    add hl, de
    inc (hl)  ; increment the number of previously accessed records
    ret nz
    ; if we have overflow, increment the next byte:
    inc hl
    inc (hl)
    ret nz
    ; if we have overflow, increment the final byte:
    inc hl
    inc (hl)
    ret     


;-----------------------------------------------------------------------
; Writes the current sector, and tries to find a directory entry/catalog that matches the info in the openout/in control block.
; Note: if we are not in the default drive, if returns with error.
;
; input:
; - hl: directory entry/catalog number - 1
; output:
; - de: directory entry/catalog ptr
; - carry: directory entry found
; - no carry: directory entry not found
paketdsk_flush_directory_buffer_and_find_matching_directory_entry:
    call paketdsk_flush_record_buffer_to_disk_if_necessary_and_reset_track_directory_and_allocation_table
    call paketdsk_directory_entry_search
    jr paketdsk_get_next_available_directory_ptr__check_we_are_on_default_drive


;-----------------------------------------------------------------------
; Gets the pointer to the next available directory entry.
; If it's not on the default drive, or the directory is full, it returns with error.
;
; output:
; - de: ptr to the next available directory
; - a: #e5
paketdsk_get_next_available_directory_ptr:
    ld hl, -1
    ; Loop until we find a directory entry that is available (has "#e5" as a user, which means it's available)
paketdsk_get_next_available_directory_ptr__loop:
    inc hl
    call paketdsk_get_next_directory_entry_ptr_internal  ; de = directory ptr
    jp nc, paketdsk_return_with_error  ; "Drive <drive>: directory full"

    ld a, (de)
    cp #e5  ; user number associated with erased files (available directory entries)
    jr nz, paketdsk_get_next_available_directory_ptr__loop
paketdsk_get_next_available_directory_ptr__check_we_are_on_default_drive:
    push af
        ld a, (AMSDOS_work_RAM_default_drive_flag)
        or a
        jp z, paketdsk_return_with_error
    pop af
    ret     


;-----------------------------------------------------------------------
; Check if the file information (user, filename, extent) matches that in a
; given directory entry/catalog.
;
; input:
; - bc: filename buffer ptr
; - de: directory entry/catalog ptr
paketdsk_user_filename_extent_in_control_block_match_catalog:
    push bc
    push de
    push hl
        ld h, b
        ld l, c  ; hl = filename buffer ptr
        ld a, (de)  ; a = user number in directory entry/catalog
        xor (hl) ; does it match with the user number in the openout control block?
        jr nz, paketdsk_user_filename_extent_in_control_block_match_catalog__no_match  ; if user does not match, jump
        ; user matches
        inc hl  ; hl = ptr to filename in openout control block
        inc de  ; de = ptr to filename in directory entry/catalog
        ld b, 11  ; 8 + 3 (filename length)
paketdsk_user_filename_extent_in_control_block_match_catalog__filename_loop:
        ld a, (hl)
        cp #3f  ; '?'
        jr z, paketdsk_user_filename_extent_in_control_block_match_catalog__letter_match  ; a '?' matches with any character
        ld a, (de)
        xor (hl)
        and #7f
        jr nz, paketdsk_user_filename_extent_in_control_block_match_catalog__no_match  ; no match!
paketdsk_user_filename_extent_in_control_block_match_catalog__letter_match:
        ; match
        inc hl
        inc de
        djnz paketdsk_user_filename_extent_in_control_block_match_catalog__filename_loop
        ; filename matches
        ld a, (hl)  ; a = extent number
        inc a
        jr z, paketdsk_user_filename_extent_in_control_block_match_catalog__extent_match  ; if #ff, jump
        ; extent number is not #ff
        ld a, AMSDOS_XDPB_offset_extent_mask
        call paketdsk_read_byte_from_XDPB  ; 'a' should be 0 here
        cpl  ; 'a' should be #ff here
        ld b, a
        ld a, (de)  ; a = extent number in directory entry/catalog
        xor (hl)
        and b  ; match only the bits that matter, acording to the extent mask
        jr nz, paketdsk_user_filename_extent_in_control_block_match_catalog__no_match  ; extends do not match, jump
        ; extent number matches
paketdsk_user_filename_extent_in_control_block_match_catalog__extent_match:
        inc hl
        inc de
        inc hl
        inc de
        ; Note: according to both:
        ; - https://www.cpcwiki.eu/index.php/AMSDOS_Memory_Map#File_Header_for_OPENIN.2FOPENOUT
        ; - and https://www.cpcwiki.eu/index.php/765_FDC#The_15_FDC_Commands
        ; these bytes (extent high byte) are unused, or just zero. But this code here,
        ; clearly shows that is incorrect.
        ld a, (hl)  ; 'a' = FCB[#0f]
        inc a
        jr z, paketdsk_user_filename_extent_in_control_block_match_catalog__no_match  ; if FCB[#0f] == #ff, fail
        ld a, (de)
        xor (hl)  ; compare with the same byte in the directory entry/catalog
paketdsk_user_filename_extent_in_control_block_match_catalog__no_match:
    pop hl
    pop de
    pop bc
    ret nz

    scf     
    ret     


;-----------------------------------------------------------------------
; This clears the DPH allocation table to all 0s (except the blocks used for the directory).
paketdsk_clear_XDPB_block_allocation_table:
    ld a, AMSDOS_XDPB_offset_max_block_number
    call paketdsk_read_word_from_XDPB  ; hl = max block number
    ld a, 3
    call shift_hl_right_by_a  ; hl = max block number / 8
    inc hl  ; number of bytes in the allocation table
    ex de, hl
    ld a, AMSDOS_DPH_offset_allocation_table_ptr
    call paketdsk_read_word_from_DPH_ptr  ; hl = allocation table ptr (this is inside the XDPB corresponding the the current drive)
    push hl
paketdsk_clear_XDPB_block_allocation_table__loop:  ; zero out the allocation table
        ld (hl), 0
        inc hl
        dec de
        ld a, d  ; is de == 0?
        or e
        jr nz, paketdsk_clear_XDPB_block_allocation_table__loop
        ; Set the blocks that are used by the directory:
        ld a, AMSDOS_XDPB_offset_directory_allocation_table
        call paketdsk_read_word_from_XDPB
        ex de, hl  ; de = XDBP directory allocation table
    pop hl  ; hl = allocation table ptr
    ld (hl), e
    inc hl
    ld (hl), d
    ret     


;-----------------------------------------------------------------------
; Updates the block allocation table in the XDPB for all the blocks in the current directory entry.
;
; input:
; - a: #ff if we want to update the block allocation table to 1s,
;      and #00 if we want to update it to 0s.
; - de: ptr to directory entry/catalog or
;       ptr to file control block (FCB) + 1 (since an FCB is a catalog with a prefix and a suffix)
paketdsk_update_block_allocation_table_for_current_extent:
    push hl
    push de
    push bc
        ld c, a
        ld hl, #0010
        add hl, de  ; hl = ptr to Block Numbers for current Extent
        ld b, 16  ; size of the array of blocks in the current extent
paketdsk_update_block_allocation_table_for_current_extent__loop:
        ld e, (hl)  ; block number
        inc hl
        ld a, AMSDOS_XDPB_offset_max_block_number_high
        call paketdsk_read_byte_from_XDPB
        or a
        jr z, paketdsk_update_block_allocation_table_for_current_extent__block_number_set
        ; block numbers are 2 bytes
        dec b
        ld a, (hl)
        inc hl
paketdsk_update_block_allocation_table_for_current_extent__block_number_set:
        ld d, a  ; de = block number
        or e
        jr z, paketdsk_update_block_allocation_table_for_current_extent__skip  ; if block number == 0
        push hl
            ld a, AMSDOS_XDPB_offset_max_block_number
            call paketdsk_read_word_from_XDPB  ; hl = max block number
            ld a, l
            sub e
            ld a, h
            sbc a, d  ; is ls de <= hl?
            call nc, paketdsk_update_block_allocation_table_one_block  ; call if de <= hl
        pop hl
paketdsk_update_block_allocation_table_for_current_extent__skip:
        djnz paketdsk_update_block_allocation_table_for_current_extent__loop
    pop bc
    pop de
    pop hl
    scf     
    ret     


;-----------------------------------------------------------------------
; Updates a bit in the block allocation table in the XDPB.
;
; input:
; - c: #ff if we want to set the bit to 1, and #00 if we want it to 0
; - de: block number
paketdsk_update_block_allocation_table_one_block:
    push bc
    push de
        push de
            ex de, hl
                ld a, 3
                call shift_hl_right_by_a  ; hl /= 8
            ex de, hl  ; de = block number // 8
            ld a, AMSDOS_DPH_offset_allocation_table_ptr
            call paketdsk_read_word_from_DPH_ptr
            add hl, de  ; hl = ptr to the byte of the block in the allocation table
        pop de
        ld a, e
        and #07
        ld e, a  ; e = bit within the byte in the allocation table
        ld a, 1
        inc e
paketdsk_update_block_allocation_table_one_block__bit_mask_loop:
        rrca    
        dec e
        jr nz, paketdsk_update_block_allocation_table_one_block__bit_mask_loop
        ; here 'a' is a mask indicating the bit corresponding to the block
        ld b, a
        and c
        ld c, a  ; b = bit mask, c = c & bit mask
        ld a, b
        cpl     
        and (hl)  ; a = previous value of (hl), but with the target bit zeroed out
        or c  ; we set the bit to the target value
        ld (hl), a  ; update tbe block allocation table
    pop de
    pop bc
    ret     


;-----------------------------------------------------------------------
; Iterates over the block allocation table trying to find one that is available.
; output:
; - hl: index of a free block
; - no carry: disk full
paketdsk_find_free_block:
	push bc
	push de
    	ld a, AMSDOS_XDPB_offset_max_block_number
    	call paketdsk_read_word_from_XDPB
    	ex de, hl  ; de = Max Block Number from XDPB
    	ld a, AMSDOS_DPH_offset_allocation_table_ptr
    	call paketdsk_read_word_from_DPH_ptr  ; hl = Pointer to Block Allocation Table (ALT)
paketdsk_find_free_block__byte_loop:
    	ld bc, #0880  ; c = 128, b = 8
paketdsk_find_free_block__bit_loop:
    	ld a, (hl)  ; read from the block allocation table
    	and c
    	jr z, paketdsk_find_free_block__found
    	rrca 
    	ld c, a  ; shift the c bitmask to the right (to look for the next bit)
    	ld a, d
    	or e  ; de == 0
    	jr z, paketdsk_find_free_block__done  ; max number of blocks reached, and there wasn't any block available
    	dec de
    	djnz paketdsk_find_free_block__bit_loop  ; bit loop
    	inc hl  ; move to the next byte in the ALT
    	jr paketdsk_find_free_block__byte_loop
paketdsk_find_free_block__found:  ; we found a free block!
    	ld a, (hl)
    	or c
    	ld (hl), a  ; mark it as used (set it's bit to 1)
    	ld a, AMSDOS_XDPB_offset_max_block_number
    	call paketdsk_read_word_from_XDPB  ; hl = Max Block Number
    	or a
    	sbc hl, de  ; hl = index of the block we found to be free
    	scf
paketdsk_find_free_block__done:
	pop de
	pop bc
	ret  


;-----------------------------------------------------------------------
; Gets the pointer to a new directory entry/catalog. If this is the first one of a new block
; (i.e. "catalog number" % 4 == 0), it updates the checksum of the new block that will contain this
; directory entry.
;
; input:
; - hl: directory entry/catalog number
paketdsk_get_next_directory_entry_ptr_internal:
    push hl
    push bc
        ld a, l
        and #03
        jr nz, paketdsk_get_next_directory_entry_ptr_internal__sector_read
        ; first directory number in a new sector, we might need to read a new sector from disk.
        ; Check if directory number is larger than the maximum allowed.
        ex de, hl
        ld a, AMSDOS_XDPB_offset_last_valid_dir_entry_number
        call paketdsk_read_word_from_XDPB
        call cp_hl_de
        ccf     
        ex de, hl  ; recover the directory number in hl
        jr nc, paketdsk_get_next_directory_entry_ptr_internal__done  ; maximum directory entry number exceeded
        ; We are still within bounds
        call paketdsk_read_directory_record_and_update_checksum
        xor a
paketdsk_get_next_directory_entry_ptr_internal__sector_read:
        ld b, a  ; b = directory entry within the current block (4 entries per block)
        ld hl, AMSDOS_work_RAM_directory_record_buffer
        ld de, 32  ; size of a catalog/directory entry
        inc b
        jr paketdsk_get_next_directory_entry_ptr_internal__loop_entry_point
paketdsk_get_next_directory_entry_ptr_internal__loop:
        add hl, de
paketdsk_get_next_directory_entry_ptr_internal__loop_entry_point:
        djnz paketdsk_get_next_directory_entry_ptr_internal__loop
        ex de, hl  ; de = ptr to the new directory entry
        scf
paketdsk_get_next_directory_entry_ptr_internal__done:
    pop bc
    pop hl
    ret     


;-----------------------------------------------------------------------
; Updates the checksum for the sector corresponding to the current directory if
; it does not match the current one.
;
; input:
; - hl: directory number (4 per record)
paketdsk_read_directory_record_and_update_checksum:
    ld a, 2
    call shift_hl_right_by_a
    ex de, hl  ; de = record index
    ld hl, AMSDOS_work_RAM_directory_record_buffer
    call paketdsk_read_record_from_current_sector_index
    ld a, AMSDOS_XDPB_offset_checksum_area_size
    call paketdsk_read_word_from_XDPB  ; hl = checksum area size
    ex de, hl
        call cp_hl_de
    ex de, hl
    ret nc  ; if the record index is >= than the checksum area size, return with error

    ld a, AMSDOS_DPH_offset_checksums_ptr
    call paketdsk_read_word_from_DPH_ptr
    add hl, de  ; hl = ptr to the checksum of the target record
    call paketdsk_compute_directory_buffer_checksum
    cp (hl)
    ret z  ; checksums match, we are done

    push af
        ex de, hl
            add hl, hl
            add hl, hl  ; hl = directory number
            call paketdsk_cp_hl_to_DPH_directory_number
        ex de, hl
    pop de
    jp c, paketdsk_close_file_in_current_drive  ; if "current directory number" > hl
    ld (hl), d  ; update the checksum
    ret     


;-----------------------------------------------------------------------
; Writes the directory buffer to disk, and updates the corresponding checksum.
;
; input:
; - hl: directory number (4 per sector)
paketdsk_write_directory_buffer_and_update_checksum:
	push hl
	push bc
    	ld a, 2
    	call shift_hl_right_by_a  ; hl /= 4 (sector corresponding to the directory number)
    	ex de, hl
        	ld hl, AMSDOS_work_RAM_directory_record_buffer
        	ld c, 1  ; mark that we want to write the directory buffer in the method below
        	call paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer
        	ld a, AMSDOS_XDPB_offset_checksum_area_size
        	call paketdsk_read_word_from_XDPB
    	ex de, hl  ; de = checksum area size, hl = sector index corresponding to the directory number
    	call cp_hl_de
    	ex de, hl
    	jr nc, paketdsk_write_directory_buffer_and_update_checksum__checksum_updated  ; if it's outside the checksum area, just return
    	ld a, AMSDOS_DPH_offset_checksums_ptr
    	call paketdsk_read_word_from_DPH_ptr
    	add hl, de
    	call paketdsk_compute_directory_buffer_checksum
    	ld (hl), a  ; set the checksum
paketdsk_write_directory_buffer_and_update_checksum__checksum_updated:
	pop bc
	pop hl
	call paketdsk_cp_hl_to_DPH_directory_number
	ret c


;-----------------------------------------------------------------------
; Set directory number in the DPH for the current drive to hl + 1.
;
; input:
; - hl: directory number - 1
paketdsk_set_DPH_directory_plus_one:
    push de
    push hl
        ex de, hl
        inc de  ; directory number += 1
        ld a, AMSDOS_DPH_offset_current_directory_number
        call paketdsk_get_DPH_ptr_plus_offset
        ld (hl), e
        inc hl
        ld (hl), d  ; set directory number
    pop hl
    pop de
    scf
    ret


;-----------------------------------------------------------------------
; Compares the number in hl to the directory in the DPH for the current drive.
;
; input:
; - hl: directory number
; output:
; - cp hl, (directory in DPH)
paketdsk_cp_hl_to_DPH_directory_number:
    push de
        push hl
            ld a, AMSDOS_DPH_offset_current_directory_number
            call paketdsk_get_DPH_ptr_plus_offset
            ld e, (hl)
            inc hl
            ld d, (hl)  ; de = directory number
        pop hl
        call cp_hl_de
    pop de
    ret     


;-----------------------------------------------------------------------
; Checksum for the 128 bytes of the directory buffer.
paketdsk_compute_directory_buffer_checksum:
    push bc
    push hl
        ld b, 128
        ld hl, AMSDOS_work_RAM_directory_record_buffer
        xor a
paketdsk_compute_directory_buffer_checksum__loop:
        add a, (hl)
        inc hl
        djnz paketdsk_compute_directory_buffer_checksum__loop
    pop hl
    pop bc
    ret     


;------------------------------------------------------------------------
; get read only, read/write state
;
; input:
; - de: address of catalog/directory-entry structure
; output:
; - carry: read only
; - no carry: read/write 
paketdsk_is_file_read_only:
	push hl
    	ld hl, 9
    	add hl, de
    	ld a, (hl)  ; get first character of the extension. bit 7 of this character encodes read or read/write status.
    	add a, a  ; transfer bit 7 into carry
	pop hl
	ret     


;------------------------------------------
; input:
; - de: record index
; - hl: pointer to buffer where to put the read data
paketdsk_read_record_from_current_sector_index:
    push bc
    push de
    push hl
        call paketdsk_set_track_and_record_from_record_index
        call paketdsk_read_sector_and_copy_to_buffer
        jr paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer__entry_point  ; just detect errors, and restore the stack

;------------------------------------------
; Given a record index, it saves the current sector (if necessary) to the next
; available sector/track (notice each sector contains several records, 4, so,
; sector index will only increment when enough records are written).
;
; input:
; - c: (see 'paketdsk_inc_sector_track_and_flush_record_buffer')
; - de: record index
; - hl: tmp record buffer ptr
paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer:
    push bc
    push de
    push hl
        push bc
            call paketdsk_set_track_and_record_from_record_index
        pop bc
        call paketdsk_inc_sector_track_and_flush_record_buffer
paketdsk_set_next_sector_track_from_record_index_and_flush_record_buffer__entry_point:
        or a
        jp nz, paketdsk_return_with_error
    pop hl
    pop de
    pop bc
    ret


;------------------------------------------
; Given a record index (in 'de'), computes which track does it
; correspond to (and which record within that track), and sets
; 'memory_track' and 'memory_record' accordingly).
;
; input:
; - de: record index
; - hl: pointer to buffer where to put the read data
paketdsk_set_track_and_record_from_record_index:
    ld (memory_sector_read_buffer_ptr), hl
    ld a, AMSDOS_XDPB_offset_track_offset
    call paketdsk_read_word_from_XDPB
    ld b, h
    ld c, l  ; bc = Track Offset (should be == 0 for DATA format)
    xor a  ; a = AMSDOS_XDPB_offset_records_per_track
    call paketdsk_read_word_from_XDPB  ; hl = Records per Track (should be 36 for DATA format)
    ; This loop does: bc = offset + de // records-per-track, hl = de % records-per-track
    dec bc
paketdsk_set_track_and_record_from_record_index__loop:
    inc bc
    ld a, e  ; de -= hl
    sub l
    ld e, a
    ld a, d
    sbc a, h
    ld d, a
    jr nc, paketdsk_set_track_and_record_from_record_index__loop
    add hl, de  ; hl = index of the record within the track, bc = track
    push hl
        ld a, c
        ld (memory_track), a
    pop bc
    xor a  ; a = AMSDOS_DPH_offset_skew_factor_translation
    call paketdsk_read_word_from_DPH_ptr
    ex de, hl  ; de = Skew Factor Translation (physical-to-logical sector)
    ld h, b
    ld l, c  ; hl = index of the record within the track
    ld a, c
    ld (memory_record), a  ; index of the record within the track (8 bit)
    ret     


;------------------------------------------
; input:
; - a: offset
; output:
; - hl = (AMSDOS_work_RAM_DPH_ptr) + a
paketdsk_get_DPH_ptr_plus_offset:
    ld hl, (AMSDOS_work_RAM_DPH_ptr)
    add a, l
    ld l, a
    ret nc
    inc h
    ret


;------------------------------------------
; input:
; - a: offset of the pointer we want inside the XDPB
; output:
; - hl = (pointer)
paketdsk_read_word_from_DPH_ptr:
    call paketdsk_get_DPH_ptr_plus_offset
    jp ld_word_from_hl  ; LD HL, (HL)


;------------------------------------------
; (1) gets the XDPB pointer, (2) adds 'a', and (3) reads a word from that ptr
;
; input:
; - a: offset inside XDPB
; output:
; - hl: word(XDPB_ptr + a)
paketdsk_read_word_from_XDPB:
    push af
	    ld a, AMSDOS_DPH_offset_xdpb_ptr
	    call paketdsk_read_word_from_DPH_ptr  ; hl = Pointer to XDPB
    pop af
    ; hl += a
    add a, l
    ld l, a
    adc a, h
    sub l
    ld h, a
    jp ld_word_from_hl  ; LD HL, (HL)


;------------------------------------------
; input:
; - a: offset inside XDPB
; output:
; - a: (XDPB_ptr + a)
paketdsk_read_byte_from_XDPB:
    push hl
        call paketdsk_read_word_from_XDPB
        ld a, l
    pop hl
    ret     


;--------------------------------------------------------------
; output:
; - bc: AMSDOS_work_RAM_tmp_filename_buffer
paketdsk_initialize_filename_buffer:
    ld c, #ff  ; character to initialize normalized filename with
    ld de, AMSDOS_work_RAM_tmp_filename_buffer  ; offset in working memory to write normalized filename
    
    call paketdsk_init_drive_user_and_normalize_filename  ; bc = AMSDOS_work_RAM_tmp_filename_buffer
    push bc
        ld d, 8 + 3  ; filename size + extension size
        inc bc
        ; make sure there are no wildcards:
paketdsk_initialize_filename_buffer__wildcard_loop:
        inc bc
        ld a, (bc)
        cp '?'
        jp z, paketdsk_return_with_error  ; display "Bad command" and quit command
        dec d
        jr nz, paketdsk_initialize_filename_buffer__wildcard_loop
    pop bc
    ret     


;------------------------------------------------------------------
; input:
; - hl: ptr to filename
; - de: ptr in the working memory to write the normalized filename to
; - c: character to initialize filename with (#ff)
; output:
; - bc: ptr to normalized filename
; - z: filename is empty
; - nz: all correct
paketdsk_init_drive_user_and_normalize_filename:
    push hl
        push de
        	; initialize default drive and user (2 bytes):
            ld a, (AMSDOS_work_RAM_drive)
            ld (de), a
            inc de
            ld a, (AMSDOS_work_RAM_user)
            ld (de), a
            inc de
            push bc
            	; initialize filename to all 'c's (8 + 3 'c's, and 3 0s).
                ld b, c
                ld c, 8  ; filename length
                call paketdsk_fill_de_with_spaces
                ld a, b
                ld c, 3  ; extension length
                call paketdsk_fill_de_with_a
                ld bc, 3
                call clear_memory_de
            pop bc
        pop de
    pop hl
    push de
    	call paketdsk_normalize_filename
    pop de
    jp nc, paketdsk_return_with_error  ; display "Bad command" and quit command
    ld b, d
    ld c, e
    inc de  ; skip drive
    inc de  ; skip user
    ld a, (de)
    cp ' '  ; check if filename is empty
    jp z, paketdsk_return_with_error  ; "Bad command"
    ret


;------------------------------------------------------------------
; Normalizes a file name (uppercase, remove illegal characters, replace wildcards, etc.)
;
; input:
; - hl: ptr to the filename
; - b: length of the filename
; - de: ptr to the normalized filename we are writing
; output:
; - carry: success
; - no carry: failure, name format was incorrect
paketdsk_normalize_filename:
    dec hl
    call paketdsk_next_non_space_char_upper
    ccf
    ret c  ; if we have reached the end of the filename, we are done

    ld c, a
    ; See if there is a ":" character in the filename
    ; This is to see if a "user" was specified (from 0 to 15)
    push hl
    push bc
paketdsk_normalize_filename__find_colon_loop:
	    cp ':'
	    jr z, paketdsk_normalize_filename__find_colon_loop_done
	    call paketdsk_next_char_upper
	    jr c, paketdsk_normalize_filename__find_colon_loop
	    scf
paketdsk_normalize_filename__find_colon_loop_done:
    pop bc
    pop hl
    ld a, c
    jr c, paketdsk_normalize_filename__no_user  ; jump if no ":"
    ; There was a ":" character in the filename, fill the user info
    inc de  ; skip drive number
    cp '0'
    jr c, paketdsk_normalize_filename__done_with_user
    cp '9' + 1
    jr nc, paketdsk_normalize_filename__done_with_user
    ; we have a number:
    sub '0'
    ld c, a
    ld (de), a
    call paketdsk_next_char_upper
    cp '0'
    jr c, paketdsk_normalize_filename__done_with_user
    cp '9' + 1
    jr nc, paketdsk_normalize_filename__done_with_user
    or a
    dec c
    ret nz  ; if there are two digits, the first must have been a 1

    add a, 10 - '0'
    cp 16  ; we can only have users 0 - 15
    ret nc

    ld (de), a  ; save user
    call paketdsk_next_char_upper
paketdsk_normalize_filename__done_with_user:
    dec de
    cp 'Q'
    jr nc, paketdsk_normalize_filename__done_with_drive
    cp 'A'
    jr c, paketdsk_normalize_filename__done_with_drive
    sub 'A'
    ld (de), a
    call paketdsk_next_char_upper
paketdsk_normalize_filename__done_with_drive:
    call paketdsk_next_non_space_char_upper__loop
    xor #3a
    ret nz

    call paketdsk_next_non_space_char_upper
    ccf     
    ret c

paketdsk_normalize_filename__no_user:
    inc de  ; skip drive number
    inc de  ; skip user
    cp '.'
    ret z

    ld c, 8  ; max length of the filename (before extension)
    call paketdsk_normalize_filename_fragment
    ret c

    xor '.'
    ret nz

    call paketdsk_next_non_space_char_upper
    ld c, 3  ; max length of the file extension
    jr nc, paketdsk_fill_de_with_spaces
    ; jr paketdsk_normalize_filename_fragment


;--------------------------------------------------------
; Normalize a filename part (extension or name before extension). 
; Normalization involves making names uppercase, removing illegal characters,
; filling with spaces up to max length, or with wildcards, if '*' is found.
;
; input:
; - hl: ptr to filename
; - c: length of the current part
; - de: ptr where we are writing the normalized filename
paketdsk_normalize_filename_fragment:
	; If we have a non printable character (< ' '), just fill with spaces
    cp ' '
    jr c, paketdsk_fill_de_with_spaces
    ; See if we have an illegal character:
    push hl
    push bc
	    ld b, a
	    ld hl, paketdsk_invalid_characters_table
paketdsk_normalize_filename_fragment__invalid_character_table_loop:
	    ld a, (hl)
	    inc hl
	    or a
	    jr z, paketdsk_normalize_filename_fragment__invalid_character_table_loop_done
	    cp b
	    jr nz, paketdsk_normalize_filename_fragment__invalid_character_table_loop
	    scf  ; we found an illegal character!
paketdsk_normalize_filename_fragment__invalid_character_table_loop_done:
	    ld a, b
    pop bc
    pop hl
    jr c, paketdsk_fill_de_with_spaces  ; If we had an illegal character, fill with spaces
    dec c
    ret m

    cp '*'
    call z, paketdsk_fill_de_with_wildcard
    ld (de), a
    inc de
    call paketdsk_next_char_upper
    jr nc, paketdsk_fill_de_with_spaces
    cp ' '
    jr nz, paketdsk_normalize_filename_fragment
    call paketdsk_next_non_space_char_upper__loop
    ; jp paketdsk_fill_de_with_spaces


;--------------------------------------------------------
; fill with spaces
;
; input:
; - c: count
; - de: buffer
paketdsk_fill_de_with_spaces:
    push af
	    ld a, #20
	    call paketdsk_fill_de_with_a  ; fill with byte
    pop af
    ccf     
    ret     


;------------------------------------------------------------------------
; fill with wildcard token
;
; input:
; - c: count
; - de: buffer
paketdsk_fill_de_with_wildcard:
    ld a, '?'


;------------------------------------------------------------------------
; Fill DE C + 1 bytes with value A.
;
; input:
; - de: buffer
; - a: byte
; - c: count - 1
paketdsk_fill_de_with_a:
    inc c
paketdsk_fill_de_with_a__loop:
    dec c  ; decrement count
    ret z
    ld (de), a  ; write byte
    inc de  ; increment pointer
    jr paketdsk_fill_de_with_a__loop            


;------------------------------------------------------------------------
; Find the next character that is not a space, and converts it to upper case
;
; input:
; - b: number of bytes left
; - hl: pointer to the previous byte converted
; output:
; - b: number of characters left in the string updated
; - hl: pointing to the first non space
; - a: resulting byte converted to upper case
; - carry: success
; - no carry: we are done
paketdsk_next_non_space_char_upper:
    call paketdsk_next_char_upper
    ret nc
paketdsk_next_non_space_char_upper__loop:
    cp #20
    scf
    ret nz

    call paketdsk_next_char_upper
    jr c, paketdsk_next_non_space_char_upper__loop
    ret


;------------------------------------------------------------------------
; Convert bytes to upper case
;
; input:
; - b: number of bytes left
; - hl: pointer to the previous byte converted
; output:
; - b: decremented by one (if > 0)
; - hl: incremented by one
; - a: resulting byte converted to upper case
; - carry: success
; - no carry: we are done
paketdsk_next_char_upper:
    ld a, b
    or a
    ret z

    inc hl
    dec b
    ld a, (hl)  ; rst #20  ; firmware function: RST 4 - LOW: RAM LAM
    and #7f
    call char_to_upper_case  ; convert character to upper case
    scf     
    ret


;------------------------------------------------------------------------
; table of invalid characters
paketdsk_invalid_characters_table:
    db '<'
    db '>'
    db '.'
    db ','
    db ';'
    db ':'
    db '='
    db '['
    db ']'
    db '_'
    db '%'
    db '|'
    db '('
    db ')'
    db '/'
    db '\'
    db #7f
    db 0


;------------------------------------------------------------------------
; copy 32 bytes from HL to DE 
; hl, de, bc preserved
ldir32:
    push hl
    push de
    push bc
        ld bc, 32
        ldir    
    pop bc
    pop de
    pop hl
    ret     


;------------------------------------------------------------------------
; HL shift right by A
shift_hl_right_by_a:
    srl h
    rr l
    dec a
    jr nz, shift_hl_right_by_a         
    ret     


;------------------------------------------------------------------------
; cp HL, DE
cp_hl_de:
    push hl
	    or a
	    sbc hl, de
    pop hl
    ret     


;------------------------------------------------------------------------
; LD HL, (HL)
ld_word_from_hl:
    push de
	    ld e, (hl)
	    inc hl
	    ld d, (hl)
	    ex de, hl
    pop de
    ret     
