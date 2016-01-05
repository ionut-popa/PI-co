package ro.dudydu.sec;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ro.dudydu.io.*;

import ro.dudydu.io.RangeCodecImpl;

/*
1 2 1 2 1 2 1 2 0 1 2   3   3   3 1 2 1   4   4   4 2 0   5   5   5   5

1 2 1 2 1 2 1 2 0 1 2 1 2 1 2 1 2   3 1 2 1 2 1 2 1 2 0 1 2 1 2 1 2 1 2

1 2 1 2 1 2 1 2 0 1 2 1 2 1 2 1 2   3 1 2 1 2 1 2 1 2 0 1 2 1 2 1 2 1 2 3
/*

    W S3 S2 S1 |  X Y Z T ...

    Matches
    S3 S2 S1 X Y             cost1
          S1 X               cost0
             X Y T           cost2


    If (X T Y) minimum cost -> Don't add S3

 */









/**
 * @author Popa Ionut (ionut.popa@gmail.com)
 */
public class SECodec {

    //[INFO]:x.patch, original:1916367, compressed:554842, time compress:1391

    public static final int MAX_SYNCH_SIZE = 1;//1;//5;

    private static final int MAX_DICT_SIZE = 1 << 16;


    public static byte[] compress(byte[] input) throws IOException {
        //ICodec encoder = new RangeCodecImpl(MAX_SYNCH_SIZE);
        ICodec encoder = new IntegerHuffmanCodecImpl(MAX_SYNCH_SIZE);
        return compress3(input, encoder);
    }

    public static int digram(byte b1, byte b2) {
        return ((b1 & 0xFF) << 8) | (b2 & 0xFF);
    }


    public static int monogram(byte b1) {
        return (b1 & 0xFF);
    }

    static int window = 2 * 1024;//16;
    static int lookahead = 16;

    static int[]   monogramCount           = new int[256];
    static int[]   monogramIndexAdd        = new int[256];
    static int[]   monogramIndexRemove     = new int[256];
    static int[][] monogramPositions       = new int[256][window];


    class Match {
        int position;
        int lenght;
    }

