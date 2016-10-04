/**
 *
 */
#include "sec-io.h"
#include "sec-compress.h"
#include "sec-decompress.h"

/*
Dictionary during compression
=============================
    a -> index_reduced_dictionary[0] = 97
      -> index_reduced_dirctionary[1] = -1
      -> siblings = 0

    ab -> index_reduced_dictionary[0] = 256
       -> index_reduced_dictionary[1] = 1 ;//parent->index_reduced_dictionary[0]->siblings++; 1; a->siblings = 1
       -> index_dictionary_root[0] = parent->index_dictionary_root[0] (if len > 1)
       -> index_dictionary_root[1] = parent->index_reduced_dictionary[0] (if len == 1);
       -> index_dictionary_root[2] = this;

    abc-> index_reduced_dictionary[0] = 257; parent->index_dictionary_root[0]
       -> index_reduced_dictionary[1] = 2; parent->index_dictionary_root[0].siblings//parent->index_reduced_dictionary[0] (parent->parent->siblings++)
       -> index_reduced_dictionary[2] = 1 (parent->siblings++);


Dictionary during decompression
===============================
     1 ->
        preffix[0] = 1

        preffix[1] = a -> ab
                     b -> ba

        preffix[2] = ab -> abc
                        -> abb
     2 -> 2, abc, abb

     97 -> a
     98 -> b
    256 -> ab
              [1] -> abc
              [2] -> abb
    257 -> abc
    258 -> ba
    259 -> abb

 */

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <errno.h>


#define GCC_VERSION (__GNUC__ * 100 + __GNUC_MINOR__)

#if (GCC_VERSION >= 302) || (__INTEL_COMPILER >= 800) || defined(__clang__)
#  define expect(expr,value)    (__builtin_expect ((expr),(value)) )
#else
#  define expect(expr,value)    (expr)
#endif

#define likely(expr)     expect((expr) != 0, 1)
#define unlikely(expr)   expect((expr) != 0, 0)



void sec_compress_test(sec_bit_stream_codec *codec, byte *input, int input_length) {
    sec_dictionary_init();
    sec_block *a = &dictionary['a'];
    sec_block *b = &dictionary['b'];
    sec_block *ad = sec_dictionary_insert(a, 'd');
    sec_block *ac = sec_dictionary_insert(a, 'c');
    sec_block *ab = sec_dictionary_insert(a, 'b');
    sec_block *abc = sec_dictionary_insert(ab, 'c');
    sec_block *aba = sec_dictionary_insert(ab, 'a');
    sec_block *abb = sec_dictionary_insert(ab, 'b');
    sec_block *abq = sec_dictionary_insert(ab, 'q');
    sec_block *abz = sec_dictionary_insert(ab, 'z');
    sec_block *abk = sec_dictionary_insert(ab, 'k');
    sec_block *abg = sec_dictionary_insert(ab, 'g');
    sec_block *aa = sec_dictionary_insert(a, 'a');
    sec_block *af = sec_dictionary_insert(a, 'f');
    sec_block *ae = sec_dictionary_insert(a, 'e');
    sec_block *bb = sec_dictionary_insert(b, 'b');
    sec_block *ba = sec_dictionary_insert(b, 'a');
    sec_block *bac = sec_dictionary_insert(ba, 'c');
    sec_block_log("root", &dictionary[SEC_DICTIONARY_SIZE]);
    sec_block_log("a", a);
    sec_block_log("aa", aa);
    sec_block_log("ab", ab);
    sec_block_log("ac", ac);
    sec_block_log("ad", ad);
    sec_block_log("ae", ae);
    sec_block_log("af", af);
    sec_block_log("aba", aba);
    sec_block_log("abb", abb);
    sec_block_log("abc", abc);
    sec_block_log("abg", abg);
    sec_block_log("abk", abk);
    sec_block_log("abq", abq);
    sec_block_log("abz", abz);
    sec_block_log("b", b);
    sec_block_log("bb", bb);
    sec_block_log("ba", ba);
    sec_block_log("bac", bac);

    printf("====================================================================\n");

    sec_block *abg_search = sec_dictionary_search(ab, 'g');
    sec_block_log("abg_search", abg_search);
    sec_block *abx_search = sec_dictionary_search(ab, 'x');
    sec_block_log("abx_search", abx_search);
    sec_block *bac_search = sec_dictionary_search(ba, 'c');
    sec_block_log("bac_search", bac_search);
    sec_block *ae_search = sec_dictionary_search(a, 'e');
    sec_block_log("ae_search", ae_search);
}


int main(int argc, char *argv[])
{
    int fd, offset;
    byte *data;
    struct stat sbuf;

    if (argc != 4) {
        fprintf(stderr, "usage: %s [-c|-d] <file-in> <file-out>\n", argv[0]);
        exit(1);
    }

    if ((fd = open(argv[2], O_RDONLY)) == -1) {
        perror("open");
        exit(1);
    }

    if (stat(argv[2], &sbuf) == -1) {
        perror("stat");
        exit(1);
    }

    data = mmap((caddr_t) 0, sbuf.st_size, PROT_READ, MAP_SHARED, fd, 0);

    if ((int) data == (caddr_t)(-1)) {
        perror("mmap");
        exit(1);
    }

    if (strcmp("-c", argv[1]) == 0) {
        // Initialize the encoder and allocate space for compressed data buffer
        sec_bit_stream_codec encoder = { 0 };
        encoder.buffer_len = sbuf.st_size;
        encoder.buffer = (byte *) malloc(encoder.buffer_len * 2);
        sec_bit_stream_encode_init(&encoder);

        printf("Start encoding\n");
        sec_compress(&encoder, data, sbuf.st_size);
        printf("End encoding: %d\n", encoder.current_byte_pos);

        FILE *out = fopen(argv[3], "wb");

        if (out){
            fwrite(encoder.buffer, encoder.current_byte_pos, 1, out);
        }

        //fclose(out);
    }
    if (strcmp("-d", argv[1]) == 0) {
        sec_bit_stream_codec decoder = { 0 };
        decoder.buffer_len = sbuf.st_size;
        decoder.buffer = data;

        sec_bit_stream_decode_init(&decoder);
        printf("Start decoding\n");
        int bufferLen;
        byte *buffer = sec_decompress(&decoder, &bufferLen);
        printf("End decoding");
        FILE *out = fopen(argv[3], "wb");
        if (out){
            fwrite(buffer, bufferLen, 1, out);
        }

        fclose(out);
    }

    return 0;
}
