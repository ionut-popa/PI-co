package ro.dudydu.io;

import java.io.IOException;

/**
 * Encode dictionary pointers and synchronization words lenght using a range encoder
 * Implementation based on: http://www.winterwell.com/software/compressor.php
 */
public class RangeCodecImpl implements ICodec {

    private final static int FIRST_QUARTER = 0x200000;
    private final static int THIRD_QUARTER = 0x600000;
    private final static int HALF = 0x400000;
    private final static int HIGH = 0x7fffff;
    private final static int INITIAL_READ = 23;

    int[] freq = new int[256];

    //INDEX - CODE
    int[] synchEncodingTable = new int[256];
    int[] synchEncodingLen = new int[256];

    //CODE - INDEX
    int[] synchDecodingTable = new int[256];
    private int maxSynchSize = 0;

    private static int RESET_FREQUENCIES = 10 * 1024;

    private BitOutputBuffer bitBufOut;
    private BitInputBuffer bitBufIn;

    private int mLow;
    private int mHigh;
    private int mBuffer;
    private int mScale;
    private int mStep;
    private int value;
    private int low;
    private int high;
    private int current;

    /**
     * Construct codec for - encoding
     *
     * @param maxSynchSize
     */
    public RangeCodecImpl(int maxSynchSize) {
        bitBufOut = new BitOutputBuffer();
        this.maxSynchSize = maxSynchSize;
        for (int i = 0; i <= maxSynchSize; i++) {
            freq[i] = 1;
        }
        low = 0;
        high = HIGH;
        mLow = low;
        mHigh = high;
        mStep = 0;
        mScale = 0;
        current = 0;
    }

    /**
     * Construct codec for - decoding
     *
     * @param maxSynchSize
     */
    public RangeCodecImpl(int maxSynchSize, byte[] in) throws IOException {
        bitBufIn = new BitInputBuffer(in);
        this.maxSynchSize = maxSynchSize;
        this.maxSynchSize = maxSynchSize;
        for (int i = 0; i <= maxSynchSize; i++) {
            freq[i] = 1;
        }

		current = 0;
		low = 0;
        high = HIGH;
		mLow = low;
        mHigh = high;
        mStep = 0;
        mScale = 0;
        mBuffer = 0;
		/*	Fill buffer with bits from the input stream */

		for( int i=0; i<INITIAL_READ; i++ ) {
			mBuffer = 2 * mBuffer;
			mBuffer += bitBufIn.readBit();
		}
    }

    public void encodeInteger(int val, int total) throws IOException {
        current = val;
        low = val;
        high = val + 1;

        /* 2. Update the coder */
        mStep = (mHigh - mLow + 1) / total;
        mHigh = (mLow + mStep * high) - 1;
        mLow = mLow + mStep * low;

        /* Renormalize if possible */
        while ((mHigh < HALF) || (mLow >= HALF)) {
            if (mHigh < HALF) {
                bitBufOut.writeBit((byte) 0);
                mLow = mLow * 2;
                mHigh = mHigh * 2 + 1;

                /* Perform e3 mappings */
                for (; mScale > 0; mScale--)
                    bitBufOut.writeBit((byte) 1);
            } else if (mLow >= HALF) {
                bitBufOut.writeBit((byte) 1);
                mLow = (mLow - HALF) * 2;
                mHigh = (mHigh - HALF) * 2 + 1;

                /* Perform e3 mappings */
                for (; mScale > 0; mScale--)
                    bitBufOut.writeBit((byte) 0);
            }
        }

        while ((FIRST_QUARTER <= mLow) && (mHigh < THIRD_QUARTER)) {
            mScale++;
            mLow = (mLow - FIRST_QUARTER) * 2;
            mHigh = (mHigh - FIRST_QUARTER) * 2 + 1;
        }
    }

    public void encodeSynch(int synchSize, int numberOfEligibleSynch) throws IOException {
        current = synchSize;
        low = 0;
        int total = 0;

        /* retrieve cumulative freq */
        for (int j = 0; j < numberOfEligibleSynch; j++) {
            total += freq[j];
        }

        for (int j = 0; j < current; j++) {
            low += freq[j];
        }
        high = low + freq[current];


        /* 2. Update the coder */
        mStep = (mHigh - mLow + 1) / total;
        mHigh = (mLow + mStep * high) - 1;
        mLow = mLow + mStep * low;

        /* Renormalize if possible */
        while ((mHigh < HALF) || (mLow >= HALF)) {
            if (mHigh < HALF) {
                bitBufOut.writeBit((byte) 0);
                mLow = mLow * 2;
                mHigh = mHigh * 2 + 1;

                /* Perform e3 mappings */
                for (; mScale > 0; mScale--)
                    bitBufOut.writeBit((byte) 1);
            } else if (mLow >= HALF) {
                bitBufOut.writeBit((byte) 1);
                mLow = (mLow - HALF) * 2;
                mHigh = (mHigh - HALF) * 2 + 1;

                /* Perform e3 mappings */
                for (; mScale > 0; mScale--)
                    bitBufOut.writeBit((byte) 0);
            }
        }

        while ((FIRST_QUARTER <= mLow) && (mHigh < THIRD_QUARTER)) {
            mScale++;
            mLow = (mLow - FIRST_QUARTER) * 2;
            mHigh = (mHigh - FIRST_QUARTER) * 2 + 1;
        }

        /* 3. Update model */
        freq[current] += 1;
        //total += 1;

        s++;
        if (s % RESET_FREQUENCIES == 0){
             //reset frequencies
            for (int i = 0; i <= maxSynchSize; i++) {
                freq[i] = 1;
            }
        }
    }
    int s = 0;