    public static int[] findMatch(byte[] input, int startSearchPosition, int maxLenght, int _window, int sync) {
        int maxLenghtFound      = -1;
        int posMaxLenghtFound   = 0;
        int count               = 0;

//        for (int i = (startSearchPosition - window) < 0 ? 0 : (startSearchPosition - window);
//                i < startSearchPosition;
//                i++) {

        if (startSearchPosition > 0) {
            int monogramStart = monogram(input[startSearchPosition]);

            for (int p = 0; p < monogramCount[monogramStart]; p++) {
                int i = monogramPositions[monogramStart][(monogramIndexRemove[monogramStart] + p ) % window];
                if (i > startSearchPosition - maxLenght) {
                    break;
                }
                if (i == 5) {
                    ////System.out.println("WWWWWWWWWWWWWW");
                }

                for (int j = 0; j < maxLenght; j++) {
                    if (input[i + j] == input[startSearchPosition + j]) {
                        if (j > maxLenghtFound) {
                            ////System.out.println("[DEBUG] Input compare:" + input[i + j] + "==" + input[startSearchPosition + j]);
                            maxLenghtFound = j;
                            if (sync == 1) {
                                posMaxLenghtFound = p;
                            } else {
                                posMaxLenghtFound = i;
                            }
                            ////System.out.println("[DEBUG] Found match pos:" + posMaxLenghtFound + " Match len:" + maxLenghtFound);
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return new int[]{posMaxLenghtFound, maxLenghtFound + 1};
    }


    public static double log2(double v) {
//        if (v == 0) {
//            return 1000000000;
//        }
        return Math.log(v) / Math.log(2);
    }

    public static byte[] compress3(byte[] input, ICodec encoder) throws IOException {

        int len = 0;

        int position = 0;
        int inputLength = input.length;

        //first - encodeInteger file length
        byte b1 = (byte) (inputLength >> 24);
        byte b2 = (byte) ((inputLength << 8) >> 24);
        byte b3 = (byte) ((inputLength << 16) >> 24);
        byte b4 = (byte) ((inputLength << 24) >> 24);

        encoder.encodeInteger(b1 & 0xFF, 8);
        encoder.encodeInteger(b2 & 0xFF, 8);
        encoder.encodeInteger(b3 & 0xFF, 8);
        encoder.encodeInteger(b4 & 0xFF, 8);

        //Block format:
        // [Match length]{If Match length > 0}{[sync][Match pos]}[Literal]

        while (position < inputLength - lookahead- 1) {
            int oldPosition = position;

            int m = monogram(input[position]);

            int[] match1 = findMatch(input, position, lookahead - 1, window, 0);
            //int[] match2 = new int[2];int sync = 0;///Length:5936749
            int[] match2 = findMatch(input, position - 1, lookahead, window - 1, 1);int sync = 1;

            double score1 = (log2(lookahead) + 1 + log2(window) + 8) / match1[1];
            double score2 = (log2(lookahead) + 1 + log2(monogramCount[m]) + 8) / (match2[1] - 1);

            if (match1[1] <= 0) {
                score1 = 10000000;
            }
            if (match2[1] <= 2) {
                score2 = 10000000;
            }

            if (score1 <= score2) {
                int oldLen = len;
                int matchPos = match1[0];
                if (position - window < 0 || match1[1] == 0) {
                    ////System.out.println("<<<<<<<<<<<<<==:" + position );
                    //matchPos += window;
                } else {
                    matchPos = matchPos - (position - window);
                }
                if (position == 2018) {
                    //System.out.println("XXXX");
                }
                if (position < 4000) {
                    //System.out.println("0.Match pos:" + matchPos + " Match len:" + match1[1] + " position:" + position);
                }
                if (match1[1] != 0) {
                    len += log2(lookahead) + sync + log2(window) + 8 ;
                    encoder.encodeInteger(match1[1] /*match length*/, 4);
                    if (sync > 0) {
                        encoder.encodeInteger(0 /*sync*/, 1);
                    }
                    encoder.encodeInteger(matchPos /*match pos*/, 11);
                } else {
                    len += log2(lookahead) + 8;
                    encoder.encodeInteger(match1[1] /*match length*/, 4);
                }
                position += match1[1];
                encoder.encodeInteger(input[position] /*literal*/, 8);
                position += 1;
                if (len < oldLen) {
                    //System.out.println("DANGER !!!!");
                }
            } else {
                m = monogram(input[position - 1]);
                int oldLen = len;
                if (log2(monogramCount[m]) + log2(lookahead) + 8 + 1 < 0) {
                    //System.out.println("WHAT THE HELL!");
                }
                len += Math.ceil(log2(lookahead - 1)) + sync + Math.ceil(log2(monogramCount[m])) + 8;
                if (len < oldLen) {
                    //System.out.println("DANGER !!!!");
                }

                int matchPos = match2[0];
//

                encoder.encodeInteger(match2[1] - 1 /*match length*/, 4);
                encoder.encodeInteger(1 /*sync*/, 1);
                encoder.encodeInteger(matchPos /*match size*/, (int) Math.ceil(monogramCount[m] - 1));
                position += match2[1] - 1;
                encoder.encodeInteger(input[position] /*literal*/, 8);
                position += 1;
            }
//
            for (int i = (oldPosition - window) < 0 ? 0 : (oldPosition - window);
                    i < (position - window);
                    i++) {
                int mm = monogram(input[i]);
                monogramCount[mm]--;
                monogramPositions[mm][monogramIndexRemove[mm]] = -1;
                monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) % window;
            }
            for (int i = (oldPosition) < 0 ? 0 : (oldPosition);
                    i < (position);
                    i++) {
                int mm = monogram(input[i]);
                monogramCount[mm]++;
                monogramPositions[mm][monogramIndexAdd[mm]] = i;
                monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) % window;
            }

            int sum = 0;
            for (int i = 0; i < 256; i++) {
                sum += monogramCount[i];
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
            ////System.out.println("Length:" + (len/8) + " Pos:" + position);
        }
        return encoder.flushEncoding();
    }


    public static byte[] decompress3(byte[] compressed, ICodec decoder) throws IOException {

            //read original input length
            int b1 = (int) decoder.decodeInteger(8);
            int b2 = (int) decoder.decodeInteger(8);
            int b3 = (int) decoder.decodeInteger(8);
            int b4 = (int) decoder.decodeInteger(8);

            int outputLength = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;

            //System.out.println("OUTPUT: " + outputLength);

            byte[] output = new byte[(int) outputLength];

        try {

            int position = 0;
            while (position < outputLength) {
                int oldPosition = position;

                //Block format:
                // [Match length]{If Match length > 0}{[sync][Match pos]}[Literal]

                int length = (int) decoder.decodeInteger(4);
                if (length == 0) {
                    //read
                    int literal = (int) decoder.decodeInteger(8);
                    output[position] = (byte) (literal & 0xFF);
                    position++;
                } else {
                    //read sync
                    int sync = (int) decoder.decodeInteger(1);
                    if (sync == 0) {
                        int matchPos = (int) decoder.decodeInteger(11);
                        if (position - window < 0) {
                            // nothing
                        } else {
                            matchPos = position - window + matchPos;
                        }
                        for (int i = 0; i < length; i++) {
                            output[position] = output[matchPos + i];
                            position++;
                        }
                    } else {
                        int m = monogram(output[position - 1]);
                        int matchPos1 = (int) decoder.decodeInteger((int) Math.ceil(log2(monogramCount[m] - 1)));
                        int matchPos = matchPos1;
                        length += 1;
                        matchPos = monogramPositions[m][(monogramIndexRemove[m] + matchPos) % window];

                        position = position - 1;
                        for (int i = 0; i < length; i++) {
                            if (matchPos < 0) {
                                System.out.println("SSSS");
                            }
                            output[position] = output[matchPos + i];
                            position++;
                        }
                    }
                    int literal = (int) decoder.decodeInteger(8);
                    output[position] = (byte) (literal & 0xFF);
                    position++;
                }

                for (int i = (oldPosition - window) < 0 ? 0 : (oldPosition - window);
                        i < (position - window);
                        i++) {
                    int mm = monogram(output[i]);
                    monogramCount[mm]--;
                    monogramPositions[mm][monogramIndexRemove[mm]] = -1;
                    monogramIndexRemove[mm] = (monogramIndexRemove[mm] + 1) % window;
                }
                for (int i = (oldPosition) < 0 ? 0 : (oldPosition);
                        i < (position);
                        i++) {
                    int mm = monogram(output[i]);
                    monogramCount[mm]++;
                    monogramPositions[mm][monogramIndexAdd[mm]] = i;
                    monogramIndexAdd[mm] = (monogramIndexAdd[mm] + 1) % window;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public static byte[] compress0(byte[] input, ICodec encoder) throws IOException {
        //input = new byte[]{1, 2, 1, 2, 1, 2, 1, 2, 0, 1, 2, 1, 2, 1, 2, 1, 2, 3, 1, 2, 1, 2, 1, 2, 1, 2, 0, 1, 2, 1, 2, 1, 2, 1, 2, 3};

        int window = 1024;//256 * 2;//8;
        int[] monogramPosition  = new int[256];
        int[] digramPosition    = new int[256 * 256];
        int[] digramReplacement = new int[256 * 256];
        int[] digramReplacementInverse = new int[256];

        int[] monogramCount  = new int[256];
        int[] digramCount    = new int[256 * 256];


        int position = 0;
        int inputLength = input.length;

        byte[] output = new byte[inputLength * 2];

        Arrays.fill(digramReplacement, -1);
        Arrays.fill(digramReplacementInverse, -1);

        int len = 0;

        boolean full = false;
        int fullCnt = 0, duplicateDigramCnt = 0;

        while (position < inputLength - window - 1) {

            int inWindow = 0;

            for (inWindow = 0; inWindow < window - 1; inWindow++) {
                int m = monogram(input[position]);
                int d = digram(input[position], input[position + 1]);
                monogramCount[m]++;
                digramCount[d]++;

                if (digramReplacement[d] != -1) {
                    output[len] = (byte) (digramReplacement[d] & 0xFF);
                    ////System.out.println("XXXX");
                    position++;
                    len += 1;
                } else if (digramReplacementInverse[m] != -1) {
                    output[len] = (byte) ((digramReplacementInverse[m] >> 8) & 0xFF);
                    len += 1;
                    output[len] = (byte) ((digramReplacementInverse[m] >> 0) & 0xFF);;
                    len += 1;
                } else {
                    output[len] = (byte) (m & 0xFF);
                    len += 1;
                }
                position++;
            }

            Arrays.fill(digramReplacement, -1);
            Arrays.fill(digramReplacementInverse, -1);

            //search for the most freq digram and replace with the less freq monogram(s)
            for (int j = 0; j < 2; j++) {
                int mostFreqDigramCount = 0;
                int lessFreqMonogramCound = window;
                int mostFreqDigram = -1;
                int lessFreqMonogram = -1;

                for (int i = 0; i < 256 * 256; i++) {
                    if (digramCount[i] > mostFreqDigramCount) {
                        mostFreqDigramCount = digramCount[i];
                        mostFreqDigram = i;
                    }
                }

                for (int i = 0; i < 256; i++) {
                    if (monogramCount[i] < lessFreqMonogramCound) {
                        lessFreqMonogramCound = monogramCount[i];
                        lessFreqMonogram = i;
                    }
                }

                if (mostFreqDigram != -1 && lessFreqMonogram != -1) {
                    digramReplacement[mostFreqDigram] = lessFreqMonogram;
                    digramReplacementInverse[lessFreqMonogram] = mostFreqDigram;

                    ////System.out.println("Replacement: " +  mostFreqDigram + " (count:" + mostFreqDigramCount + ") with" +
                    //    lessFreqMonogram + " (count:" + lessFreqMonogramCound + ")");

                    digramCount[mostFreqDigram] = 0;//reset
                    monogramCount[lessFreqMonogram] = window;
                } else {
                    break;
                }
            }

            Arrays.fill(digramCount, 0);
            Arrays.fill(monogramCount, 0);
        }

        //System.out.println("Length: " + len + " pos: " + position);

        //for (int i = 0; i < len; i++) {
        //    System.out.print((output[i] & 0xFF) + " ");
        //}
        //System.out.println("");
        //System.out.println("=======================================");
        byte[] outout = new byte[len];
        System.arraycopy(output, 0, outout, 0, len);
        return outout;
    }

    public static byte[] compress1(byte[] input, ICodec encoder) throws IOException {
        //input = new byte[]{1, 2, 1, 2, 1, 2, 1, 2, 0, 1, 2, 1, 2, 1, 2, 1, 2, 3, 1, 2, 1, 2, 1, 2, 1, 2, 0, 1, 2, 1, 2, 1, 2, 1, 2, 3};
/*
        OUT1:2
        OUT1:3
        OUT1:1
        OUT1:2
        OUT1:3
        OUT1:4
        OUT1:0
        OUT1:5
        OUT1:9
        OUT1:9
        OUT1:5
        OUT1:9
        Duplicated digram:1289
        Replace with6
        OUT1:4
        OUT1:3
*/
        int window = 32;//256 * 2;//8;
        int[] monogramPosition  = new int[256];
        int[] digramPosition    = new int[256 * 256];
        int[] digramReplacement = new int[256 * 256];
        int[] digramReplacementInverse = new int[256];

        int[] monogramCount  = new int[256];
        int[] digramCount    = new int[256 * 256];


        int position = 0;
        int inputLength = input.length;

        byte[] output = new byte[inputLength * 2 ];

        for (int i = 0; i < 256 * 256; i++) {
            digramReplacement[i] = -1;
        }
        for (int i = 0; i < 256; i++) {
            digramReplacementInverse[i] = -1;
        }

        int len = 0;

        boolean full = false;
        int fullCnt = 0, duplicateDigramCnt = 0;

        while (position < inputLength - 1) {
            int m = monogram(input[position]);
            int d = digram(input[position], input[position + 1]);

            int prevMonogramPosition = monogramPosition[m];
            int prevDigramPosition = digramPosition[d];

            if (d == 0) {
                ////System.out.println("DIGRAM ZERO !!!");
            }

            if (digramReplacement[d] != -1) {
                ////System.out.println("Replace digram:" + d + "  with:" + digramReplacement[d]);
                output[len] = (byte) (digramReplacement[d] & 0xFF);
                position++;
                len += 1;
            } else if (digramReplacementInverse[m] != -1) {
                ////System.out.println("OUT2:" + m);
                ////System.out.println("OUT2:" + m);
                ////System.out.println("Cancel replacement of:" + digramReplacementInverse[m] + " for:" + m);

                output[len] = (byte) ((digramReplacementInverse[m] >> 8) & 0xFF);
                len += 1;
                output[len] = (byte) ((digramReplacementInverse[m] >> 0) & 0xFF);;
                len += 1;

                int dd = digramReplacementInverse[m];

                digramReplacement[digramReplacementInverse[m]] = -1;
                digramReplacementInverse[m] = -1;

                int i = 0;
                for (i = 0; i < 256; i++) {
                    if (monogramPosition[i] == 0 && digramReplacementInverse[i] == -1) {
                        digramReplacement[dd] = i;
                        digramReplacementInverse[i] = dd;
                        break;
                    }
                }
            } else {
                ////System.out.println("OUT1:" + m);
                output[len] = (byte) (m & 0xFF);
                len += 1;
            }

            monogramPosition[m] = position;
            digramPosition[d] = position;

            if (position > window) {
                // check if digram was already saw in recent the past, try to replace with a single symbol not saw in the recent past
                if (prevDigramPosition > (position - window)) {
                    duplicateDigramCnt++;
                    ////System.out.println("Duplicated digram:" + d + " count:" + duplicateDigramCnt + " prevPos:" + prevDigramPosition + " current pos:" + position);
                    if (digramReplacement[d] == -1 && !full) {
                        int i = 0;
                        for (i = 0; i < 256; i++) {
                            if (monogramPosition[i] == 0 && digramReplacementInverse[i] == -1) {
                                digramReplacement[d] = i;
                                digramReplacementInverse[i] = d;
                                break;
                            }
                        }
                        if (i == 256) {
                            fullCnt++;
                            ////System.out.println("FULL:" + fullCnt);
                            full = true;
                        }
                    }
                    ////System.out.println("Replace " + d + " with " + digramReplacement[d]);
                }

                int oldD = digram(input[position - window - 1], input[position - window]);
                int oldM = monogram(input[position - window]);
                if (monogramPosition[oldM] < position - window) {
                    full = false;
                    monogramPosition[oldM] = 0;
                }
                if (digramPosition[oldD] < position - window) {
                    digramPosition[oldD] = 0;
                    if (digramReplacement[oldD] != -1) {
                        digramReplacementInverse[digramReplacement[oldD]] = -1;
                        digramReplacement[oldD] = -1;
                    }
                }
            }

            position++;
        }

        //System.out.println("Length: " + len + " pos: " + position);

        //for (int i = 0; i < len; i++) {
        //    System.out.print((output[i] & 0xFF) + " ");
        //}
        //System.out.println("");
        //System.out.println("=======================================");
        byte[] outout = new byte[len];
        System.arraycopy(output, 0, outout, 0, len);
        return outout;
    }



    public static byte[] compress2(byte[] input, ICodec encoder) throws IOException {

        double encodingCostSum = 0;
        int    encodingCostCount = 0;
        double encodingCostAvg = 0;

        int inputLength = input.length;
        int counter = 0;

        //first - encodeInteger file length
        byte b1 = (byte) (inputLength >> 24);
        byte b2 = (byte) ((inputLength << 8) >> 24);
        byte b3 = (byte) ((inputLength << 16) >> 24);
        byte b4 = (byte) ((inputLength << 24) >> 24);

        encoder.encodeInteger(b1 & 0xFF, 256);
        encoder.encodeInteger(b2 & 0xFF, 256);
        encoder.encodeInteger(b3 & 0xFF, 256);
        encoder.encodeInteger(b4 & 0xFF, 256);

        //first MAX_SYNCH_SIZE symbols are encoded as they are (plain-text)
        for (int i = 0; i < MAX_SYNCH_SIZE; i++) {
            encoder.encodeInteger(input[i] & 0xFF, 256);
        }

        //initialize dictionary
        SETree tree = new SETree();

        //start parsing from MAX_SYNCH_SIZE
        int position = MAX_SYNCH_SIZE;

        SEBlock blockToUpdate = null;
        SEBlock blockToUpdate1 = null;
        int positionToAdd = -1;
        int[] countSynch = new int[MAX_SYNCH_SIZE + 1];
        List<Integer> eligibleSynchList = new ArrayList<Integer>();
        while (position < inputLength) {
            ////System.out.println("Pos:" + position + " len:" + inputLength);
            //start from position - synch size and traverse the dictionary (the tree) trying to find a match.
            double minimumEncodingCost = Double.MAX_VALUE;
            SEBlock minimumEncodingCostBlock = null;
            int minimumEncodingCostSynch = -1;

            if (tree.getRoot().getTotalNumberOfSiblings() >= MAX_DICT_SIZE) {
                counter++;
                tree = new SETree();
                //SETree.siblings.clear();
                ////System.out.println("POS: " + position);
                //tree.reset();
            }

            //TODO: count the number of eligible synch. if eligibleSynch = 1 don't need to output synchronization length
            eligibleSynchList.clear();
            int limit = MAX_SYNCH_SIZE;
            for (int synch = 0; synch <= limit; synch++) {
                //start with root:
                SEBlock block = tree.getRoot();
                SEBlock blockTmp = null;
                int level = 0;


                //traverse the tree until a leaf is reached
                double encodingCost = 1; //also, the encoding cost for this block is computed: the product of sizes for each level>synch
                while (true) {
                    blockTmp = (position - synch) + level < inputLength ?
                        block.find(input[(position - synch) + level]) :
                        null;
                    if (level == synch) {
                        if (block.getTotalNumberOfSiblings() > 0) {
                            eligibleSynchList.add(synch);
                        }
                    }
                    if (blockTmp == null) {
                        break;//block is the one we are looking for
                    } else {
                        if (level == synch) {
                            encodingCost = (Math.log(block.getTotalNumberOfSiblings()) / Math.log(2));
                        }
                        level++;
                        block = blockTmp;
                    }
                }//end while
                encodingCost += encoder.getSynchEncodingLenght(synch);
                encodingCost /= (block.getLevel() - synch);
                //choose the block with minimum encoding cost
                if (block.getLevel() > synch) {
                    if (encodingCost < minimumEncodingCost) {
                        minimumEncodingCost = encodingCost;
                        minimumEncodingCostBlock = block;
                        minimumEncodingCostSynch = synch;
                    }
                }
            }//end for


            //encodeInteger synchronization
            try {
                countSynch[minimumEncodingCostSynch]++;
            } catch (Exception e) {
                e.printStackTrace();
            }

            int totalIdx = eligibleSynchList.size();
            int index = eligibleSynchList.indexOf(minimumEncodingCostSynch);
            encoder.encodeSynch(index, totalIdx);

            //encodeInteger block
            if (position > 0) {
                minimumEncodingCostBlock.encode(input[position - 1], minimumEncodingCostSynch, encoder);
            } else {
                minimumEncodingCostBlock.encode((byte) 0, minimumEncodingCostSynch, encoder);
            }

            ////System.out.println("Encode synch: " + minimumEncodingCostSynch  + "/"+minimumEncodingCostBlock + " total tree size:" + tree.getRoot().getTotalNumberOfSiblings());
            //System.out.prinln("[DEBUG]: Compress " + eligibleSynchList + ";" + Arrays.asList( freq ));

            ////System.out.println("[DEBUG]: synch sise:" + minimumEncodingCostSynch + " tree starting from:" + minimumEncodingCostBlock);


            //update dictionary (not by adding a new child to minimumEncodingCostBlock, but, adding a child to "minimumEncodingCostBlock" in prev step)
            if (blockToUpdate != null && tree.getRoot().getTotalNumberOfSiblings() < MAX_DICT_SIZE) {
                SEBlock bAdd = new SEBlock(blockToUpdate, input[positionToAdd]);
                blockToUpdate.addChild(bAdd);
            }

            //move further -> update position
            position += -minimumEncodingCostSynch + minimumEncodingCostBlock.getLevel();

            encodingCostSum += minimumEncodingCost;
            encodingCostCount++;

            encodingCostAvg = (encodingCostSum / encodingCostCount);

            //if (minimumEncodingCost <= encodingCostAvg + 2) {
                blockToUpdate = (position < inputLength) ? minimumEncodingCostBlock : null;
                positionToAdd = position;
            //} else {
            //    blockToUpdate = null;
            //}
            ////System.out.println("Encoding cost AVG:" + encodingCostAvg + " encoding cost:" + minimumEncodingCost + " tree:" + tree.getRoot().getTotalNumberOfSiblings());

        }
        byte[] encoded = encoder.flushEncoding();
        //System.out.println("[DEBUG]:Intitial size:" + inputLength);
        //System.out.println("[DEBUG]:Compressed size:" + encoded.length);
        //System.out.println("[DEBUG]:Report on synchronizations usage");
        for (int i = 0; i < MAX_SYNCH_SIZE + 1; i++) {
            //System.out.println("[DEBUG]:  -synch[" + i + "]=" + countSynch[i]);
        }
        return encoded;
    }


    public static byte[] decompress(byte[] compressed) throws IOException {
        //range encoder used to encodeInteger synch sizes and also dictionary entries.
        //ICodec decoder = new RangeCodecImpl(MAX_SYNCH_SIZE, compressed);
        ICodec decoder = new IntegerHuffmanCodecImpl(MAX_SYNCH_SIZE, compressed);
        return decompress3(compressed, decoder);
    }

    public static byte[] decompress(byte[] compressed, ICodec decoder) throws IOException {
        //read original input length
        int b1 = (int) decoder.decodeInteger(256);
        int b2 = (int) decoder.decodeInteger(256);
        int b3 = (int) decoder.decodeInteger(256);
        int b4 = (int) decoder.decodeInteger(256);

        int outputLength = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;

        //System.out.println("OUTPUT: " + outputLength);

        byte[] output = new byte[(int) outputLength];

        //first MAX_SYNCH_SIZE symbols retrieved as they are
        int position = 0;
        for (position = 0; position < MAX_SYNCH_SIZE; position++) {
            byte val = (byte) (decoder.decodeInteger(256));
            output[position] = val;
        }

        //dictionary
        SETree tree = new SETree();

        //start decompression

        SEBlock blockToUpdate = null;
        int positionToAdd = -1;
        List<Integer> eligibleSynchList = new ArrayList<Integer>(MAX_SYNCH_SIZE + 1);
        while (position < outputLength) {

            if (tree.getRoot().getTotalNumberOfSiblings() == MAX_DICT_SIZE) {
                tree = new SETree();
                //tree.reset();
            }
            //TODO: Not every time we should output a synchronization.
            //count the number of eligible synch. if eligibleSynch = 0 don't need to output synchronization length

            eligibleSynchList.clear();
            for (int synch = 0; synch <= MAX_SYNCH_SIZE; synch++) {
                //start with root:
                SEBlock block = tree.getRoot();
                SEBlock blockTmp = null;
                int level = 0;
                //traverse the tree until a leaf is reached
                while (true) {

                    if (level == synch) {
                        if (block.getTotalNumberOfSiblings() > 0) {
                            eligibleSynchList.add(synch);
                        }
                        break;
                    }
                    blockTmp = block.find(output[(position - synch) + level]);
                    if (blockTmp == null) {
                        break;//block is the one we are looking for
                    } else {

                        level++;
                        block = blockTmp;
                    }
                }//end while
            }//end for

            //read synch size - only if necessary

            int synchSize = eligibleSynchList.get((int) decoder.decodeSynch(eligibleSynchList.size()));
            //int synchSize = (int) decoder.decodeSynch();

            SEBlock block = tree.getRoot();

            //traverse first synchSize levels of the tree
            for (int synch = 0; synch < synchSize; synch++) {
                block = block.find(output[(position - synchSize) + synch]);
            }
            int total = block.getTotalNumberOfSiblings();
            int index = (int) decoder.decodeInteger(total);
            block = block.getSibligAtIndex(index);

            ////System.out.println("Decode " + synchSize + "/" + block + "/block:" + index + "/from:" + total );
            ////System.out.println("[DEBUG]: Decompress  ;" + Arrays.asList( freq ));

            ////System.out.println("[DEBUG]: synch sise:" + synchSize + " tree starting from:" + block);

            byte[] decoded = block.decode(synchSize);

            //System.out.print( "[DEBUG]: decoded" + new String(decoded));
            /*while (true) {
              int index = decoder.decodeInteger( block.getNextLevelSize() + 1 );//number of next level + 1 mark-up stop symbol
              blockTmp = block.getChild( index );
              if (blockTmp != null) {
                block = blockTmp;
              } else {
                break;
              }
            }*/

            //decode block and copy decode bytes to output
            //byte[] decoded = block.decode( synchSize );

            if (position + decoded.length < outputLength) {
                System.arraycopy(decoded, 0, output, position, decoded.length);
            }

            //move further -> update position
            position += -synchSize + block.getLevel();

            //update dictionary (not by adding a new child to minimumEncodingCostBlock, but, adding a child to "minimumEncodingCostBlock" in prev step)
            if (blockToUpdate != null) {
                blockToUpdate.addChild(new SEBlock(blockToUpdate, output[positionToAdd]));
            }


            blockToUpdate = position < outputLength ? block : null;
            positionToAdd = position;
        }
        ////System.out.println("[DEBUG]: " + new String(output));

        return output;
    }
}

/*

Fir Y o problema NP completa.

Fie X un program care rezolva Y.
Cat de mare e programul Y?








       A BCDEFG
         BCDEFG

Cost of encoding Index is Log(Dictionary size) if synch = 0;
Cost of encoding Index is Log(Dictionary size | A ) if synch = 1;


For each X such that: AX is in the Dictionary
  Efficiency if AX would be chosen: Log (Dictionary size | A) / 1
  Therefore should not consider dictionary items W starting with X such that: W = XV
  and Log(Dictionary) / Length(W) >  Log (Dictionary|A) / 1

  |W|


  Why?
  If the unknown dictionary item would have Length(W) < Log(Dictionary) / Log(A), then it would be
  much more efficient to select AX.
End For


If Synch = 0
{
   if (block to encod size = 1)
   {
       If dictionary contains A X, then the  symbols starting with X should not be considered if:
           length < Log(Dictionary size)/Log(Dictionary  size | A )
   }
}
*/







