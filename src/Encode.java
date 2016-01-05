//Vincent Tran
//CS 1501
//Will perform Move to Front transform on ASCII characters
//Format: java Encode inputfilename.extension outputfilename.extension
//Tested files: .bin

import java.lang.*;
import java.util.*;
import java.io.*;

public class Encode
{
        public static void main(String [] args) throws IOException
        {
                ArrayList<Character> data = new ArrayList<Character>();
                int[] dict = new int[256];
                for(int i = 0; i < 256; i++)
                        dict[i] = i;

                String current;
                //Will read each character from the input file and store into an ArrayList
                BufferedReader br = new BufferedReader(new FileReader(args[0]));
                current = br.readLine();
                for(int i=0; i < current.length(); i++)
                        data.add(current.charAt(i));

                int index=0;
                int charVal;
                String output = "";

                //Performs the Move-To-Front transform on the ArrayList
                for(char x : data)
                {
                        charVal = (int) x;
                        for(int i = 0; i < 256; i++)
                        {
                                if(dict[i] == charVal)
                                        index = i;
                        }

                        for(int check = index; check != 0; check--)
                                dict[check] = dict[check-1];

                        dict[0] = charVal;
                        output = output + index + " ";
                }

                //Writes into a file with the user-defined name and extension
                File file = new File(args[1]);
                Writer fileOut = new BufferedWriter(new FileWriter(file));
                fileOut.write(output);
                fileOut.close();
        }
}
