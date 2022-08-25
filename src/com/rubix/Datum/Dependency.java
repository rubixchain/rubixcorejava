package com.rubix.Datum;

import static com.rubix.Resources.Functions.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map.Entry;

import com.rubix.Resources.Functions;

import org.json.JSONArray;
import org.json.JSONException;

public class Dependency {

	public static HashMap<String, String> dataTableHashMap() {
		String dataTableContent = Functions.readFile(
				DATA_PATH + "DataTable.json");
        JSONArray dataTableArray = null;
		try {
			dataTableArray = new JSONArray(dataTableContent);
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        HashMap<String, String> dataTableMap = new HashMap<String, String>();
        for (int ctr = 0; ctr < dataTableArray.length(); ctr++) {
            try {
				dataTableMap.put(dataTableArray.getJSONObject(ctr).get("didHash").toString(),
				        dataTableArray.getJSONObject(ctr).get("peerid").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return dataTableMap;

	}
	public static HashMap<String, String> widDataTableHashMap() {
		String dataTableContent = Functions.readFile(
				DATA_PATH + "DataTable.json");
        JSONArray dataTableArray = null;
		try {
			dataTableArray = new JSONArray(dataTableContent);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        HashMap<String, String> dataTableMap = new HashMap<String, String>();
        for (int ctr = 0; ctr < dataTableArray.length(); ctr++) {
            try {
				dataTableMap.put(dataTableArray.getJSONObject(ctr).get("peerid").toString(),
				        dataTableArray.getJSONObject(ctr).get("walletHash").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return dataTableMap;

	}
	
	public static String getPIDfromDID(String did,HashMap<String, String>dataTable) {
		String pidString = "";
		if (dataTable.containsKey(did)) {
			pidString = dataTable.get(did);
        } else {
        	pidString = "Not Found";
        }
		return pidString;
	}
	
	public static String getDIDfromPID(String pid,HashMap<String, String>dataTable) {
		String didString = "Not Found";
		for (Entry<String, String> entry : dataTable.entrySet()) {
            if (entry.getValue().equals(pid)) {
                didString = entry.getKey();
                System.out.println("The key for value " + pid + " is " + entry.getKey());
                break;
            }
        }
		return didString;
		}
	
	public static String getWIDfromPID(String pid,HashMap<String, String>dataTable) {
		String widString = "";
		if (dataTable.containsKey(pid)) {
			widString = dataTable.get(pid);
        } else {
        	widString = "Not Found";
        }
		return widString;
	}
	
	public static void checkDatumPath() {
        boolean status = false;
        String configPath = "";
        String OSName = getOsName();
        if (OSName.contains("Windows")) {
            configPath = ("C:\\Rubix\\");
        } else if (OSName.contains("Mac")) {
            configPath = "/Applications/Rubix/";
        } else if (OSName.contains("Linux")) {
            configPath = "/home/" + getSystemUser() + "/Rubix/";
        }
        String configContentString = readFile(configPath + "config.json");
        JSONArray configContentArray = null;
		try {
			configContentArray = new JSONArray(configContentString);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        if (!configContentString.contains("DATUM_CHAIN_PATH")) {
            try {
				configContentArray.getJSONObject(0).put("DATUM_CHAIN_PATH", configPath + "DATUM/");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            writeToFile(configPath + "config.json", configContentArray.toString(), false);
        } 

        configContentString = readFile(configPath + "config.json");
        if (configContentString.contains("DATUM_CHAIN_PATH")) {
            status = true;
        }

    }
	

}
