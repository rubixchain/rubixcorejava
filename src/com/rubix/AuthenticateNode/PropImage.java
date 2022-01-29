package com.rubix.AuthenticateNode;



import java.awt.image.BufferedImage;

import static com.rubix.Resources.Functions.binarytoDec;
import static com.rubix.Resources.Functions.intToBinary;


public class PropImage {

    public static String img2bin(BufferedImage image){
        int p, R, G, B;
        StringBuilder bin = new StringBuilder();
        int width = image.getWidth();
        int height = image.getHeight();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                p = image.getRGB(j, i);
                R = (p >> 16) & 0xff;
                G = (p >> 8) & 0xff;
                B = p & 0xff;
                bin.append(intToBinary(R));
                bin.append(intToBinary(G));
                bin.append(intToBinary(B));
            }
        }
        return bin.toString();
    }

    public static BufferedImage generateRGB(String info, int width, int height){
        String dec = binarytoDec(info);
        String[] splitval = dec.split("\\s");
        if(splitval.length != width * height * 3)
            return null;
        else {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int a = 255, l = 0, q;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    q = (a << 24) | (Integer.parseInt(splitval[l]) << 16) | (Integer.parseInt(splitval[l + 1]) << 8) | Integer.parseInt(splitval[l + 2]);
                    image.setRGB(j, i, q);
                    l = l + 3;
                }
            }
            return image;
        }

    }
    public static String getpos(String s1, String s2){
        int i,j,temp,temp1,sum;
        if(s1.length()!=s2.length()||s1.length()<1){
            return "";
        }
        StringBuilder tempo = new StringBuilder();
        for(i=0;i<s1.length();i+=8){
            sum=0;
            for(j=i;j<i+8;j++){
                temp = s1.charAt(j)-'0';
                temp1 = s2.charAt(j)-'0';
                sum+=temp*temp1;
            }
            sum%=2;
            tempo.append(sum);
        }
        return tempo.toString();
    }

}
