package ro.dudydu.sec;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SETree {

    private SEBlock root;

    public static List<SEBlock> siblings = new ArrayList<SEBlock>();

    public SETree() {
        this.root = new SEBlock(null, (byte) 0);

        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
            SEBlock newBlock = new SEBlock(root, (byte) b);
            this.root.addChild(newBlock);

//            for (int b1 = Byte.MIN_VALUE; b1 <= Byte.MAX_VALUE; b1++) {
//                SEBlock newBlock1 = new SEBlock(newBlock, (byte) b1);
//                newBlock.addChild(newBlock1);
//            }
        }
        System.out.println("Initial tree size:" + this.root.getTotalNumberOfSiblings());
        //this.root.addChild( new SEBlock(root, Byte.MAX_VALUE ) );
    }

    public SEBlock getRoot() {
        return root;
    }


    void addChildrenx(SEBlock node, SEBlock newNode) {
        if (node.getTotalNumberOfSiblings() > 200) {
            //System.out.println("NODE LEN: " + node.getLevel() + " SIBLINGS:" + node.getTotalNumberOfSiblings() + "  " + node);
            for (Byte k : node.getNextLevel().keySet()) {
                SEBlock childNewNode = new SEBlock(newNode, k);
                SEBlock child = node.getNextLevel().get(k);
                newNode.addChild(childNewNode);
                addChildrenx(child, childNewNode);
            }
        }
    }



    public void reset() {
        this.root = new SEBlock(null, (byte) 0);

        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {

            SEBlock newBlock = new SEBlock(root, (byte) b);
            this.root.addChild(newBlock);

//            for (int b1 = Byte.MIN_VALUE; b1 <= Byte.MAX_VALUE; b1++) {
//                SEBlock newBlock1 = new SEBlock(newBlock, (byte) b1);
//                newBlock.addChild(newBlock1);
//            }
        }

        List<SEBlock> preserve = new ArrayList<SEBlock>();
        for (SEBlock s : siblings) {
            if (s.getLevel() > 30) {
                preserve.add(s);
            }
        }



        //SEBlock node    = root;
        //SEBlock newNode = newRoot;

        //addChildrenx(node, newNode);

        //root = newRoot;
        siblings = new ArrayList<SEBlock>();

        System.out.println("Preserve: " + preserve.size());

        for (SEBlock s : preserve) {
            SEBlock x = s;
            List<Byte>  seq = new ArrayList<Byte>();
            while (x.getLevel() >= 1) {
                seq.add(x.b);
                x = x.prefix;
            }
            SEBlock parent = root;
            for (int i = seq.size() - 1; i >=20; i--) {
                //System.out.println("ADD: "+ seq.get(i));

                SEBlock newBlock = new SEBlock(parent, seq.get(i));
                parent.addChild(newBlock);
                parent = parent.getNextLevel().get(seq.get(i));
            }
            //System.out.println("-----");
        }


        System.out.println("TREE RESET SIZE:" + root.getTotalNumberOfSiblings());
    }
}
