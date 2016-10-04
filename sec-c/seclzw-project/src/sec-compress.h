/**
 * Copyright: Ionut Popa 2016
 */

#include "sec-io.h"

#ifndef _SEC_COMPRESS_H
#define _SEC_COMPRESS_H

#define SEC_SYNC_SIZE       1
#define SEC_DICTIONARY_SIZE (1 << 16)
#define SEC_ALPHABET_SIZE   256

int dictionary_size[SEC_SYNC_SIZE];

/**
 * @brief Data structure inspired by: http://warp.povusers.org/EfficientLZW/part4.html
 *        first               -> it's the index in the dictionary of the "first" token having the same preffix as current node
 *        index_brother_left  -> it's the index in the dictionary of the "next" token having the symbol < symbol
 *        index_brother_right -> it's the index in the dictionary of the "next" token having the symbol > symbol
 *        the data token is defined by (position_in_stream & lenght)
 */
typedef struct _sec_block {
    int     parent_index;
    int     first;
    int     index_brother_left;
    int     index_brother_right;
    byte    symbol;
    //int     position_in_stream;
    int     lenght;
    int     index_reduced_dictionary[SEC_SYNC_SIZE + 1];
    int     index_dictionary_root[SEC_SYNC_SIZE + 1];
    int     siblings;                               //number of total elements having this node as preffix
} sec_block;


/**
 * @brief dictionary
 */
sec_block dictionary[SEC_DICTIONARY_SIZE + 1];


/**
 * @brief sec_dictionary_init
 */
void sec_dictionary_init();


void sec_block_log(
        char *info,
        sec_block *b);


sec_block * sec_dictionary_search(
        sec_block *parent,
        byte symbol);


/**
 * @brief sec_dictionary_insert
 * @param parent
 * @param symbol
 * @return
 */
sec_block * sec_dictionary_insert(
        sec_block *parent,
        byte symbol);

#endif
