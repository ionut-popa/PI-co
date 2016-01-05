package ro.dudydu.io;

public class BitInputBuffer {
    byte[] source;
    int bytep = 0, bitp = 0;
    int currentByte = 0;

    public BitInputBuffer(byte[] source) {
        this.source = source;
        currentByte = source[0] & 0xff;
        //bitp = 8;
    }
                //0  1  2  3  4   5   6   7    8
    int[] mask = {0, 1, 3, 7, 15, 31, 63, 127, 255};

    public long readBit() {
        long result = (currentByte >> 7) & 1;
        currentByte = (byte) (currentByte << 1);
        if (bitp++ == 7) {
            bytep++;
            if (bytep > source.length - 1) {
                currentByte = 0;
            } else {
                currentByte = source[bytep];
                bitp = 0;
            }
        }
        return result;
    }



    public long decodeInteger(int totalBits) {
        long result = 0;
        int offset = 0;
        if (totalBits < 0) {
            return 0;
        }
        do {
            if (totalBits < bitp) {
                result |= (currentByte & mask[totalBits]) << offset;
                bitp -= totalBits;
                currentByte >>= totalBits;
                totalBits = 0;
            } else {
                result |= (currentByte & mask[bitp]) << offset;
                totalBits -= bitp;
                offset += bitp;
                bitp -= bitp;
                currentByte >>= bitp;
            }

            if (bitp == 0) {
                bytep++;
                if (bytep < source.length) {
                    currentByte = source[bytep] & 0xFF;
                }
                bitp = 8;
            }
        } while (totalBits > 0);

        return result;
    }
}
