/*
 * Fast LZ-SEC compression
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


typedef unsigned char byte;


typedef struct _Match {
    int pos;
    int len;
} Match;


typedef struct _ICodec {
    byte    *buffer;
    int     bufferLen;
    long    currentByte;
    int     currentBitPos;
    int     currentBytePos;
} ICodec;


int window                              = (16 * 1024);
int window_1                            = (16 * 1024 - 1);
int windowBits                          = 14;
int lookahead                           = 16;
int lookaheadBits                       = 4;

int  monogramCount[256]                 = { 0 };
int  monogramIndexAdd[256]              = { 0 };
int  monogramIndexRemove[256]           = { 0 };
int  monogramPositions[256][32 * 1024]  = { 0 };

#define FAST_LOG2(x) (sizeof(int) * 8 - 1 - __builtin_clz((int) (x) ) )
#define FAST_LOG2_UP(x) (((x) - (1 << FAST_LOG2(x))) ? FAST_LOG2(x) + 1 : FAST_LOG2(x))

static inline int log2int(int x) {
//    if (val == 0) return 1000000;
//    if (val == 1) return 0;
//    int ret = 0;
//    while (val >= 1) {
//        val >>= 1;
//        ret++;
//    }
//    return ret;
//    return ceil(log2(x));
//    return FAST_LOG2_UP(x);
    return ((sizeof(unsigned int) * 8 - 1) - __builtin_clz(x)) + (!!(x & (x - 1)));
}

/////////////////////////////////////////////////////////////////////////////////////
// BitStream encoding / decoding
/////////////////////////////////////////////////////////////////////////////////////

static int currentByte;
static int currentBitPos;
static int currentBytePos;

/**
 * Initialize variable size integer decoder
 */
