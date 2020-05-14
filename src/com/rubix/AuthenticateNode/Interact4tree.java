package com.rubix.AuthenticateNode;

import java.io.IOException;
import java.util.ArrayList;



public class Interact4tree {
    public String privateShare = new String();
    public String candidateShare = new String();
    public String bits;
    public StringBuilder pvt, cnd;
    public int cand1[][], secret[][];

    public Interact4tree(String s) {
        bits = s;
    }

    public ArrayList<String> getItBack() {
        ArrayList temp = new ArrayList<String>();
        temp.add(privateShare);
        temp.add(candidateShare);
        return temp;
    }

    public void shareCreate() {

        int i, j;
        secret = new int[bits.length()][8];
        cand1 = new int[bits.length()][8];
        SecretShare share;
        pvt = new StringBuilder();
        cnd = new StringBuilder();
        for (i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) == '0') {
                share = new SecretShare(0);
                share.starts();
                for (j = 0; j < 8; j++) {
                    secret[i][j] = share.S0[j];
                    cand1[i][j] = share.Y1[j];
                    pvt.append(share.S0[j]);
                    cnd.append(share.Y1[j]);
                }
            }
            if (bits.charAt(i) == '1') {
                share = new SecretShare(1);
                share.starts();
                for (j = 0; j < 8; j++) {
                    secret[i][j] = share.S0[j];
                    cand1[i][j] = share.Y1[j];
                    pvt.append(share.S0[j]);
                    cnd.append(share.Y1[j]);
                }
            }

        }
        privateShare = pvt.toString();
        candidateShare = cnd.toString();

        checkShare();
    }

    public void checkShare() {

        int i, j, sum;
        boolean verified = true;

        for (i = 0; i < secret.length; i++) {
            sum = 0;
            for (j = 0; j < secret[i].length; j++)
                sum += secret[i][j] * cand1[i][j];
            sum %= 2;
            if (sum != (bits.charAt(i) - 48))
                verified = false;
        }
        if (verified)
            System.out.println("Verified :Correct");
        else
            System.out.println("Verified :Wrong");


    }

    public String getBack(String s1, String s2, Boolean finals) throws IOException {
        int i, j, temp, temp1, sum;
        if (s1.length() != s2.length() || s1.length() < 1) {
            System.out.println("Shares corrupted");
            return "null";
        }
        StringBuilder tempo = new StringBuilder();
        String result = "";
        char nextChar;
        for (i = 0; i < s1.length(); i += 8) {
            sum = 0;
            for (j = i; j < i + 8; j++) {
                temp = s1.charAt(j) - '0';
                temp1 = s2.charAt(j) - '0';
                sum += temp * temp1;
            }
            sum %= 2;
            tempo.append(sum);
        }

        if (finals) {
            for (i = 0; i < tempo.length(); i += 8) {
                nextChar = (char) Integer.parseInt(tempo.substring(i, i + 8), 2);
                result += nextChar;
            }
        } else
            result = tempo.toString();
        return result;

    }
}
