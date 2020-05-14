package com.rubix.AuthenticateNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

public class SplitShares {
    static ArrayList temp = new ArrayList<String>();
    static int n = 2,count=0;
    static JSONArray arr = new JSONArray();

    public static String convertToBinary(String strs){
        byte[] bytes = strs.getBytes();
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes){
            int val = b;
            for (int i = 0; i < 8; i++){
                binary.append((val & 128) == 0 ? 0 : 1);
                val <<= 1;
            }
            binary.append(' ');
        }
        return binary.toString();
    }

    public static JSONArray splits(String s,int cnt) throws IOException, JSONException {
        String temp1,temp2;
        if(cnt<n) {
            count++;
            Interact4tree inter = new Interact4tree(s);
            inter.shareCreate();
            temp = inter.getItBack();
            temp1 = (String) temp.get(0);
            temp2 = (String) temp.get(1);
            if(cnt==n-1) {
                JSONObject share1 = new JSONObject();
                JSONObject share2 = new JSONObject();
                share1.put("val", temp1);
                share2.put("val", temp2);
                arr.put(share1);
                arr.put(share2);
            }
            cnt++;
            splits(temp1, cnt);
            splits(temp2, cnt);
        }
        else
            return null;
        return arr;
    }

    public static ArrayList recombine(ArrayList inp) throws IOException
    {
        int i;
        String t1,t2;
        ArrayList temp = new ArrayList<String>();
        Interact4tree temp1 = new Interact4tree("tempdata");
        for (i = 0; i <inp.size()-1 ; i+=2)
        {
            t1 = (String) inp.get(i);
            t2 = (String) inp.get(i+1);
            temp.add(temp1.getBack(t1,t2,false));
        }
        if(temp.size()>1)
            temp = recombine(temp);
        return temp;
    }
}
