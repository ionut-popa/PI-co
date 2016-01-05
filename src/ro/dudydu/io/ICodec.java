package ro.dudydu.io;

import java.io.IOException;

/**
 * Codec interface - methods for coding dictionary pointers
 * and synchronization size
 */
public interface ICodec {
    /**
     * Encode current dictionary pointer
     * @param total the size of current dictionary
     * @return
     * @throws IOException
     */
    void encodeInteger(int val, int total) throws IOException;

    /**
     * Decode synchronization length for current dictionary pointer
     * @param synchLen
     * @param numberOfEligibleSynch     the number of eligible synchronizations in one compression step
     *        might be smaller than maxSynchSize
     * @return
     * @throws IOException
     */
    void encodeSynch(int synchLen, int numberOfEligibleSynch) throws IOException;

    /**
     * Decode current dictionary pointer
     * @param total the size of current dictionary
     * @return
     * @throws IOException
     */
    long decodeInteger(int total) throws IOException;

    /**
     * Decode synchronization length for current dictionary pointer
     * @param numberOfEligibleSynch the number of eligible synchronizations in one compression step
     *        might be smaller than maxSynchSize
     * @return
     * @throws IOException
     */
    long decodeSynch(int numberOfEligibleSynch) throws IOException;

    /**
     * @param synchLen
     * @return estimation(in bits) of space required to encode synch
     */
    double getSynchEncodingLenght(int synchLen);

    /**
     * Finalize encoding
     * @return  encoded data
     * @throws IOException
     */
    byte[] flushEncoding() throws IOException;
}
