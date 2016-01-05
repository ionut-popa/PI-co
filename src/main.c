/*

 Free Software
 Copyright: ionut.popa@gmail.com

*/

#include <stdio.h>
#include <stdlib.h>



/*
 *
 *
 *
 */








/**
 * A node element for a preffix tree
 */
struct se_block {
    unsigned char   c;
    struct se_block *children[256];
    int             level;
    int             childrens;

    int             indexInDictionary;
};


/**
 * A dictionary is a ROOT se_block.
 * We maintain a list of dictionary, one for each char
 *
 * For each dictionary - we keep track of total number of items
 */

struct se_block root;

static void sec_init_dictionary(struct se_block *root) {
    unsigned int c, c1;
    memset(root, 0, sizeof(struct se_block));
    for (c = 0; c < 256; c++) {
        struct se_block *child = (struct se_block *) calloc(1, sizeof(struct se_block));
        child->c = c;
        child->level = 0;
        root->childrens++;
        root->children[c] = child;

        for (c1 = 0; c1 < 256; c1++) {
            struct se_block *child1 = (struct se_block *) calloc(1, sizeof(struct se_block));
            child1->c = c1;
            child1->level = 1;
            root->childrens++;
            child->childrens++;
            child->children[c1] = child1;
        }
    }
}

