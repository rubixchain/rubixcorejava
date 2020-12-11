package com.rubix.Resources;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IntegrityCheck {
    public static String message;

    public static boolean didIntegrity(String did){
        if(did.length() != 46) {
            message = "Wrong DID Format (DID length: 46)";
            return false;
        }
        else if(!did.subSequence(0, 2).equals("Qm")) {
            message = "Wrong DID Format (DID begins with Qm)";
            return false;
        }
        else
            return true;
    }

    public static boolean dateIntegrity(String begin, String end) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date d1 = null;
        Date d2 = null;
        try {
            d1 = formatter.parse(begin);
            d2 = formatter.parse(end);
        } catch (ParseException e) {
            message = "Wrong format date (Date Format: yyyy-MM-dd)";
            return false;
        }

        if(d1.compareTo(d2) > 0) {
            message = "Begin date occurs after End date";
            return false;
        }
        return true;

    }

    public static boolean txnIdIntegrity(String ID){
        if(ID.length() != 64) {
            message = "Wrong Transaction ID format (Length: 64)";
            return false;
        }
        return true;
    }

    public static boolean rangeIntegrity(int a, int b){
        if(a < 0 || b < 0) {
            message = "Range below bounds";
            return false;
        }
        if(a > b){
            message = "Start bound larger than End bound";
            return false;
        }
        return true;
    }
}
