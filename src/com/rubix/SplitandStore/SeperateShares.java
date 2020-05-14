package com.rubix.SplitandStore;

public class SeperateShares {
    public static String getShare(int[][] shares, int payloadLength, int i) {

        int[] K = new int[8 * payloadLength];
        int m = 8;
        StringBuilder share = new StringBuilder();
        for (int j = 0; j < K.length * m; j++)
            share.append(shares[i][j]);

        return share.toString();
    }
}
