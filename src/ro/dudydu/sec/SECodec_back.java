package ro.dudydu.sec;

import ro.dudydu.io.ICodec;
import ro.dudydu.io.RangeCodecImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


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
public class SECodec_back {

    //[INFO]:x.patch, original:1916367, compressed:554842, time compress:1391

    public static final int MAX_SYNCH_SIZE = 3;//1;//5;

    private static final int MAX_DICT_SIZE = 1 << 16;


    public static byte[] compress(byte[] input) throws IOException {
        ICodec encoder = new RangeCodecImpl(MAX_SYNCH_SIZE);
        //ICodec encoder = new IntegerHuffmanCodecImpl(MAX_SYNCH_SIZE);
        return compress(input, encoder);
    }

    public static byte[] compress(byte[] input, ICodec encoder) throws IOException {

        int inputLength = input.length;

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

        List<SEBlock> blocksToAddToDictionary = new ArrayList<SEBlock>();
        List<SEBlock> blocksToAddToDictionaryNext = new ArrayList<SEBlock>();

        int positionToAdd = -1;
        int[] countSynch = new int[MAX_SYNCH_SIZE + 1];
        List<Integer> eligibleSynchList = new ArrayList<Integer>();
        while (position < inputLength) {
            blocksToAddToDictionary.clear();


            //start from position - synch size and traverse the dictionary (the tree) trying to find a match.
            double minimumEncodingCost = Double.MAX_VALUE;
            SEBlock minimumEncodingCostBlock = null;
            int minimumEncodingCostSynch = -1;

            if (tree.getRoot().getTotalNumberOfSiblings() >= MAX_DICT_SIZE) {
                tree = new SETree();
                System.out.println("Pos:" + position + " len:" + inputLength);
                //SETree.siblings.clear();
                //System.out.println("POS: " + position);
                //tree.reset();
            }

            //TODO: count the number of eligible synch. if eligibleSynch = 1 don't need to output synchronization length
            eligibleSynchList.clear();
            for (int synch = 0; synch <= MAX_SYNCH_SIZE; synch++) {
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

                    if ( (position - synch) + level < inputLength) {
                        SEBlock bAdd = new SEBlock(block, input[(position - synch) + level]);
                        blocksToAddToDictionary.add(bAdd);
                    }

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

            //System.out.println("Encode synch: " + minimumEncodingCostSynch  + "/"+minimumEncodingCostBlock + " total tree size:" + tree.getRoot().getTotalNumberOfSiblings());
            //System.out.prinln("[DEBUG]: Compress " + eligibleSynchList + ";" + Arrays.asList( freq ));

            //System.out.println("[DEBUG]: synch sise:" + minimumEncodingCostSynch + " tree starting from:" + minimumEncodingCostBlock);


            //update dictionary (not by adding a new child to minimumEncodingCostBlock, but, adding a child to "minimumEncodingCostBlock" in prev step)
//            if (blockToUpdate != null && (tree.getRoot().getTotalNumberOfSiblings() < 16000 || minimumEncodingCostSynch >= 1)) {
//                SEBlock bAdd = new SEBlock(blockToUpdate, input[positionToAdd]);
//                blockToUpdate.addChild(bAdd);
//            }

            //System.out.println("ADD to dictionary:" + blocksToAddToDictionary.size() + " Total:" + tree.getRoot().getTotalNumberOfSiblings());
            for (SEBlock b : blocksToAddToDictionaryNext) {
                    b.prefix.addChild(b);
            }
            blocksToAddToDictionaryNext.clear();
            for (SEBlock b : blocksToAddToDictionary) {
                if (b.getLevel() - 1 <= minimumEncodingCostBlock.getLevel()) {
                    blocksToAddToDictionaryNext.add(b);
                }
            }

            blocksToAddToDictionaryNext = blocksToAddToDictionary;

            //move further -> update position
            position += -minimumEncodingCostSynch + minimumEncodingCostBlock.getLevel();
            //blocksToAddToDictionaryNext.clear();
            //blocksToAddToDictionaryNext.addAll(blocksToAddToDictionary);
            //blockToUpdate = (position < inputLength) ? minimumEncodingCostBlock : null;

//            if (minimumEncodingCostSynch == 0 &&
//                    blockToUpdate  != null &&
//                    tree.getRoot().getTotalNumberOfSiblings() > (1<<14) &&
//                    blockToUpdate.getLevel() < 2) {
//
//                blockToUpdate = null;
//            }
//            positionToAdd = position;
        }
        byte[] encoded = encoder.flushEncoding();
        System.out.println("[DEBUG]:Intitial size:" + inputLength);
        System.out.println("[DEBUG]:Compressed size:" + encoded.length);
        System.out.println("[DEBUG]:Report on synchronizations usage");
        for (int i = 0; i < MAX_SYNCH_SIZE + 1; i++) {
            System.out.println("[DEBUG]:  -synch[" + i + "]=" + countSynch[i]);
        }
        return encoded;
    }


    public static byte[] decompress(byte[] compressed) throws IOException {
        //range encoder used to encodeInteger synch sizes and also dictionary entries.
        ICodec decoder = new RangeCodecImpl(MAX_SYNCH_SIZE, compressed);
        //ICodec decoder = new IntegerHuffmanCodecImpl(MAX_SYNCH_SIZE, compressed);
        return decompress(compressed, decoder);
    }

    public static byte[] decompress(byte[] compressed, ICodec decoder) throws IOException {
        //read original input length
        int b1 = (int) decoder.decodeInteger(256);
        int b2 = (int) decoder.decodeInteger(256);
        int b3 = (int) decoder.decodeInteger(256);
        int b4 = (int) decoder.decodeInteger(256);

        int outputLength = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;

        System.out.println("OUTPUT: " + outputLength);

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

            //System.out.println("Decode " + synchSize + "/" + block + "/block:" + index + "/from:" + total );
            //System.out.println("[DEBUG]: Decompress  ;" + Arrays.asList( freq ));

            //System.out.println("[DEBUG]: synch sise:" + synchSize + " tree starting from:" + block);

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
        //System.out.println("[DEBUG]: " + new String(output));

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