void decodeInit(ICodec *decoder) {
    currentBytePos = 0;
    currentBitPos = 32;

    currentByte =
            decoder->buffer[currentBytePos + 0] << 0 |
            decoder->buffer[currentBytePos + 1] << 8 |
            decoder->buffer[currentBytePos + 2] << 16|
            decoder->buffer[currentBytePos + 3] << 24;
    currentBytePos += 4;

    memset(monogramIndexAdd, 0, 256 * sizeof(int));
    memset(monogramIndexRemove, 0, 256 * sizeof(int));
    memset(monogramPositions, 0, 256 * window * sizeof(int));
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
int mask[] = {0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767};


/**
 * Retrieve an integer of totalBits from the data buffer
 * See for alternative: https://github.com/Cyan4973/FiniteStateEntropy/blob/master/lib/bitstream.h
 */
inline long decodeInteger(ICodec *decoder, int totalBits) {
    long result = 0;
    int offset = 0;
    if (totalBits < 0) {
        return 0;
    }

    do {
        if (totalBits < currentBitPos) {
            result |= (currentByte & mask[totalBits]) << offset;
            currentBitPos -= totalBits;
            currentByte >>= totalBits;
            break;
        } else {
            result |= (currentByte & mask[currentBitPos]) << offset;
            totalBits -= currentBitPos;
            offset += currentBitPos;
            currentByte = decoder->buffer[currentBytePos + 0] << 0 |
                          decoder->buffer[currentBytePos + 1] << 8 |
                          decoder->buffer[currentBytePos + 2] << 16|
                          decoder->buffer[currentBytePos + 3] << 24;
            currentBytePos += 4;
            currentBitPos = 32;
        }
    } while (1);

    //decoder->currentBitPos  = currentBitPos;
    //decoder->currentBytePos = currentBytePos;
    //decoder->currentByte    = currentByte;

    return result;
}


void encodeInit(ICodec *encoder) {
    encoder->currentByte = 0;
    encoder->currentBitPos = 0;
    memset(monogramCount, 0, 256 * sizeof(int));
    memset(monogramIndexAdd, 0, 256 * sizeof(int));
    memset(monogramIndexRemove, 0, 256 * sizeof(int));
    memset(monogramPositions, 0, 256 * window * sizeof(int));
}


void encodeInteger(ICodec *encoder, int val, int totalBits) {
    //printf("Code: %d/%d\n", val, totalBits);
    do {
        encoder->currentByte |= ((val << encoder->currentBitPos) & 0xFF);
        if (encoder->currentBitPos + totalBits > 8) {
            totalBits -= (8 - encoder->currentBitPos);
            val >>= (8 - encoder->currentBitPos);
            encoder->currentBitPos += (8 - encoder->currentBitPos);
        } else {
            encoder->currentBitPos += totalBits;
            val >>= totalBits;
            totalBits = 0;
        }

        if (encoder->currentBitPos == 8) {
            encoder->buffer[encoder->currentBytePos++] = (byte) encoder->currentByte;
            encoder->currentBitPos = 0;
            encoder->currentByte = 0;
        }
    } while (totalBits > 0);
}


byte * encodeFlush(ICodec *encoder) {
    if (encoder->currentBytePos != 0) {
        encoder->buffer[encoder->currentBytePos++] = (byte) encoder->currentByte;
    }
    return encoder->buffer;
}


/////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////

Match findMatch(byte input[], int startSearchPosition, int maxLenght, int _window, int sync) {
    int maxLenghtFound      = -1;
    int posMaxLenghtFound   = 0;
    int p, j;

    if (startSearchPosition > 0) {
        int monogramStart = input[startSearchPosition];
        //printf("Monogram count:%d, monogramStart: %d\n", monogramCount[monogramStart], monogramStart);
        for (p = 0; p < monogramCount[monogramStart]; p++) {
            int i = monogramPositions[monogramStart][(monogramIndexRemove[monogramStart] + p ) % window];
            if (i > startSearchPosition - maxLenght) {
                break;
            }

            for (j = 0; j < maxLenght; j++) {
                if (input[i + j] == input[startSearchPosition + j]) {
                    if (j > maxLenghtFound) {
                        maxLenghtFound = j;
                        if (sync == 1) {
                            posMaxLenghtFound = p;
                        } else {
                            posMaxLenghtFound = i;
                        }
                    }
                } else {
                    break;
                }
            }
        }
    }
    Match m = {posMaxLenghtFound, maxLenghtFound + 1};
    return m;
}


static byte* compress3(byte input[], int inputLength, ICodec *encoder)  {
    int len = 0, i;

    int position = 0;

    //first - encodeInteger file length
    byte b1 = (byte) (inputLength >> 24);
    byte b2 = (byte) ((inputLength << 8) >> 24);
    byte b3 = (byte) ((inputLength << 16) >> 24);
    byte b4 = (byte) ((inputLength << 24) >> 24);

    encodeInteger(encoder, b1 & 0xFF, 8);
    encodeInteger(encoder, b2 & 0xFF, 8);
    encodeInteger(encoder, b3 & 0xFF, 8);
    encodeInteger(encoder, b4 & 0xFF, 8);

    //Block format:
    // [Match length]{If Match length > 0}{[sync][Match pos]}[Literal]

    while (position < inputLength - lookahead- 1) {
        int oldPosition = position;

        int m = input[position];

        Match match1 = findMatch(input, position, lookahead - 1, window, 0);
        //Match match2 = {0,0};int sync = 0;///Length:5936749
        Match match2 = findMatch(input, position - 1, lookahead, window - 1, 1);int sync = 1;

        double score1 = (log2(lookahead) + 1 + log2(window) + 8) / match1.len;
        double score2 = (log2(lookahead) + 1 + log2(monogramCount[m]) + 8) / (match2.len - 1);

        if (match1.len <= 0) {
            score1 = 10000000;
        }
        if (match2.len <= 2) {
            score2 = 10000000;
        }

        if (score1 <= score2) {
            int oldLen = len;
            int MatchPos = match1.pos;
            if (position - window < 0 || match1.len == 0) {
                // Nothing
            } else {
                MatchPos = MatchPos - (position - window);
            }

            if (match1.len != 0) {
                len += log2(lookahead) + sync + log2(window) + 8 ;
                encodeInteger(encoder, match1.len /*Match length*/, lookaheadBits);
                if (sync > 0) {
                    encodeInteger(encoder, 0 /*sync*/, 1);
                }
                encodeInteger(encoder, MatchPos /*Match pos*/, windowBits);
            } else {
                len += log2(lookahead) + 8;
                encodeInteger(encoder, match1.len /*Match length*/, lookaheadBits);
            }
            position += match1.len;
            encodeInteger(encoder, input[position] & 0xFF /*literal*/, 8);
            position += 1;
        } else {
            m = input[position - 1];
            int oldLen = len;

            len += log2(lookahead - 1) + sync + log2(monogramCount[m]) + 8;

            int MatchPos = match2.pos;

            encodeInteger(encoder, match2.len - 1 /*Match length*/, lookaheadBits);
            encodeInteger(encoder, 1 /*sync*/, 1);
            encodeInteger(encoder, MatchPos /*Match size*/, (int) log2int(monogramCount[m] - 1));
            position += match2.len - 1;
            encodeInteger(encoder, input[position] & 0xFF /*literal*/, 8);
            position += 1;
        }
        // If position > window
        //      Iterate from oldPosition - window to position - window
        //          Remove from monogramCount all positions
        //              monogramIndexRemove++

        // Iterate from oldPosition -> position
        //      Add to monogramCount all the new positions
        //      Add to monogramIndexAdd++
        //      Add to monogramPositions[indexAdd] -> the position
        //      Add to monogramPositionsInverse[the position] -> indexAdd
        for (i = (oldPosition - window) < 0 ? 0 : (oldPosition - window);
                i < (position - window);
                i++) {
            int mm = input[i];
            monogramCount[mm]--;
            monogramPositions[mm][monogramIndexRemove[mm]] = -1;
            monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) % window;
        }
        for (i = (oldPosition) < 0 ? 0 : (oldPosition);
                i < (position);
                i++) {
            int mm = input[i];
            monogramCount[mm]++;
            monogramPositions[mm][monogramIndexAdd[mm]] = i;
            monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) % window;
        }
    }
    //add a padding value to the end -> this will act as a barrier for the decoding process - allowing us to remove some extra checks
    encodeInteger(encoder, 0x00, 8);
    return encodeFlush(encoder);
}


