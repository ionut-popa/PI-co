package ro.dudydu.io;


import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * Reading and writing compressed data:<br/>
 *  - dictionary index - encoded as integers using LOG2(CURRENT_DICTIONARY_SIZE)<br/>
 *  - synchronization size - encoded using a static Huffman table. Table is recomputed each
 * RESET_FREQUENCIES<br/>
 */
public class IntegerHuffmanCodecImpl implements ICodec {

    BitInputBuffer bitBufIn = null;
    BitOutputBuffer bitBufOut = null;

    int[] freq = new int[256];

    //INDEX - CODE
    int[] synchEncodingTable = new int[256];
    int[] synchEncodingLen = new int[256];

    //CODE - INDEX
    int[] synchDecodingTable = new int[256];
    private int maxSynchSize = 0;

    private static int RESET_FREQUENCIES = 1024 * 4;

    /**
     * Construct codec for - encoding
     * @param maxSynchSize
     */
    public IntegerHuffmanCodecImpl(int maxSynchSize) {
        bitBufOut = new BitOutputBuffer();
        this.maxSynchSize = maxSynchSize;
        for (int i = 0; i <= maxSynchSize; i++) {
            freq[i] = 0;
        }
        buildCodes();
    }

    /**
     * Construct codec for - decoding
     * @param maxSynchSize
     */
    public IntegerHuffmanCodecImpl(int maxSynchSize, byte[] in) throws IOException {
        bitBufIn = new BitInputBuffer(in);
        this.maxSynchSize = maxSynchSize;
        this.maxSynchSize = maxSynchSize;
        for (int i = 0; i <= maxSynchSize; i++) {
            freq[i] = 0;
        }
        buildCodes();
    }


    /**
     * Builds synchronization codewords, depending on current, internal, frequncies.
     * Also, frequencies are reseted.
     */
    private void buildCodes() {
        int[] freqOrder = new int[256];

        //stupid algorithm to find out freqOrder.
        for (int i = 0; i <= maxSynchSize; i++) {
            int max = -1;
            int maxPos = 0;

            for (int j = maxSynchSize; j >= 0; j--) {
                if (freq[j] >= max) {
                    max = freq[j];
                    maxPos = j;
                }
            }
            freqOrder[i] = maxPos;
            freq[maxPos] = -1;
        }

        //codewords - low frequency - small codewords
        for (int i = 0; i < maxSynchSize; i++) {
            String code = "";
            String lastCode = "";
            for (int j = 0; j < i ; j++) {
                code += "1";
            }
            lastCode = code + "1";
            code += "0";
            synchEncodingTable[freqOrder[i]] = Integer.parseInt(code, 2);
            synchEncodingLen[freqOrder[i]] = code.length();
            synchDecodingTable[Integer.parseInt(code, 2)] = freqOrder[i];
            if (i == maxSynchSize - 1) {
                synchEncodingTable[freqOrder[i + 1]] = Integer.parseInt(lastCode, 2);
                synchEncodingLen[freqOrder[i + 1]] = lastCode.length();
                synchDecodingTable[Integer.parseInt(lastCode, 2)] = freqOrder[i + 1];
            }
        }

        //reset frequencies
        for (int i = 0; i <= maxSynchSize; i++) {
            freq[i] = 0;
        }

    }


    public void encodeInteger(int val, int total) throws IOException {
        //bitBufOut.encodeInteger(val, total);

        while (total > 0) {
            bitBufOut.writeBit((byte) (val & 1));
            val >>= 1;
            total--;
        }
    }


    public void encodeSynch(int synchSize, int numberOfEligibleSynch) throws IOException {
        if (maxSynchSize == 0 || numberOfEligibleSynch == 1) {
            return;
        }
        int val = synchEncodingTable[synchSize];
        int len = synchEncodingLen[synchSize];
        for (int i = 0; i < synchEncodingLen[synchSize]; i++) {
            byte bit = (byte) ((val >> (len-1-i)) & 1);
            bitBufOut.writeBit(bit);
        }
        freq[synchSize]++;
        s++;
        if (s % RESET_FREQUENCIES == 0){
            buildCodes();
        }
    }
    int s = 0;

    public long decodeInteger(int total) throws IOException {
        //return bitBufIn.decodeInteger(total);

        int val = 0;
        int len = 0;
        while (total > 0) {
            int bit = (int) bitBufIn.readBit();
            val |= bit << len;
            len++;
            total--;
        }
        return val;

    }

/*
    public long decodeInteger(int total) throws IOException {
        int val = 0;
        int len = 0;
        for (int i = 0; i < total; i++) {
            int bit = (int) bitBufIn.readBit();
            val |= bit << len;
            len++;

        }
        return val;
    }
*/

    public long decodeSynch(int numberOfEligibleSynch) throws IOException {
        if (maxSynchSize == 0) {
            return 0;
        }

        int val = 0;
        int len = 0;
        while (len < maxSynchSize) {
            val <<= 1;
            int bit = (int) bitBufIn.readBit();
            val |= bit;
            len++;
            if (bit == 0) {
                break;
            }
        }
        int synchSize = synchDecodingTable[val];

        freq[synchSize]++;
        s++;
        if (s % RESET_FREQUENCIES == 0){
            buildCodes();
        }

        return synchSize;
    }

    public byte[] flushEncoding() {
        return bitBufOut.toByteArray();
    }

    public double getSynchEncodingLenght(int synch) {
        return synchEncodingLen[synch];
    }
}


