;------------------------------------------------------------------------
; Amstrad CPC disk file load/save library for the PAKET engine
; Santiago Ontañón, 2024

    org #0000

RAMBlock_start:

paketdsk_start_of_ram_paketdsk:

execute_ticker_flag: ds virtual 1

AMSDOS_RAM1_start:
; The values after "BIOS:" are the values observed in the BIOS at startup, just for reference.
memory_motor_spinup_delay: ds virtual 2  ; BIOS: #0032
memory_motor_spin_down_delay: ds virtual 2  ; BIOS: #00fa
memory_formatting_delay: ds virtual 1  ; BIOS: #af
memory_delay: ds virtual 1  ; BIOS: #0f
memory_track_settle_delay: ds virtual 1  ; BIOS: #0c
memory_result_phase_received_n_bytes: ds virtual 1
memory_FDC_response: ds virtual 7
; current drive/track/record:
memory_drive: ds virtual 1
memory_track: ds virtual 1
memory_record: ds virtual 1
; drive/track/record used for reading:
memory_drive_2: ds virtual 1
memory_track_2: ds virtual 1
memory_sector_2: ds virtual 1
memory_n_records_left: ds virtual 1
; drive/track/record used for writing:
memory_drive_3: ds virtual 1
memory_track_3: ds virtual 1
memory_record_3: ds virtual 1
memory_directory_record_buffer_can_be_flushed_flag: ds virtual 1  ; this indicates that 'AMSDOS_work_RAM_sector_buffer' contains a copy of the directory buffer to be flushed to disk)
memory_read_write_sector_flag: ds virtual 1  ; set fo #ff after a successful read/write
memory_motor_on_off: ds virtual 1  ; 0: off, !=0: on
memory_sector_read_buffer_ptr: ds virtual 2  ; documentation stated that this was the directory buffer ptr, but it's not correct, as it's replaced during execution to various pointers; it's, in fact, a pointer to where to store the read data
memory_sector_buffer_ptr: ds virtual 2
memory_temporary_stack: ds virtual 2
memory_retry_count: ds virtual 1  ; BIOS: #10
memory_ticker_block: ds virtual 4  ; count (2 bytes), count reset (2 bytes) (this used to be 6 bytes in the original firmware)
memory_desired_track_sector_number: ds virtual 1
memory_command: ds virtual 1
memory_sector_buffer_ptr_2: ds virtual 2
AMSDOS_RAM1_end:


; AMSDOS work RAM2:
AMSDOS_RAM2_start:
AMSDOS_work_RAM_drive: ds virtual 1
AMSDOS_work_RAM_user: ds virtual 1
AMSDOS_work_RAM_active_drive: ds virtual 1
AMSDOS_work_RAM_DPH_ptr: ds virtual 2
AMSDOS_work_RAM_default_drive_flag: ds virtual 1  ; When this is #00, the code rebuilds the block allocation table in the XDPB when it has a chance (and then sets it to #ff).
AMSDOS_work_RAM_return_address: ds virtual 2
AMSDOS_work_RAM_openin_FCB: ds virtual 36
AMSDOS_work_RAM_openout_FCB: ds virtual 36
AMSDOS_work_RAM_openin_FILEHEADER: ds virtual 74
AMSDOS_work_RAM_openout_FILEHEADER: ds virtual 74
AMSDOS_work_RAM_tmp_record_buffer: 
AMSDOS_work_RAM_tmp_filename_buffer: ds virtual 128
AMSDOS_work_RAM_unused: ds virtual 44  ; kept, so that we can copy the firmware memory area here directly with a single copy command
AMSDOS_work_RAM_XDPB_drive_a: ds virtual 64
AMSDOS_work_RAM_XDPB_drive_b: ds virtual 64
AMSDOS_work_RAM_DPH_drive_a: ds virtual 16
AMSDOS_work_RAM_DPH_drive_b: ds virtual 16
AMSDOS_work_RAM_directory_record_buffer: ds virtual 128
AMSDOS_work_RAM_sector_buffer: ds virtual 512


paketdsk_end_of_ram_paketdsk:

RAMBlock_end: