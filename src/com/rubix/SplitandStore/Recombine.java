package com.rubix.SplitandStore;

public class Recombine {

    public static int[] stringToIntArray(String string) {
        int[] intArray = new int[string.length()];
        for (int k = 0; k < string.length(); k++) {
            if (string.charAt(k) == '0')
                intArray[k] = 0;
            else
                intArray[k] = 1;
        }
        return intArray;
    }

    public static String recombine(int[] Share1, int[] Share2, int[] Share3)
    {
        StringBuilder recStr = new StringBuilder();
        int payloadlength = 205; // fix the payload size here ( 33070 )

        
        //reconstruction with ideal contrast secret sharing, just shown an example of combining share1(essential share), share2 and share3
        int[] K = new int[8 * payloadlength];

        int m = 8;
        int j, count1;
        int[] COMB = new int[K.length * m];
        int[] REC_K = new int[K.length]; // Reconstructed K

        // if you need to combine share1(essential share), share3 and share4, replace the value of s0=0, s1=2 and s2=3;
        for (j = 0; j < K.length * m; j++) {

            if (Share1[j] == 1 | Share2[j] == 1 | Share3[j] == 1)
                COMB[j] = 1;
            else
                COMB[j] = 0;

        }
/*
System.out.println("The secret COMB");
            for(j=0;j<K.length*m;j++)
            {
                System.out.print(COMB[j]);
            }
            System.out.println();
*/

        int count = 0;
        int val = 0;

        for (j = 0; j < K.length * m; j++) {
            if (COMB[j] == 1)
                count = count + 1;
            if ((j + 1) % m == 0) {
                if (count == 8)
                    REC_K[val] = 1;
                else
                    REC_K[val] = 0;
                val = val + 1;
                count = 0;
            }
        }

//Prints the secret matrix (K) and reconstructed K (REC_K) in console
//
//        System.out.println("The Reconstructed Matrix");
//        for(j=0;j<K.length;j++)
//        {
//            System.out.print(REC_K[j]);
//        }
//        System.out.println();
//

        /* this statement ..............................................................................................................*/

        for (count1 = 0; count1 < payloadlength; count1++) {
            int dec_value = 0;
            int base = 1;
            for (j = 0; j < 8; j++) {
                dec_value += REC_K[count1 * 8 + j] * base;
                base = base * 2;
            }

            recStr.append((char) dec_value);
        }
        System.out.println("[Split_135]The Reconstructed secret is: " + recStr);
        return recStr.toString();
    }

}
