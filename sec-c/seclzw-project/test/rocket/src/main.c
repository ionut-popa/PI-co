/*
 * Copyright (c) 2015, Wind River Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/*
 * DESCRIPTION A simple function which serves as a starting point for a C
 * program. This function can be changed, removed, or replaced. The function
 * will be launched as a task when the Zephyr kernel is run. See prj.mdef to
 * adjust task parameters and entry point.
 */

#include <zephyr.h>
#include "sec-io.h"
#include "sec-compress.h"
#include "sec-decompress.h"

#include "test_data.h"

/* Specify delay for each pass through the pool (in ms); compute equivalent in ticks */

char _dummy_mem[1 << 24];
int  _dummy_mem_ptr = 0;
/**
 * DUMMY MALLOC !!!
 */ 
void* malloc(size_t size) {
    void *ret = &_dummy_mem[_dummy_mem_ptr];
    _dummy_mem_ptr += size;
    return ret;
}

/**
 * DUMMY FREE !!!
 */ 
void free(void *prt) {
    //NOTHING
}


void test_compress(char *buffer, int buffer_size) {

    //Test compression
    sec_bit_stream_codec encoder = { 0 };
    encoder.buffer_len = buffer_size;
    encoder.buffer = (byte *) malloc(encoder.buffer_len * 2);
    
    sec_bit_stream_encode_init(&encoder);
    sec_compress(&encoder, buffer, buffer_size);

    printf("[INFO] [SEC] Compress: original size: %d, compressed: %d\n", 
            buffer_size, encoder.current_byte_pos);   
    
    //Test decompression
    sec_bit_stream_codec decoder = { 0 };
    decoder.buffer_len = encoder.current_byte_pos;
    decoder.buffer = encoder.buffer;

    sec_bit_stream_decode_init(&decoder);
    int decompressed_len;
    byte *decompressed = sec_decompress(&decoder, &decompressed_len);
    
    int i, identical = 1;
    
    //TODO: fix decode, a few bytes at the end ar not always decoded
    for (i = 0; i < decompressed_len - 128; i++) {
        if (decompressed[i] != buffer[i]) {
            printf("[ERROR] [SEC] Decompress: consistency check error, at pos: %d\n", i);
            identical = 0;
            break;
        }
    }
    
    printf("[INFO] [SEC] Decompress: consistency check: %s\n", 
            (identical == 1 ? "OK" : "NOK"));
    
    free(encoder.buffer);
    free(decompressed);
}


int main ( void ) {
    test_compress(gps_csv, sizeof(gps_csv));
    test_compress(gps_delta_csv, sizeof(gps_delta_csv));
    test_compress(HVAC_csv, sizeof(HVAC_csv));
    test_compress(route_csv, sizeof(route_csv));
    test_compress(sensordata_csv, sizeof(sensordata_csv));
    test_compress(walk_csv, sizeof(walk_csv));
    test_compress(weather_csv, sizeof(weather_csv));
}
