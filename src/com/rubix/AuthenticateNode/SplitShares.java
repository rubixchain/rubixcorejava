package com.rubix.AuthenticateNode;

import static com.rubix.Resources.Functions.IPFS_PORT;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.json.JSONArray;

import io.ipfs.api.IPFS;

public class SplitShares {
    public static Logger SplitSharesLogger = Logger.getLogger(SplitShares.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    static ArrayList<String> temp = new ArrayList<>();
    static int n = 2, count = 0;
    public static JSONArray initiate;
    static JSONArray arr = new JSONArray();

    /**
     * This is the main method to be called for converting the secret into binary
     * and creating shares
     *
     * @param decentralizedID Secret string
     * @return Array of shares
     * @throws IOException Handles IO Exceptions
     */
    private static boolean generateShares(BufferedImage decentralizedID) throws IOException {

        String content = PropImage.img2bin(decentralizedID);
        Interact interact = new Interact(content);
        return interact.createShare();
    }

}
