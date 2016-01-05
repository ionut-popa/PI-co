package ro.dudydu.sec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.io.IOException;

import ro.dudydu.io.ICodec;

/**
 * A block consists of:<br>
 * - prefix - reference to parent block<br>
 * - b      - a byte<br>
 * The string represented by this block is created by putting
 * in the reverse order: <code>this.b, this.prefix.b, ... this.{prefix}*.b</code>
 *
 * @author popai
 */

public class SEBlock {
    private static int time = 0;
    private static List<SEBlock> leafs = new ArrayList<SEBlock>();

    public SEBlock root;
    public SEBlock prefix;
    public byte b;
    public int indexInParent = 0;

    private int level;

    private HashMap<Byte, SEBlock> nextLevel = new HashMap<Byte, SEBlock>();
    public int totalNumberOfChildren = 0;
    public int totalNumberOfSiblings = 0;
    private int timeAdded = 0;



    public SEBlock(SEBlock prefix, byte b) {
        this.prefix = prefix;
        this.b = b;
        this.level = (prefix != null ? prefix.level : -1) + 1;
    }

    /**
     * @return number of block from this to the root.
     */
    public int getLevel() {
        return level;
    }

    public HashMap<Byte, SEBlock> getNextLevel() {
        return nextLevel;
    }

    /**
     * Find a block for "b" among current block siblings.
     * Get index of this block among children.
     *
     * @param b
     * @return block
     */
    public SEBlock find(byte b) {
        SEBlock block = nextLevel.get(b);
        return block;
    }

    /**
     * s
     * Total number of block starting with this one.
     *
     * @return
     */
    public int getNextLevelSize() {
        return nextLevel.size();
    }

    public int countLeftBrothers(boolean inclusive) {
        int count = 0;
        for (Entry<Byte, SEBlock> entry : this.prefix.nextLevel.entrySet()) {
            if (entry.getKey() == this.b) {
                return count;
            }
            count += entry.getValue().totalNumberOfChildren + 1;
        }
        return count;
    }

    public void addChild(SEBlock blockToAdd) {
        if (nextLevel.get(blockToAdd.b) != null) {
            //System.out.println("ALREADY EXISTS");
            return;
        }
        //System.out.println("[DEBUG]: Add:" + blockToAdd + " as a child of:" + this);

        SEBlock current = this;
        while (current != null) {
            current.totalNumberOfChildren++;
            current = current.prefix;
        }
        nextLevel.put(blockToAdd.b, blockToAdd);
        //SETree.siblings.remove(this);
        //SETree.siblings.add(blockToAdd);
    }

/*
  public SEBlock getChild( int index ) {
    if (index < this.nextLevel.size()) {
      return (SEBlock) this.nextLevel.values().toArray()[index];
    }
    return null;
  }*/


    public void encode(byte prev, int synchronizationSize, ICodec encoder) throws IOException {
        //[DEBUG]: String str = "";
        //[DEBUG]: double encodingSize = Math.log( SECodec.MAX_SYNCH_SIZE + 1 ) / Math.log( 2 );
        SEBlock current = this;
        int index = 0;

        //[DEBUG]: encodingSize += Math.log( current.getNextLevelSize() + 1 ) / Math.log( 2 );

        int total = 0;
        while (current.prefix != null) {
            if (current.level > synchronizationSize) {
                //[DEBUG]: encodingSize += Math.log( current.prefix.getNextLevelSize() + 1 ) / Math.log( 2 );
                index += current.countLeftBrothers(false) + 1; //brother at left and current one
                total = current.prefix.totalNumberOfChildren;
            }
            //[DEBUG]: str = new String(new byte[]{current.b}) + str;
            current = current.prefix;
        }
//    if (synchronizationSize == 0)
//    {
//      SEBlock x = current.getNextLevel().get(prev);
//      if (x != null)
//      {
//        total -= x.getTotalNumberOfSiblings();
//      }
//
//      /*for (Byte b : x.getNextLevel().keySet())
//      {
//        total -= current.getNextLevel().get(b).getTotalNumberOfSiblings();
//      }*/
//
//    }

        index = index - 1;
        //[DEBUG]: str += "\t\t:" + index + " from:" + total ;
        encoder.encodeInteger(index, total);
        //System.out.println("[DEBUG]:" + synchronizationSize + " / " + str );
    }

    public SEBlock getSibligAtIndex(int index) {
        SEBlock current = this;
        int cumulativeIndex = 0;
        boolean found = false;
        while (!found) {
            for (Entry<Byte, SEBlock> entry : current.nextLevel.entrySet()) {
                int add = entry.getValue().totalNumberOfChildren;
                if (cumulativeIndex + add >= index) {
                    current = entry.getValue();
                    if (cumulativeIndex == index) {
                        found = true;
                    }
                    cumulativeIndex += 1;
                    break;
                } else {
                    cumulativeIndex += add + 1;
                }
            }
        }
        return current;
    }

    public byte[] decode(int synchSize) {
        //[DEBUG]: String str = "";
        SEBlock current = this;

        byte[] retBuffer = new byte[this.level - synchSize];
        while (current.prefix != null) {
            //[DEBUG]: str = new String(new byte[]{current.b}) + str;
            retBuffer[current.level - synchSize - 1] = current.b;
            current = current.prefix;
            if (current.level <= synchSize) {
                break;
            }
        }
        //System.out.println("[DEBUG:]" + str + "->" + new String(retBuffer));
        return retBuffer;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String str = "";
        SEBlock current = this;
        while (current.prefix != null) {
            str = new String(new byte[]{current.b}) + str;
            current = current.prefix;
        }
        return str;
    }

    public int getTotalNumberOfSiblings() {
        return totalNumberOfChildren;
    }
}
