/**
 * Copyright: Ionut Popa 2016
 *
 * File: sec-io.c
 */

#include "sec-io.h"

//#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

/////////////////////////////////////////////////////////////////////////////////////
// BitStream encoding / decoding
/////////////////////////////////////////////////////////////////////////////////////

static char current_byte;
static char current_bit_pos;
static int  current_byte_pos;

/**
 * Initialize variable size integer decoder
 */
void sec_bit_stream_decode_init(
        sec_bit_stream_codec *decoder) {
    current_byte_pos = 0;
    current_bit_pos = 8;

    current_byte =
            decoder->buffer[current_byte_pos + 0] << 0;/* |
            decoder->buffer[current_byte_pos + 1] << 8 |
            decoder->buffer[current_byte_pos + 2] << 16|
            decoder->buffer[current_byte_pos + 3] << 24;*/
    current_byte_pos += 1;
}


/**
 * Bitmasks filled with 1
 * 0 -> ...0
 * 1 -> ...1
 * 2 -> ..11
 * 3 -> .111
 * ...
 * n -> 2^n - 1
 */
static int mask[] =
        {0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767};


/**
 * Retrieve an integer of bit_count from the data buffer
 * See for alternative: https://github.com/Cyan4973/FiniteStateEntropy/blob/master/lib/bitstream.h
 */
long sec_bit_stream_decode_integer(
        sec_bit_stream_codec *decoder,
        int bit_count) {

    long result = 0;
    int offset = 0;
    if (bit_count < 0) {
        return 0;
    }

    do {
        if (bit_count < current_bit_pos) {
            result |= (current_byte & mask[bit_count]) << offset;
            current_bit_pos -= bit_count;
            current_byte >>= bit_count;
            break;
        } else {
            result |= (current_byte & mask[current_bit_pos]) << offset;
            bit_count -= current_bit_pos;
            offset += current_bit_pos;
            current_byte = decoder->buffer[current_byte_pos + 0] << 0;/* |
                          decoder->buffer[current_byte_pos + 1] << 8 |
                          decoder->buffer[current_byte_pos + 2] << 16|
                          decoder->buffer[current_byte_pos + 3] << 24;*/
            current_byte_pos += 1;
            current_bit_pos = 8;
        }
    } while (1);

    //decoder->current_bit_pos  = current_bit_pos;
    //decoder->current_byte_pos = current_byte_pos;
    //decoder->current_byte    = current_byte;

    return result;
}


void sec_bit_stream_encode_init(
        sec_bit_stream_codec *encoder) {

    encoder->current_byte = 0;
    encoder->current_bit_pos = 0;
}


void sec_bit_stream_encode_integer(
        sec_bit_stream_codec *encoder,
        int val,
        int bit_count) {

    //printf("Code: %d/%d\n", val, bit_count);
    do {
        encoder->current_byte |= ((val << encoder->current_bit_pos) & 0xFF);
        if (encoder->current_bit_pos + bit_count > 8) {
            bit_count -= (8 - encoder->current_bit_pos);
            val >>= (8 - encoder->current_bit_pos);
            encoder->current_bit_pos += (8 - encoder->current_bit_pos);
        } else {
            encoder->current_bit_pos += bit_count;
            val >>= bit_count;
            bit_count = 0;
        }

        if (encoder->current_bit_pos == 8) {
            encoder->buffer[encoder->current_byte_pos++] = (byte) encoder->current_byte;
            encoder->current_bit_pos = 0;
            encoder->current_byte = 0;
        }
    } while (bit_count > 0);
}


byte * sec_bit_stream_encode_flush(
        sec_bit_stream_codec *encoder) {

    if (encoder->current_byte_pos != 0) {
        encoder->buffer[encoder->current_byte_pos++] = (byte) encoder->current_byte;
    }
    return encoder->buffer;
}