    public long decodeInteger(int total) throws IOException {
        /* 1. Retrieve current byte */
        mStep = (mHigh - mLow + 1) / total;
        value = (mBuffer - mLow) / mStep;

        low = value;

        high = low + 1;

        /* 2. Update the decoder */
        mHigh = mLow + mStep * high - 1; // interval open at the top => -1

        /* Update lower bound */
        mLow = mLow + mStep * low;

        /* e1/e2 mapping */
        while ((mHigh < HALF) || (mLow >= HALF)) {
            if (mHigh < HALF) {
                mLow = mLow * 2;
                mHigh = ((mHigh * 2) + 1);
                mBuffer = (2 * mBuffer);
            } else if (mLow >= HALF) {
                mLow = 2 * (mLow - HALF);
                mHigh = 2 * (mHigh - HALF) + 1;
                mBuffer = 2 * (mBuffer - HALF);
            }

            mBuffer += bitBufIn.readBit();
            mScale = 0;
        }

        /* e3 mapping */
        while ((FIRST_QUARTER <= mLow) && (mHigh < THIRD_QUARTER)) {
            mScale++;
            mLow = 2 * (mLow - FIRST_QUARTER);
            mHigh = 2 * (mHigh - FIRST_QUARTER) + 1;
            mBuffer = 2 * (mBuffer - FIRST_QUARTER);
            mBuffer += bitBufIn.readBit();
        }

        return value;
    }

    public long decodeSynch(int numberOfEligibleSynch) throws IOException {
        int total = 0;
        for (int i = 0; i < numberOfEligibleSynch; i++) {
            total += freq[i];
        }
        /* 1. Retrieve current byte */
        mStep = (mHigh - mLow + 1) / total;
        value = (mBuffer - mLow) / mStep;
        low = 0;
        for (current = 0; current < maxSynchSize && low + freq[current] <= value; current++)
            low += freq[current];

        high = low + freq[current];

        /* 2. Update the decoder */
        mHigh = mLow + mStep * high - 1; // interval open at the top => -1

        /* Update lower bound */
        mLow = mLow + mStep * low;

        /* e1/e2 mapping */
        while ((mHigh < HALF) || (mLow >= HALF)) {
            if (mHigh < HALF) {
                mLow = mLow * 2;
                mHigh = ((mHigh * 2) + 1);
                mBuffer = (2 * mBuffer);
            } else if (mLow >= HALF) {
                mLow = 2 * (mLow - HALF);
                mHigh = 2 * (mHigh - HALF) + 1;
                mBuffer = 2 * (mBuffer - HALF);
            }

            mBuffer += bitBufIn.readBit();
            mScale = 0;
        }

        /* e3 mapping */
        while ((FIRST_QUARTER <= mLow) && (mHigh < THIRD_QUARTER)) {
            mScale++;
            mLow = 2 * (mLow - FIRST_QUARTER);
            mHigh = 2 * (mHigh - FIRST_QUARTER) + 1;
            mBuffer = 2 * (mBuffer - FIRST_QUARTER);
            mBuffer += bitBufIn.readBit();
        }

        /* 3. Update frequency table */
        freq[current] += 1;
        //total+=1;

        s++;
        if (s % RESET_FREQUENCIES == 0){
             //reset frequencies
            for (int i = 0; i <= maxSynchSize; i++) {
                freq[i] = 1;
            }
        }

        return current;
    }

    public double getSynchEncodingLenght(int synch) {
        double total = 0;
        for (int i = 0; i < maxSynchSize; i++) {
            total += freq[i];
        }
        double p = ((double) freq[synch]) / total;
        return p * Math.log(p) / Math.log(2);
    }

    public byte[] flushEncoding() throws IOException {
        /* Finish encoding */
        if (mLow < FIRST_QUARTER) {
            /* Case: mLow < FirstQuarter < Half <= mHigh */
            bitBufOut.writeBit((byte) 0);
            /* Perform e3-scaling */
            for (int i = 0; i < mScale + 1; i++)
                bitBufOut.writeBit((byte) 1);
        } else {
            /* Case: mLow < Half < ThirdQuarter <= mHigh */
            bitBufOut.writeBit((byte) 1);
        }
        bitBufOut.flush();
        return bitBufOut.toByteArray();
    }
}
