package com.rubix.LevelDb;

import java.util.Comparator;

import org.json.JSONException;
import org.json.JSONObject;

public class sortBasedOnSerialNo implements Comparator<JSONObject>{

	/*
        Overiiden compare method to compare Json Object based on SerialNo field
        @return int value based on comparison
    */
	@Override
	public int compare(JSONObject o1, JSONObject o2) {
        int compare=0;
        try {
			compare= o1.getInt("serialNoQst") > o2.getInt("serialNoQst") ? 1 : 
			(o1.getInt("serialNoQst") < o2.getInt("serialNoQst") ? -1 : 0);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return compare;
	}

}