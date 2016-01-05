package ro.dudydu.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BitOutputBuffer {
    ByteArrayOutputStream buf;
    int currentByte;
    byte currentBit;

    public BitOutputBuffer() {
        buf = new ByteArrayOutputStream();
        //currentByte = new byte[1];
        //currentByte[0] = 0;
        currentByte = 0;
        currentBit = 0;
    }

    public void writeBit(byte bit) throws IOException {

        currentByte = (byte) ((currentByte) << 1);
        currentByte += bit;
        currentBit += 1;
        if (currentBit == 8) {
            buf.write(currentByte);
            currentByte = 0;
            currentBit = 0;
        }

    }

    public void flush() throws IOException {
        /* Pad the buffer with zeros */
        while (currentBit != 0) {
            writeBit((byte) 0);
        }
        buf.flush();
    }

    public byte[] toByteArray() {
        try {
            buf.flush();
            return buf.toByteArray();
        }
        catch (IOException e) {
            return null;
        }
    }

    public void encodeInteger(int val, int totalBits) {
        do {
            currentByte |= ((val << currentBit) & 0xFF);
            if (currentBit + totalBits > 8) {
                totalBits -= (8 - currentBit);
                val >>= (8 - currentBit);
                currentBit += (8 - currentBit);
            } else {
                currentBit += totalBits;
                val >>= totalBits;
                totalBits = 0;
            }

            if (currentBit == 8) {
                buf.write(currentByte);
                currentBit = 0;
                currentByte = 0;
            }

        } while (totalBits > 0);

        //To change body of created methods use File | Settings | File Templates.
    }
}
