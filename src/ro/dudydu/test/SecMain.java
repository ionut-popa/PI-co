package ro.dudydu.test;

import ro.dudydu.io.BitInputBuffer;
import ro.dudydu.io.BitOutputBuffer;
import ro.dudydu.sec.SECodec;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Main test class. Command line utility
 */
public class SecMain {
    private static final String help =
            "Usage java -jar secodec<VER>.jar [option] [file_in] [file_out]\n" +
                    "\t[option]\n" +
                    "\t\t-c\tCompress the input [file_in] and output compressed data to [file_out]\n" +
                    "\t\t-d\tDecompress the input [file_in] and output decompressed data to [file_out]\n";


    public static void main(String[] args) {
        BitOutputBuffer bob = new BitOutputBuffer();

        bob.encodeInteger(5, 5);
        bob.encodeInteger(5, 5);
        bob.encodeInteger(5, 6);

        bob.encodeInteger(1, 1);
        bob.encodeInteger(5, 10);
        bob.encodeInteger(5, 5);

        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);
        bob.encodeInteger(12345, 18);


        BitInputBuffer bib = new BitInputBuffer(
            //new byte[]{(byte) 0Xa5, (byte) 0X14, (byte) 0X0b, (byte) 0X28}
            bob.toByteArray()
        );
        System.out.println(bib.decodeInteger(5));
        System.out.println(bib.decodeInteger(5));
        System.out.println(bib.decodeInteger(6));

        System.out.println(bib.decodeInteger(1));
        System.out.println(bib.decodeInteger(10));
        System.out.println(bib.decodeInteger(5));

        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));
        System.out.println(bib.decodeInteger(18));

        try {
            if (args.length != 3) {
                System.out.println(help);
                System.exit(1);
            }
            if ("-c".equals(args[0])) {
                File file = new File(args[1]);

                String absolutePath = file.getAbsolutePath();

                byte[] original = readFromFile(absolutePath);

                //compress
                long time1 = System.currentTimeMillis();
                byte[] compressed = SECodec.compress(original);
                long time2 = System.currentTimeMillis();

                FileOutputStream compressedFile = new FileOutputStream(args[2]);
                compressedFile.write(compressed);

                System.out.println("[INFO]:" + file.getName() + ", original:" + original.length +
                        ", compressed:" + compressed.length + ", time compress:" + (time2 - time1));

            } else if ("-d".equals(args[0])) {
                //decode
                File file = new File(args[1]);

                String absolutePath = file.getAbsolutePath();

                byte[] compressed = readFromFile(absolutePath);

                long time3 = System.currentTimeMillis();
                byte[] decompressed = SECodec.decompress(compressed);
                long time4 = System.currentTimeMillis();

                System.out.println("[INFO]:" + file.getName() + " time decompression:" + (time4 - time3));

                FileOutputStream decompressedFile = new FileOutputStream(args[2]);
                decompressedFile.write(decompressed);

                long time5 = System.currentTimeMillis();
                String gzipFileName = file.getName().replaceAll(".sec",".gz");
                new Gunzipper(new File(gzipFileName)).unzip(
                     new File(gzipFileName + ".unzip"));
                long time6 = System.currentTimeMillis();

                System.out.println("[INFO]:" + gzipFileName + " time decompression:" + (time6 - time5));
            } else {
                System.out.println(help);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static class Gunzipper {
      private InputStream in;

      public Gunzipper(File f) throws IOException {
        this.in = new FileInputStream(f);
      }
      public void unzip(File fileTo) throws IOException {
        OutputStream out = new FileOutputStream(fileTo);
        try {
          in = new GZIPInputStream(in);
          byte[] buffer = new byte[65536];
          int noRead;
          while ((noRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, noRead);
          }
        } finally {
          try { out.close(); } catch (Exception e) {}
        }
      }
      public void close() {
        try { in.close(); } catch (Exception e) {}
      }
    }

    private static byte[] readFromFile(String absolutePath) throws Exception {
        FileInputStream inputFile = new FileInputStream(absolutePath);
        ByteArrayOutputStream inputBuffer = new ByteArrayOutputStream();
        while (true) {
            byte[] buffer = new byte[1024];
            int length = inputFile.read(buffer);
            if (length >= 0) {
                inputBuffer.write(buffer, 0, length);
            } else {
                break;
            }
        }
        return inputBuffer.toByteArray();
    }


    static double probability(byte prev, byte current, int freq[][]) {
        double d = 0;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++)
        {
            byte p = (byte) i;
            double dist = 1 - ((double) Math.abs(prev - p) / 256);
            //dist = dist * dist * dist * dist * dist * dist * dist * dist;
            if (dist > 0.99) {
                d += dist * (1 + freq[p + 128][current + 128]);
            }
        }
        return d;
    }

    static double totalProbability(byte prev, int freq[][]) {
        double d = 0;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++)
        {
            byte c = (byte) i;
            for (int j = Byte.MIN_VALUE; j <= Byte.MAX_VALUE; j++)
            {
                byte p = (byte) j;
                double dist = 1 - ((double) Math.abs(prev - p) / 256);
                if (dist >= 0.99) {
                    d += dist * (1 + freq[p + 128][c + 128]);
                }
            }
        }
        if (d == 0) {
            System.out.println("<<<<<<<<<<<<<<<");
        }
        return d;

    }

    public static byte[] transformContent(byte[] content) {
        double totalProbability = 0;
        int freq[] = new int[256 * 256];
        int total[] = new int[256];

        byte ret[] = new byte[content.length];

        ret[0] = content[0];
        double fMax = -1;
        byte cMax = -1;
        for (int i = 1; i < content.length; i++) {
            for (int j = Byte.MIN_VALUE; j <= Byte.MAX_VALUE; j++)
            {
                byte c = (byte) j;
                double f = freq[((content[i - 1] + 128) << 8) + (c + 128)];
                if (f > fMax) {
                    fMax = f;
                    cMax = c;
                }
            }
            ret[i] = (byte) ((content[i] - cMax) % 256);
            freq[((content[i - 1] + 128) << 8) + (content[i] + 128)]++;
        }
        return ret;
    }

    private static byte[] myIndexes = new byte[256];

    private static void resetIndexes(){
        for(int k=0; k < myIndexes.length; k++){
            myIndexes[k] = (byte) (k - 128);
        }
    }
    public static byte[] mtf(byte[] list){
        resetIndexes();
        byte[] copy = new byte[list.length];
        for (int k=0; k < list.length; k++){

            //if (list[k] < 0 || list[k] > 255){
            //    throw new RuntimeException("mtf trouble index "+k+" value "+list[k]);
            //}

            for(int j=0; j < myIndexes.length; j++){
                if (myIndexes[j] == list[k]) {
                    copy[k] = (byte) (j - 128);
                    for (int i=j; i >= 1; i--) {
                        myIndexes[i] = myIndexes[i-1];
                    }
                    myIndexes[0] = list[k];
                    break;
                }
            }
        }
        return copy;
    }
}
