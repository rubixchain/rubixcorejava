package com.rubix.AuthenticateNode;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.randomPositions;
import static com.rubix.Resources.Functions.strToIntArray;
import static com.rubix.Resources.Functions.syncDataTable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class Authenticate {
    public static Logger AuthenticateLogger = Logger.getLogger(Authenticate.class);
    public static int verifyCount = 0;

    /**
     * This method is used to authenticate a node in Rubix implementing text based
     * two level NLSS.
     * <P>
     * It is customized for 32 positions verification. The position can be changed
     * by
     * modifying the numberofpositions for integer array sizes accordingly
     * 
     * @param detailString Details for verification
     * @return boolean returns true if verified and false if not verified
     * @throws IOException   handles IO Exception
     * @throws JSONException handles JSON Exception
     */

    public static boolean verifySignature(String detailString) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        verifyCount++;
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
        JSONObject details = new JSONObject(detailString);
        String decentralizedID = details.getString("did");
        String hash = details.getString("hash");
        String signature = details.getString("signature");
        syncDataTable(decentralizedID, null);
        String walletIdIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", decentralizedID);
        nodeData(decentralizedID, walletIdIpfsHash, ipfs);

        BufferedImage senderDIDImage = ImageIO.read(new File(DATA_PATH + decentralizedID + "/DID.png"));
        String senderDIDBin = PropImage.img2bin(senderDIDImage);
        BufferedImage senderWIDImage = ImageIO.read(new File(DATA_PATH + decentralizedID + "/PublicShare.png"));
        String walletID = PropImage.img2bin(senderWIDImage);

        StringBuilder senderWalletID = new StringBuilder();
        int[] SenderSign = strToIntArray(signature);
        JSONObject P = randomPositions("verifier", hash, 32, SenderSign);
        int[] posForSign = (int[]) P.get("posForSign");
        int[] originalPos = (int[]) P.get("originalPos");
        for (int positionsLevelTwoTrail : posForSign)
            senderWalletID.append(walletID.charAt(positionsLevelTwoTrail));

        String recombinedResult = PropImage.getpos(senderWalletID.toString(), signature);
        int[] positionsLevelZero = new int[32];

        for (int k = 0; k < 32; k++)
            positionsLevelZero[k] = ((originalPos[k]) / 8);

        StringBuilder decentralizedIDForAuth = new StringBuilder();
        for (int value : positionsLevelZero)
            decentralizedIDForAuth.append(senderDIDBin.charAt(value));
        if (recombinedResult.equals(decentralizedIDForAuth.toString())) {
            return true;
        } else {
            AuthenticateLogger.info("Verification Failed");
            return false;
        }

    }

}