static inline void updatePositions(byte *output, int oldPosition, int position) {
    int i;
    if (oldPosition < window) {
        int end = (position - window);
        for (i = 0; i < end; i++) {
            register int mm = output[i];
            monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) & (window_1);
        }
        for (i = oldPosition; i < position; i++) {
            register int mm = output[i];
            monogramPositions[mm][monogramIndexAdd[mm]] = i;
            monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) & (window_1);
        }
    } else {
        for (i = oldPosition; i < position; i++) {
            register int mm = output[i - window];
            monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) & (window_1);
            mm = output[i];
            monogramPositions[mm][monogramIndexAdd[mm]] = i;
            monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) & (window_1);
        }
    }
}

static inline void _updatePositions(byte *output, int oldPosition, int position) {
    int i;
    int start = (oldPosition - window) < 0 ? 0 : (oldPosition - window);
    int end   = (position - window);
    for (i = start; i < end; i++) {
        int mm = output[i];
        //monogramCount[mm]--;
        //monogramPositions[mm][monogramIndexRemove[mm]] = -1;
        monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) & (window_1);
    }
    for (i = oldPosition; i < position; i++) {
        int mm = output[i];
        //monogramCount[mm]++;
        monogramPositions[mm][monogramIndexAdd[mm]] = i;
        monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) & (window_1);
    }
}


static byte* decompress3(ICodec *decoder, int *bufferLen) {
    int i;

    //read original input length
    int b1 = (int) decodeInteger(decoder, 8);
    int b2 = (int) decodeInteger(decoder, 8);
    int b3 = (int) decodeInteger(decoder, 8);
    int b4 = (int) decodeInteger(decoder, 8);

    int outputLength = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    *bufferLen = outputLength;

    //System.out.println("OUTPUT: " + outputLength);

    byte* output = (byte *) malloc(outputLength);

    int position = 0;
    while (position < outputLength) {
        int oldPosition = position;

        //Block format:
        // [Match length]{If Match length > 0}{[sync][Match pos]}[Literal]

        int length = (int) decodeInteger(decoder, lookaheadBits);
        if (length == 0) {
            //read
            int literal = (int) decodeInteger(decoder, 8);
            output[position++] = literal;
            //position++;
        } else {
            //read sync
            int sync = (int) decodeInteger(decoder, 1);
            if (sync == 0) {
                int matchPos = (int) decodeInteger(decoder, windowBits);
                if (position - window < 0) {
                    // nothing
                } else {
                    matchPos = position - window + matchPos;
                }
                for (i = 0; i < length; i++) {
                    output[position] = output[matchPos + i];
                    position++;
                }
            } else {
                int m = output[position - 1];
                int monogramCount = monogramIndexAdd[m] - monogramIndexRemove[m];
                if (monogramCount < 0) {
                    monogramCount += window;
                }
                int matchPos = (int) decodeInteger(decoder, (int) log2int(monogramCount - 1));
                length += 1;
                matchPos = monogramPositions[m][(monogramIndexRemove[m] + matchPos) & window_1];
                for (i = 1; i < length; i++) {
                    output[position] = output[matchPos + i];
                    position++;
                }
            }
            int literal = (int) decodeInteger(decoder, 8);
            output[position++] = literal;
            //position++;
        }

        updatePositions(output, oldPosition, position);
    }

    return output;
}


int main(int argc, char *argv[]) {
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

    if (data == (caddr_t)(-1)) {
        perror("mmap");
        exit(1);
    }

    if (strcmp("-c", argv[1]) == 0) {
        // Initialize the encoder and allocate space for compressed data buffer
        ICodec encoder = { 0 };
        encoder.bufferLen = sbuf.st_size;
        encoder.buffer = (byte *) malloc(encoder.bufferLen);
        encodeInit(&encoder);

        printf("Start encoding\n");
        compress3(data, sbuf.st_size, &encoder);
        printf("End encoding: %d\n", encoder.currentBytePos);

        FILE *out = fopen(argv[3], "wb");

        if (out){
            fwrite(encoder.buffer, encoder.currentBytePos, 1, out);
        }

        fclose(out);
    }
    if (strcmp("-d", argv[1]) == 0) {
        ICodec decoder = { 0 };
        decoder.bufferLen = sbuf.st_size;
        decoder.buffer = data;

        decodeInit(&decoder);
        printf("Start decoding\n");
        int bufferLen;
        byte *buffer = decompress3(&decoder, &bufferLen);
        printf("End decoding");
        FILE *out = fopen(argv[3], "wb");

        if (out){
            fwrite(buffer, bufferLen, 1, out);
        }

        fclose(out);
    }
}
