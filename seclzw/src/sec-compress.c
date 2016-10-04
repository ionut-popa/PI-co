/**
 * Copyright: Ionut Popa 2016
 */

#include "sec-io.h"
#include "sec-compress.h"

//#include <math.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>


/////////////////////////////////////////////////////////////////////////////////////
// SEC ditionary compression
/////////////////////////////////////////////////////////////////////////////////////


/**
 * @brief sec_dictionary_init
 */
void sec_dictionary_init() {
    int i = 0, j = 0;

    memset(dictionary, 0,  (SEC_DICTIONARY_SIZE + 1) * sizeof(sec_block));

    dictionary[SEC_DICTIONARY_SIZE].first = 0;
    dictionary[SEC_DICTIONARY_SIZE].index_brother_left = -1;
    dictionary[SEC_DICTIONARY_SIZE].index_brother_right = -1;
    dictionary[SEC_DICTIONARY_SIZE].parent_index = -1;
    dictionary[SEC_DICTIONARY_SIZE].lenght = 0;
    dictionary[SEC_DICTIONARY_SIZE].symbol = 0;
    dictionary[SEC_DICTIONARY_SIZE].siblings = 0;
    for (j = 0; j <= SEC_SYNC_SIZE; j++) {
        dictionary[SEC_DICTIONARY_SIZE].index_dictionary_root[j] = -1;
        dictionary[SEC_DICTIONARY_SIZE].index_reduced_dictionary[j] = -1;
    }

    for (i = 0; i < SEC_ALPHABET_SIZE; i++) {
        dictionary[i].parent_index = SEC_DICTIONARY_SIZE;
        dictionary[i].first = -1;
        dictionary[i].index_brother_left = -1;
        dictionary[i].index_brother_right = -1;
        dictionary[i].symbol = i;
        dictionary[i].lenght = 1;
        dictionary[i].index_reduced_dictionary[0] = i;
        dictionary[i].index_dictionary_root[0] = SEC_DICTIONARY_SIZE;
        for (j = 1; j <= SEC_SYNC_SIZE; j++) {
            dictionary[i].index_reduced_dictionary[j] = -1;
        }
        dictionary[dictionary[i].index_dictionary_root[0]].siblings++;
    }

//    for (i = SEC_ALPHABET_SIZE; i < SEC_DICTIONARY_SIZE; i++) {
//        dictionary[i].siblings = 0;
//    }
}


void sec_block_log(
        char *info,
        sec_block *b) {

    if (b != NULL) {
        int i = 0;
        printf("[DEBUG] %s -> siblings: %d, lenght: %d, index:", info, b->lenght, b->siblings);
        for (i = 0; i <= SEC_SYNC_SIZE; i++) {
            printf("[%d]=%d/%d,", i, b->index_reduced_dictionary[i], dictionary[b->index_dictionary_root[i]].siblings);
        }
        char *seq = calloc(b->lenght + 1, 1);
        sec_block *c = b;
        int idx = b->lenght;
        do {
            seq[--idx] = c->symbol;
            if (c->parent_index >= 0) {
                c = &dictionary[c->parent_index];
            }
        } while (c->parent_index >= 0);
        printf(" symbol: %d, parent: %d, sequence: %s\n", b->symbol, b->parent_index, seq);
    } else {
        printf("[ERROR] %s, is NULL\n", info);
    }
}


void sec_block_to_string(
        sec_block *b) {

    if (b != NULL) {
        char *seq = calloc(b->lenght + 1, 1);
        sec_block *c = b;
        int idx = b->lenght;
        do {
            seq[--idx] = c->symbol;
            if (c->parent_index >= 0) {
                c = &dictionary[c->parent_index];
            }
        } while (c->parent_index >= 0);
        printf("%s", seq);
    }
}


sec_block * sec_dictionary_search(
        sec_block *parent,
        byte symbol) {
    //if the parent has no children (first
    int current_idx = parent->first;
    while (current_idx != -1) {
        sec_block *current = &dictionary[current_idx];
        //sec_block_log("--->search:", current);
        if (current->symbol > symbol) {
            current_idx = current->index_brother_right;
        } else if (current->symbol < symbol) {
            current_idx = current->index_brother_left;
        } else if (current->symbol == symbol) {
            return current;
        }
    }
    return NULL;
}


/**
 * @brief sec_dictionary_insert
 * @param parent
 * @param symbol
 * @return
 */
