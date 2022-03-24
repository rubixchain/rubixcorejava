package com.rubix.Resources;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.log4j.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.rubix.Resources.Functions.*;

public class FractionChooser {
    public static String output;

    public static JSONArray tokenHeader;

    public static Logger FractionChooserLogger = Logger.getLogger(FractionChooser.class);

    public static JSONArray calculate(int amount) {
        JSONArray tokensList = new JSONArray();
        tokenHeader = new JSONArray();
        JSONObject tknmap = new JSONObject();
        try {
            int index = 0;
            LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
            LinkedHashMap<Object, Object> usedmap = new LinkedHashMap<>();
            List<JSONArray> bnk = new ArrayList<>();
            if (amount < 1) {
                FractionChooserLogger.warn("Invalid Transaction Amount");
                output = "Please make a valid transaction";
                return tokensList;
            }
            JSONArray mapList = new JSONArray(readFile(PAYMENTS_PATH + "TokenMap.json"));
            int i;
            for (i = 0; i < mapList.length(); i++) {
                JSONObject tempJsonObject = mapList.getJSONObject(i);
                String type = tempJsonObject.getString("type");
                int valueInt = tempJsonObject.getInt("value");
                tknmap.put(String.valueOf(valueInt), type);
                String lists = readFile(PAYMENTS_PATH + tempJsonObject.getString("type") + ".json");
                JSONArray tempJsonArray = new JSONArray(lists);
                bnk.add(i, tempJsonArray);
                int size = tempJsonArray.length();
                map.put(Integer.valueOf(valueInt), Integer.valueOf(size));
                usedmap.put(Integer.valueOf(valueInt), Integer.valueOf(0));
            }
            List<Integer> keyList = new ArrayList(map.keySet());
            for (i = map.size() - 1; i > 0; i--) {
                if (((Integer)keyList.get(i)).intValue() <= amount) {
                    index = i;
                    break;
                }
            }
            while (amount != 0) {
                int valueInt = ((Integer)keyList.get(index)).intValue();
                if (((Integer)map.get(Integer.valueOf(valueInt))).intValue() > 0 && valueInt <= amount) {
                    amount -= valueInt;
                    int temp = ((Integer)usedmap.get(Integer.valueOf(valueInt))).intValue();
                    int temp1 = ((Integer)map.get(Integer.valueOf(valueInt))).intValue();
                    usedmap.put(Integer.valueOf(valueInt), Integer.valueOf(++temp));
                    map.put(Integer.valueOf(valueInt), Integer.valueOf(--temp1));
                } else if (index != 0) {
                    index--;
                } else {
                    FractionChooserLogger.warn("Insufficient Amount in the Wallet. Required " + amount + " currency");
                    output = "Balance not sufficient, need " + amount + " more currency";
                    return tokensList;
                }
                if (valueInt > amount && index != 0)
                    index--;
            }
            for (i = 0; i < keyList.size(); i++) {
                for (int j = 0; j < ((Integer)usedmap.get(keyList.get(i))).intValue(); j++) {
                    tokensList.put(((JSONArray)bnk.get(i)).getJSONObject(j).getString("tokenHash"));
                    tokenHeader.put(tknmap.get(String.valueOf(keyList.get(i))));
                }
            }
        } catch (JSONException e) {
            FractionChooserLogger.error("JSON Exception Occurred", (Throwable)e);
            e.printStackTrace();
        }
        FractionChooserLogger.debug("Tokens chosen to be sent: " + tokensList);
        return tokensList;
    }
}

