package com.rubix.LevelDb;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

public class sortBasedOnDate implements Comparator<JSONObject>{

    /*
        Overiiden compare method to compare Json Object based on Date field
        @return int value based on comparison
    */
    @Override
    public int compare(JSONObject o1, JSONObject o2) {
        int compare=0;
        try{
            String date1=o1.get("Date").toString();
            String date2=o2.get("Date").toString();
            Date Date1=new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(date1);
            Date Date2=new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(date2);
            compare= Date1.compareTo(Date2);
        }
        catch(JSONException e)
        {
            e.printStackTrace();
        }
        catch(ParseException e)
        {
            e.printStackTrace();
        }
        return compare;
    }
}