sec_block * sec_dictionary_insert(
        sec_block *parent,
        byte symbol) {
    sec_block   *ret = NULL;
    int         i = 0;

    //if the parent has no children (first
    int current_idx = parent->first;
    int new_idx;
    if (current_idx == -1) {
        new_idx = dictionary[parent->index_dictionary_root[0]].siblings++;
        ret = &dictionary[new_idx];
        parent->first = new_idx;
        ret->parent_index = parent->index_reduced_dictionary[0];
    } else {
        while (current_idx != -1) {
            sec_block *current = &dictionary[current_idx];
            if (current->symbol < symbol) {
                current_idx = current->index_brother_left;
                if (current_idx == -1) {
                    new_idx = dictionary[parent->index_dictionary_root[0]].siblings++;
                    ret = &dictionary[new_idx];
                    current->index_brother_left = new_idx;
                    ret->parent_index = current->parent_index;
                }
            } else if (current->symbol > symbol) {
                current_idx = current->index_brother_right;
                if (current_idx == -1) {
                    new_idx = dictionary[parent->index_dictionary_root[0]].siblings++;
                    ret = &dictionary[new_idx];
                    current->index_brother_right = new_idx;
                    ret->parent_index = current->parent_index;
                }
            } else if (current->symbol == symbol) {
                return current;
            }
        }
    }

    ret->first = -1;
    ret->index_brother_left = -1;
    ret->index_brother_right = -1;
    ret->symbol = symbol;
    ret->lenght = parent->lenght + 1;

    ret->index_reduced_dictionary[0] = new_idx;
    ret->index_dictionary_root[0] = parent->index_dictionary_root[0];

    for (i = 1; i <= SEC_SYNC_SIZE; i++) {
        if (ret->lenght > 2) {
            ret->index_dictionary_root[i] = parent->index_dictionary_root[1];
        } else if (ret->lenght == 2) {
            ret->index_dictionary_root[i] = parent->index_reduced_dictionary[0];
        }

        ret->index_reduced_dictionary[i] = dictionary[ret->index_dictionary_root[i]].siblings;
        dictionary[ret->index_dictionary_root[i]].siblings++;
    }

    return ret;
}

//#define LOGGING

int sync_count[2] = {0};

void sec_compress(sec_bit_stream_codec *codec, byte *input, int input_length) {
    int position = 0, i;
    sec_block   *current_match  = NULL;
    sec_block   *prev_match     = NULL;
    int         synch_size      = 0;
    sec_block   *best_block     = 0;
    int         best_sync       = -1;

    sec_block   *block_to_add0_next = NULL;
    byte        symbol_to_add0_next = 0;

    sec_dictionary_init();

    //first - encode file length, 32 bits
    byte b1 = (byte) ((input_length << 0)  >> 24);
    byte b2 = (byte) ((input_length << 8)  >> 24);
    byte b3 = (byte) ((input_length << 16) >> 24);
    byte b4 = (byte) ((input_length << 24) >> 24);

    sec_bit_stream_encode_integer(codec, b1, 8);
    sec_bit_stream_encode_integer(codec, b2, 8);
    sec_bit_stream_encode_integer(codec, b3, 8);
    sec_bit_stream_encode_integer(codec, b4, 8);

    for (i = 0; i < SEC_SYNC_SIZE; i++) {
        sec_bit_stream_encode_integer(codec, input[position], 8);
        position++;
    }

    while (position < input_length) {
        int      best_score      = (1 << 24);//very large
        int      score           = (1 << 24);

        if (dictionary[SEC_DICTIONARY_SIZE].siblings >= SEC_DICTIONARY_SIZE) {
            sec_dictionary_init();
            block_to_add0_next = NULL;
        }

        int synch_1_found = 0;
        //search for the best combination: synch size & dictionary item
        for (synch_size = 0; synch_size <= SEC_SYNC_SIZE; synch_size++) {
            int temp_position = position - synch_size;
            current_match = &dictionary[input[temp_position++]];
            //if (synch_size == 1 && current_match->siblings > 1) {
            //    synch_1_found = 1;
            //}
            int level = 0;
            score = 1;
            while (1) {
                prev_match = current_match;
                current_match = sec_dictionary_search(current_match, input[temp_position++]);

                if (current_match == NULL) {
                    break;
                }
            }
            //ratio of:
            // - the bites requred to encode a block: log2(total number of dictionary items starting with a synch word)
            // - the total number of symbols encoded by this block: block lenght - sync
            if (prev_match->lenght > synch_size && dictionary[prev_match->index_dictionary_root[synch_size]].siblings > 0) {
                score = (1000 - 1000 * synch_size + 1000 * log2int(dictionary[prev_match->index_dictionary_root[synch_size]].siblings)) /
                        (prev_match->lenght - synch_size);
            } else {
                score = (2 << 16);
            }
            if (score < best_score) {
                best_score = score;
                best_block = prev_match;
                best_sync = synch_size;
            }
        }
        sync_count[best_sync]++;
        //if (synch_1_found == 1) {
            sec_bit_stream_encode_integer(codec, best_sync, 1);
        //}
        sec_bit_stream_encode_integer(
                    codec,
                    best_block->index_reduced_dictionary[best_sync],
                    log2int(dictionary[best_block->index_dictionary_root[best_sync]].siblings));

        sec_block *insert = NULL;
        if (block_to_add0_next != NULL) {
            insert = sec_dictionary_insert(block_to_add0_next, symbol_to_add0_next);
        } else {
            //printf("Insert not\n");
        }
        block_to_add0_next = best_block;
        symbol_to_add0_next = input[position + best_block->lenght - best_sync];

        position += best_block->lenght - best_sync;
    }
    printf("Synch: 0: %d\n", sync_count[0]);
    printf("Synch: 1: %d\n", sync_count[1]);
}
