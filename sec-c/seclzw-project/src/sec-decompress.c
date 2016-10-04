/**
 * Copyright: Ionut Popa 2016
 */

//#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include "sec-compress.h"
#include "sec-io.h"


/////////////////////////////////////////////////////////////////////////////////////
// SEC ditionary compression
/////////////////////////////////////////////////////////////////////////////////////

//#define LOGGING

byte* sec_decompress(sec_bit_stream_codec *codec, int *_output_lenght) {
    //remapping of indices
	//TODO: This should be much higher !!! [256][1<<16 + 1]
    unsigned short dictionary_synch1[256][8192];
    int position = 0, i;
    sec_block   *current_match  = NULL;
    sec_block   *prev_match     = NULL;
    int         synch_size      = 0;
    sec_block   *best_block     = 0;
    int         best_sync       = -1;

    sec_block   *block_to_add   = NULL;
    byte        symbol_to_add   = 0;

    sec_dictionary_init();

    int b1 = sec_bit_stream_decode_integer(codec, 8);
    int b2 = sec_bit_stream_decode_integer(codec, 8);
    int b3 = sec_bit_stream_decode_integer(codec, 8);
    int b4 = sec_bit_stream_decode_integer(codec, 8);

    int output_lenght = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    *_output_lenght = output_lenght;
    byte *output = (byte *) malloc(output_lenght);

    for (i = 0; i < SEC_SYNC_SIZE; i++) {
        byte val = sec_bit_stream_decode_integer(codec, 8);
        output[position++] = val;
    }

    while (position < output_lenght) {
        if (dictionary[SEC_DICTIONARY_SIZE].siblings == SEC_DICTIONARY_SIZE) {
            sec_dictionary_init();
            block_to_add = NULL;
        }

        int synch_1_found = 0;
        int best_sync     = 0;
        //search for the best combination: synch size & dictionary item
        for (synch_size = 1; synch_size <= SEC_SYNC_SIZE; synch_size++) {
            int temp_position = position - synch_size;
            current_match = &dictionary[output[temp_position++]];
            //sec_block_log("CURRENT MATCH:", current_match);
            //if (current_match->siblings > 0) {
                synch_1_found = 1;
            //}
        }

        //if (synch_1_found == 1) {
            best_sync = sec_bit_stream_decode_integer(codec, 1);
        //}
        int index = 0, bits;
        if (best_sync == 0) {
            bits = log2int(dictionary[SEC_DICTIONARY_SIZE].siblings);
        } else {
            bits = log2int(dictionary[output[position - best_sync]].siblings);
        }
        index = sec_bit_stream_decode_integer(
                codec,
                bits);
        //printf("Index: %d, Bits:%d, Synch: %d\n", index, bits, best_sync);

        //depending on sync size search the block in a different dictionary
        //sync=0 -> full dictionary
        //sync=1 -> reduced dictionary

        if (best_sync == 0) {
            best_block = &dictionary[index];
        } else {
            byte sync = output[position - best_sync];
            best_block = &dictionary[dictionary_synch1[sync][index]];
        }
        //sec_block_log("Best block:", best_block);

        //restore char sequence from dictionary index
        sec_block *c = best_block;
        int idx = best_block->lenght;
        do {
            output[position + (--idx) - best_sync] = c->symbol;
            if (c->parent_index >= 0) {
                c = &dictionary[c->parent_index];
            }
        } while (c->parent_index >= 0);


        //update dictionary
        sec_block *insert = NULL;
        if (block_to_add != NULL) {
            symbol_to_add = output[position];
            insert = sec_dictionary_insert(block_to_add, symbol_to_add);
            dictionary_synch1[insert->index_dictionary_root[1]][insert->index_reduced_dictionary[1]] = insert->index_reduced_dictionary[0];
            //printf("insert:, ");
            //sec_block_to_string(insert);
            //printf("\n");
        } else {
            //printf("Insert not\n");
        }
        block_to_add = best_block;

        position += best_block->lenght - best_sync;
        //printf("Output: %d, %s\n", best_sync, output);
        //printf("------------------\n");
    }
    return output;
}
