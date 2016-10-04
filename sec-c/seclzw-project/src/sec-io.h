/**
 * Copyright: Ionut Popa 2016
 *
 * File: sec-io.h
 */

#ifndef _SEC_IO_H
#define _SEC_IO_H

//#define FAST_LOG2(x) (sizeof(int) * 8 - 1 - __builtin_clz((int) (x) ) )
//#define FAST_LOG2_UP(x) (((x) - (1 << FAST_LOG2(x))) ? FAST_LOG2(x) + 1 : FAST_LOG2(x))

static  int log2int(int x) {
//    return ceil(log2(x));
//    return FAST_LOG2_UP(x);
    return (31 - __builtin_clz(x)) + (!!(x & (x - 1)));
}


typedef unsigned char byte;


typedef struct _sec_bit_stream_codec {
    byte    *buffer;
    int     buffer_len;
    long    current_byte;
    int     current_bit_pos;
    int     current_byte_pos;
} sec_bit_stream_codec;


void sec_bit_stream_decode_init(
        sec_bit_stream_codec *decoder);


long sec_bit_stream_decode_integer(
        sec_bit_stream_codec *decoder,
        int bit_count);


void sec_bit_stream_encode_init(
        sec_bit_stream_codec *encoder);


void sec_bit_stream_encode_integer(
        sec_bit_stream_codec *encoder,
        int val,
        int bit_count);


byte * sec_bit_stream_encode_flush(
        sec_bit_stream_codec *encoder);

#endif
