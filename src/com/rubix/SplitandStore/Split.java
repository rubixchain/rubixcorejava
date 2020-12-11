package com.rubix.SplitandStore;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.ArrayList;
import java.util.Collections;

import static com.rubix.Resources.Functions.LOGGER_PATH;

public class Split {
    public static int[][] Share;
    public static Logger SplitLogger = Logger.getLogger(Split.class);

    public static void split(String str) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        int[] K = new int[8 * str.length()];

        int j, count1;
        for (count1 = 0; count1 < str.length(); count1++) {
            int n1 = (str.charAt(count1));

            for (j = 0; j < 8; j++) {
                if (n1 % 2 == 0)
                    K[count1 * 8 + j] = 0;
                else K[count1 * 8 + j] = 1;
                n1 = n1 / 2;
            }
        }

//        System.out.println("The secret matrix K");
//        for(j=0;j<K.length;j++)
//        {
//            System.out.print(K[j]);
//        }
//        System.out.println();

        /* Once only the code is executing from here to ................................................*/

        //Here you need to embed the secret sharing (sharing K matrix) and secret reconstruction code

        int n = 5, m = 8; // no of participants n = 5 and pixel expansion m = 8
        int x, i1, j1;
        int g;
        int u1;

        //Basis matrix S0 and S1 for (1,3,5) scheme used to share a 0 and 1 element of K respectively


        int[][] S0 = {{0, 0, 0, 0, 1, 1, 1, 1}, {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 0 ,1, 1, 0, 1}, {1, 1, 1, 0, 1, 0, 1, 1}, {1, 1, 1, 0, 0, 1, 1, 1}};
        int[][] S1 = {{1, 1, 1, 1, 0, 0, 0, 0}, {1, 1, 1, 0, 1, 1, 1, 0}, {1, 1, 1, 0 ,1, 1, 0, 1}, {1, 1, 1, 0, 1, 0, 1, 1}, {1, 1, 1, 0, 0, 1, 1, 1}};
        int[][] tempS0 = new int[n][m];
        int[][] tempS1 = new int[n][m];
        Share = new int[n][K.length * m];
//        int[] COMB = new int[K.length * m];
//        int[] REC_K = new int[K.length]; // Reconstructed K

        //Prints the Basis Matrices in console
/*
        System.out.println("The matrix S0");
        for(i=0;i<n;i++) {
            for (j = 0; j < m; j++) {
                System.out.print(S0[i][j]);
            }
            System.out.println();
        }
        System.out.println("The matrix S1");
        for(i=0;i<n;i++) {
            for (j = 0; j < m; j++) {
                System.out.print(S1[i][j]);
            }
            System.out.println();
        }
*/
        //generating n shares
        g = 0;
        for (j = 0; j < K.length; j++) {
            ArrayList<Integer> numbers = new ArrayList<>();
            for (i1 = 0; i1 < m; i1++)
                numbers.add(i1);
            Collections.shuffle(numbers);

            // permutation
            for (j1 = 0; j1 < m; j1++) {
                for (i1 = 0; i1 < n; i1++) {
                    tempS0[i1][j1] = S0[i1][numbers.get(j1)];
                    tempS1[i1][j1] = S1[i1][numbers.get(j1)];
                }
            }


            x = 0;
            if (K[j] == 0) {
                do {
                    for (u1 = 0; u1 < n; u1++)
                        Share[u1][g] = tempS0[u1][x];
                    g++;
                    x++;
                } while (x < m);

            } else {
                do {
                    for (u1 = 0; u1 < n; u1++)
                        Share[u1][g] = tempS1[u1][x];
                    g++;
                    x++;
                } while (x < m);

            }

        }
        SplitLogger.debug("(1,3,5) Share Generation Successful");
//Prints the shares in console

//        for(u1=0;u1<n;u1++)
//        {
//            System.out.println();
//            System.out.println("Share"+(u1+1));
//            System.out.println();
//            for (j = 0; j < K.length*m; j++)
//                System.out.print(Share[u1][j]);
//            System.out.println();
//            System.out.println();
//        }

//Reconstruction code from here

        //reconstruction with ideal contrast secret sharing, just shown an example of combining share1(essential share), share2 and share3

//        int s0 = 0;
//        int s1 = 1;
//        int s2 = 2;

        // if you need to combine share1(essential share), share3 and share4, replace the value of s0=0, s1=2 and s2=3;

//        for (j = 0; j < K.length * m; j++) {
//
//            if (Share[s0][j] == 1 | Share[s1][j] == 1 | Share[s2][j] == 1)
//                COMB[j] = 1;
//            else
//                COMB[j] = 0;
//
//        }

/*
System.out.println("The secret COMB");
            for(j=0;j<K.length*m;j++)
            {
                System.out.print(COMB[j]);
            }
            System.out.println();
*/

//        int count = 0;
//        int val = 0;
//
//        for (j = 0; j < K.length * m; j++) {
//            if (COMB[j] == 1)
//                count = count + 1;
//            if ((j + 1) % m == 0) {
//                if (count == 8)
//                    REC_K[val] = 1;
//                else
//                    REC_K[val] = 0;
//                val = val + 1;
//                count = 0;
//            }
//        }

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
//
//        for (count1 = 0; count1 < str.length(); count1++) {
//            int dec_value = 0;
//            int base = 1;
//            for (j = 0; j < 8; j++) {
//                dec_value += REC_K[count1 * 8 + j] * base;
//                base = base * 2;
//            }
//
//            recStr.append((char) dec_value);
//        }
//        System.out.println("[Split_135]The Reconstructed secret is: " + recStr);
    }
    public static int[][] get135Shares() {
        return Share;
    }
}