package com.rubix.Resources;

import com.rubix.AuthenticateNode.PropImage;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.json.JsonArray;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.rubix.Resources.APIHandler.networkInfo;
import static com.rubix.Resources.IPFSNetwork.*;


public class Functions {

    public static boolean mutex = false;
    public static String DATA_PATH = "";
    public static String TOKENS_PATH = "";
    public static String TOKENCHAIN_PATH = "";
    public static String LOGGER_PATH = "";
    public static String WALLET_DATA_PATH = "";
    public static String PAYMENTS_PATH = "";
    public static int RECEIVER_PORT, GOSSIP_SENDER, GOSSIP_RECEIVER, QUORUM_PORT, SENDER2Q1, SENDER2Q2, SENDER2Q3, SENDER2Q4, SENDER2Q5, SENDER2Q6, SENDER2Q7;
    public static int QUORUM_COUNT;
    public static int SEND_PORT;
    public static int IPFS_PORT;
    public static String SYNC_IP = "";
    public static int APPLICATION_PORT;
    public static String EXPLORER_IP = "";
    public static String USERDID_IP = "";
    public static String configPath = "";
    public static String dirPath = "";
    public static boolean CONSENSUS_STATUS;
    public static JSONObject QUORUM_MEMBERS;
    public static JSONArray BOOTSTRAPS;

    public static Logger FunctionsLogger = Logger.getLogger(Functions.class);

    public static void setDir() {
        String OSName = getOsName();
        if (OSName.contains("Windows"))
            dirPath = "C:\\Rubix\\";
        else if (OSName.contains("Mac"))
            dirPath = "/Applications/Rubix/";
        else if (OSName.contains("Linux"))
            dirPath = "/home/" + getSystemUser() + "/Rubix/";
        else
            System.exit(0);
    }

    public static void setConfig() {
        setDir();
        configPath = dirPath.concat("config.json");
    }

    /**
     * This method sets the required paths used in the functions
     */
    public static void pathSet() {
        setConfig();
        String configFileContent = readFile(configPath);

        JSONArray pathsArray;
        try {
            pathsArray = new JSONArray(configFileContent);

            DATA_PATH = pathsArray.getJSONObject(0).getString("DATA_PATH");
            TOKENS_PATH = pathsArray.getJSONObject(0).getString("TOKENS_PATH");
            LOGGER_PATH = pathsArray.getJSONObject(0).getString("LOGGER_PATH");
            TOKENCHAIN_PATH = pathsArray.getJSONObject(0).getString("TOKENCHAIN_PATH");
            WALLET_DATA_PATH = pathsArray.getJSONObject(0).getString("WALLET_DATA_PATH");
            PAYMENTS_PATH = pathsArray.getJSONObject(0).getString("PAYMENTS_PATH");

            SEND_PORT = pathsArray.getJSONObject(1).getInt("SEND_PORT");
            RECEIVER_PORT = pathsArray.getJSONObject(1).getInt("RECEIVER_PORT");
            GOSSIP_RECEIVER = pathsArray.getJSONObject(1).getInt("GOSSIP_RECEIVER");
            GOSSIP_SENDER = pathsArray.getJSONObject(1).getInt("GOSSIP_SENDER");
            QUORUM_PORT = pathsArray.getJSONObject(1).getInt("QUORUM_PORT");
            SENDER2Q1 = pathsArray.getJSONObject(1).getInt("SENDER2Q1");
            SENDER2Q2 = pathsArray.getJSONObject(1).getInt("SENDER2Q2");
            SENDER2Q3 = pathsArray.getJSONObject(1).getInt("SENDER2Q3");
            SENDER2Q4 = pathsArray.getJSONObject(1).getInt("SENDER2Q4");
            SENDER2Q5 = pathsArray.getJSONObject(1).getInt("SENDER2Q5");
            SENDER2Q6 = pathsArray.getJSONObject(1).getInt("SENDER2Q6");
            SENDER2Q7 = pathsArray.getJSONObject(1).getInt("SENDER2Q7");
            IPFS_PORT = pathsArray.getJSONObject(1).getInt("IPFS_PORT");
            APPLICATION_PORT = pathsArray.getJSONObject(1).getInt("APPLICATION_PORT");

            SYNC_IP = pathsArray.getJSONObject(2).getString("SYNC_IP");
            EXPLORER_IP = pathsArray.getJSONObject(2).getString("EXPLORER_IP");
            USERDID_IP = pathsArray.getJSONObject(2).getString("USERDID_IP");

            CONSENSUS_STATUS = pathsArray.getJSONObject(3).getBoolean("CONSENSUS_STATUS");
            QUORUM_COUNT = pathsArray.getJSONObject(3).getInt("QUORUM_COUNT");

            QUORUM_MEMBERS = pathsArray.getJSONObject(4);

            BOOTSTRAPS =pathsArray.getJSONArray(5);


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void nodeData(String did, String wid, IPFS ipfs) throws IOException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        File dataFolder = new File(DATA_PATH + did + "/");

        if (!(dataFolder.exists())) {
            dataFolder.mkdirs();
            IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");
            IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");
        }
        else {
            File DIDFile = new File(dataFolder+"/DID.png");
            File WIDFile = new File(dataFolder+"/PublicShare.png");
            if(!DIDFile.exists())
                IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");
            if(!WIDFile.exists())
                IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");
            String didHash = add(DATA_PATH + did + "/DID.png", ipfs);
            String widHash = add(DATA_PATH + did + "/PublicShare.png", ipfs);
            if (!didHash.equals(did) || !widHash.equals(wid)) {
                FunctionsLogger.debug("New DID Created for user " + did);
                File didFile = new File(DATA_PATH + did + "/DID.png");
                File widFile = new File(DATA_PATH + did + "/PublicShare.png");
                didFile.delete();
                widFile.delete();
                IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");
                IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");
            }
        }
    }


    /**
     * This method gets the currently logged in username
     *
     * @return lineID The current user
     */

    public static String getSystemUser() {
        Process processID;
        String lineID = "";
        try {
            String OS = getOsName();
            String[] command = new String[3];
            if (OS.contains("Mac") || OS.contains("Linux")) {
                command[0] = "bash";
                command[1] = "-c";
            } else if (OS.contains("Windows")) {
                command[0] = "cmd.exe";
                command[1] = "/c";
            }
            command[2] = "whoami";

            processID = Runtime.getRuntime().exec(command);
            InputStreamReader inputStreamReader = new InputStreamReader(processID.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            lineID = bufferedReader.readLine();
            processID.waitFor();
            inputStreamReader.close();
            bufferedReader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return lineID;
    }


    /**
     * This method calculates different types of hashes as mentioned in the passed parameters for the mentioned message
     *
     * @param message   Input string to be hashed
     * @param algorithm Specification of the algorithm used for hashing
     * @return (String) hash
     */

    public static String calculateHash(String message, String algorithm) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            FunctionsLogger.error("Invalid Cryptographic Algorithm", e);
            e.printStackTrace();
        }
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] c = new byte[messageBytes.length];
        System.arraycopy(messageBytes, 0, c, 0, messageBytes.length);
        final byte[] hashBytes = digest.digest(messageBytes);
        return bytesToHex(hashBytes);
    }

    /**
     * This method Converts the string passed to it into an integer array
     *
     * @param inputString Input string to be converted to integer array
     * @return (Integer Array) outputArray
     */
    public static int[] strToIntArray(String inputString) {
        int[] outputArray = new int[inputString.length()];
        for (int k = 0; k < inputString.length(); k++) {
            if (inputString.charAt(k) == '0')
                outputArray[k] = 0;
            else
                outputArray[k] = 1;
        }
        return outputArray;
    }

    /**
     * This method Converts the integer array passed to it into String
     *
     * @param inputArray Input integer array to be converted to String
     * @return (String) outputString
     */
    public static String intArrayToStr(int[] inputArray) {
        StringBuilder outputString = new StringBuilder();
        for (int i : inputArray) {
            if (i == 1)
                outputString.append("1");
            else
                outputString.append("0");
        }
        return outputString.toString();
    }

    /**
     * This method converts the passed byte array into Hexadecimal String (hex)
     *
     * @param inputHash Byte Array to be concerted into hexadecimal string
     * @return outputHexString
     */
    public static String bytesToHex(byte[] inputHash) {
        StringBuilder outputHexString = new StringBuilder();
        for (byte b : inputHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) outputHexString.append('0');
            outputHexString.append(hex);
        }
        return outputHexString.toString();
    }


    /**
     * This method returns the content of the file passed to it
     *
     * @param filePath Location of the file to be read
     * @return File Content as string
     */
    public static String readFile(String filePath) {
        FileReader fileReader;
        StringBuilder fileContent = new StringBuilder();
        try {
            fileReader = new FileReader(filePath);
            int i;
            while ((i = fileReader.read()) != -1)
                fileContent.append((char) i);
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent.toString();
    }


    /**
     * This method writes the mentioned data into the file passed to it
     * This also allows to take a decision on whether or not to append the data to the already existing content in the file
     *
     * @param filePath     Location of the file to be read and written into
     * @param data         Data to be added
     * @param appendStatus Decides whether or not to append the new data into the already existing data
     */

    public synchronized static void writeToFile(String filePath, String data, Boolean appendStatus) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            File writeFile = new File(filePath);
            FileWriter fw;

            fw = new FileWriter(writeFile, appendStatus);

            fw.write(data);
            fw.close();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
    }

    /**
     * This method helps to sign a data with the selectively disclosed private share
     *
     * @param filePath Location of the Private share
     * @param hash     Data to be signed on
     * @return Signature for the data
     * @throws IOException Handles IO Exceptions
     */

    public static String getSignFromShares(String filePath, String hash) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        String p1 = intArrayToStr(p1Sign);
        return p1;
    }


    /**
     * This function will sign on JSON data with private share
     *
     * @param detailsString Details(JSONObject) to sign on
     * @return Signature
     * @throws IOException   Handles IO Exceptions
     * @throws JSONException Handles JSON Exceptions
     */
    public static String sign(String detailsString) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject details = new JSONObject(detailsString);
        String DID = details.getString("did");
        String filePath = DATA_PATH + DID + "/PrivateShare.png";
        String hash = calculateHash(detailsString, "SHA3-256");

        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        return intArrayToStr(p1Sign);
    }

    /**
     * This function will connect to the receiver
     *
     * @param connectObjectString Details required for connection[DID, appName]
     * @throws JSONException Handles JSON Exception
     */
    public static void establishConnection(String connectObjectString) {
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
        JSONObject connectObject = null;
        try {
            connectObject = new JSONObject(connectObjectString);
            String DID = connectObject.getString("did");
            String appName = DID.concat(connectObject.getString("appName"));
            String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", DID);
            swarmConnect(peerID, ipfs);
            forward(appName, SEND_PORT, peerID);
        } catch (JSONException e) {
            FunctionsLogger.error("JSONException Occurred", e);
        }

    }


    /**
     * This function will allow for a user to listen on a particular appName
     *
     * @param connectObject Details required for connection[DID, appName]
     * @throws JSONException Handles JSON Exception
     */
    public static void listenThread(JSONObject connectObject) {
        String DID = null;
        try {
            DID = connectObject.getString("did");
            String appName = DID.concat(connectObject.getString("appName"));
            listen(appName, RECEIVER_PORT);
        } catch (JSONException e) {
            FunctionsLogger.error("JSONException Occurred", e);
        }

    }


    /**
     * This function converts any integer to its binary form
     *
     * @param a An integer
     * @return Binary form of the input integer
     */
    public static String intToBinary(int a) {
        String temp = Integer.toBinaryString(a);
        while (temp.length() != 8) {
            temp = "0" + temp;
        }
        return temp;
    }

    /**
     * This function converts any binary value to its Decimal form
     *
     * @param bin Binary value
     * @return Decimal format of the input binary
     */
    public static String binarytoDec(String bin) {
        StringBuilder result = new StringBuilder();
        int val;
        for (int i = 0; i < bin.length(); i += 8) {
            val = Integer.parseInt(bin.substring(i, i + 8), 2);
            result.append(val);
            result.append(' ');
        }
        return result.toString();
    }

    /**
     * This method updates the mentioned JSON file
     * It can add a new data or remove an existing data
     *
     * @param operation Decides whether to add or remove data
     * @param filePath  Locatio nof the JSON file to be updated
     * @param data      Data to be added or removed
     */

    public static void updateJSON(String operation, String filePath, String data) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            while (mutex) {
            }

            mutex = true;
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
                JSONArray js = new JSONArray();
                writeToFile(file.toString(), js.toString(), false);
            }
            String fileContent = readFile(filePath);
            JSONArray contentArray = new JSONArray(fileContent);

            for (int i = 0; i < contentArray.length(); i++) {
                JSONObject contentArrayJSONObject = contentArray.getJSONObject(i);
                Iterator iterator = contentArrayJSONObject.keys();
                if (operation.equals("remove")) {
                    while (iterator.hasNext()) {
                        String tempString = iterator.next().toString();
                        if (contentArrayJSONObject.getString(tempString).equals(data))
                            contentArray.remove(i);
                    }
                }
            }
            writeToFile(filePath, contentArray.toString(), false);

            if (operation.equals("add")) {
                JSONArray newData = new JSONArray(data);
                for (int i = 0; i < newData.length(); i++)
                    contentArray.put(newData.getJSONObject(i));
                writeToFile(filePath, contentArray.toString(), false);
            }
            mutex = false;
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
    }

    /**
     * This method gets you a required data from a JSON file with a tag to be compared with
     *
     * @param filePath Location of the JSON file
     * @param get      Data to be fetched from the file
     * @param tagName  Name of the tag to be compared with
     * @param value    Value of the tag to be compared with
     * @return Data that is fetched from the JSON file
     */
//    public static String getValues(String filePath, String get, String tagName, String value) {
//        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
//        String resultString = "";
//        JSONParser jsonParser = new JSONParser();
//
//        try (FileReader reader = new FileReader(filePath)) {
//            Object obj = jsonParser.parse(reader);
//            org.json.simple.JSONArray List = (org.json.simple.JSONArray) obj;
//            for (Object o : List) {
//                org.json.simple.JSONObject js = (org.json.simple.JSONObject) o;
//
//                String itemCompare = js.get(tagName).toString();
//                if (value.equals(itemCompare)) {
//                    resultString = js.get(get).toString();
//                }
//            }
//        } catch (ParseException e) {
//            FunctionsLogger.error("JSON Parser Exception Occurred", e);
//            e.printStackTrace();
//        } catch (IOException e) {
//            FunctionsLogger.error("IOException Occurred", e);
//            e.printStackTrace();
//        }
//
//
//        return resultString;
//    }
    public static String getValues(String filePath, String get, String tagName, String value) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String resultString = "";
        try {
            JSONArray searchSpace = new JSONArray(readFile(filePath));
            for (int i = 0; i < searchSpace.length(); i++) {
                JSONObject temp = searchSpace.getJSONObject(i);
                if (temp.get(tagName).equals(value)) {
                    resultString = temp.getString(get);
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return resultString;
    }

    /**
     * This method gets the Operating System that is being run the your computer
     *
     * @return Name of the running Operating System
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * This function calculates the minimum number of quorum peers required for consensus to work
     *
     * @return Minimum number of quorum count for consensus to work
     */
    public static int minQuorum() {
        return (((QUORUM_COUNT - 1) / 3) * 2) + 1;
    }

    /**
     * This function calculates the minimum number of quorum peers required for consensus to work
     *
     * @return Minimum number of quorum count for consensus to work
     */
    public static int minQuorum(int count) {
        return (((count - 1) / 3) * 2) + 1;
    }


    /**
     * This method checks if Quorum is available for consensus
     *
     * @param quorum List of peers
     * @param ipfs   IPFS instance
     * @return final list of all available Quorum peers
     */
    public static ArrayList<String> QuorumCheck(JSONArray quorum, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        ArrayList<String> peers = new ArrayList<>();

        if (quorum.length()>=minQuorum(7)) {
            for (int i = 0; i < quorum.length(); i++) {
                String quorumPeer;
                try {
                    quorumPeer = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", quorum.getString(i));
                    if (checkSwarmConnect().contains(quorumPeer)) {
                        peers.add(quorumPeer);
                        FunctionsLogger.debug(quorumPeer);
                    }
                } catch (JSONException e) {
                    FunctionsLogger.error("JSON Exception Occurred", e);
                    e.printStackTrace();
                }
            }

                FunctionsLogger.debug("Quorum Peer IDs : " + peers);
                return peers;
        } else
            return null;
    }




    public static void QuorumSwarmConnect(JSONArray quorum, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            for (int i = 0; i < quorum.length(); i++) {
                String quorumPeer;
                try {
                    quorumPeer = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", quorum.getString(i));
                    IPFSNetwork.swarmConnect(quorumPeer,ipfs);
                } catch (JSONException e) {
                    FunctionsLogger.error("JSON Exception Occurred", e);
                    e.printStackTrace();
                }
            }


    }



    /**
     * This method identifies the Peer ID of the system by IPFS during installation
     *
     * @param filePath Location of the file in which your IPFS Peer ID is stored
     * @return Your system's Peer ID assigned by IPFS
     */
    public static String getPeerID(String filePath) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONArray fileContentArray;
        String peerid = "";
        JSONObject fileContentArrayJSONObject;
        try {
            String fileContent = Functions.readFile(filePath);
            fileContentArray = new JSONArray(fileContent);
            fileContentArrayJSONObject = fileContentArray.getJSONObject(0);
            peerid = fileContentArrayJSONObject.getString("peerid");
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return peerid;
    }

    public static int[] getPrivatePosition(int[] positions, int[] privateArray) {
        int[] PrivatePosition = new int[positions.length];
        for (int k = 0; k < positions.length; k++) {
            int a = positions[k];
            int b = privateArray[a];
            PrivatePosition[k] = b;
        }
        return PrivatePosition;
    }

    public static JSONObject randomPositions(String role, String hash, int numberOfPositions, int[] pvt1) throws JSONException {

        int u = 0, l = 0, m = 0;
        long st = System.currentTimeMillis();
        int[] hashCharacters = new int[256];
        int[] randomPositions = new int[32];
        int[] randPos = new int[256];
        int[] finalPositions, pos;
        int[] originalPos = new int[32];
        int[] posForSign = new int[32 * 8];
        for (int k = 0; k < numberOfPositions; k++) {

            hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
            randomPositions[k] = (((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % 2048;
            originalPos[k] = (randomPositions[k] / 8) * 8;

            pos = new int[32];
            pos[k] = originalPos[k];
            randPos[k] = pos[k];
            finalPositions = new int[8];
            for (int p = 0; p < 8; p++) {
                posForSign[u] = randPos[k];
                randPos[k]++;
                u++;

                finalPositions[l] = pos[k];
                pos[k]++;
                l++;
                if (l == 8)
                    l = 0;
            }
            if (role.equals("signer")) {
                int[] p1 = getPrivatePosition(finalPositions, pvt1);
                hash = calculateHash(hash + intArrayToStr(originalPos) + intArrayToStr(p1), "SHA3-256");
            } else {
                int[] p1 = new int[8];
                for (int i = 0; i < 8; i++) {
                    p1[i] = pvt1[m];
                    m++;
                }
                hash = calculateHash(hash + intArrayToStr(originalPos) + intArrayToStr(p1), "SHA3-256");
            }

        }

        JSONObject resultObject = new JSONObject();
        resultObject.put("originalPos", originalPos);
        resultObject.put("posForSign", posForSign);
        long et = System.currentTimeMillis();
        FunctionsLogger.debug("Time taken for randomPositions Calculation " + (et - st));
        return resultObject;
    }

    /**
     * This functions extends the random positions into 64 times longer
     *
     * @param randomPositions Array of random positions
     * @param positionsCount  Number of positions required
     * @return Extended array of positions
     */
    public static int[] finalPositions(int[] randomPositions, int positionsCount) {
        int[] finalPositions = new int[positionsCount * 64];
        int u = 0;
        for (int k = 0; k < positionsCount; k++) {
            for (int p = 0; p < 64; p++) {
                finalPositions[u] = randomPositions[k];
                randomPositions[k]++;
                u++;
            }
        }
        return finalPositions;
    }

    /**
     * This function deletes the mentioned file
     *
     * @param fileName Location of the file to be deleted
     */
    public static void deleteFile(String fileName) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            Files.deleteIfExists(Paths.get(fileName));
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        FunctionsLogger.debug("File Deletion successful");
    }


    /**
     * This functions picks the required number of quorum members from the mentioned file
     *
     * @param filePath Location of the file
     * @param hash     Data from which positions are chosen
     * @return List of chosen members from the file
     */
    public static ArrayList<String> quorumChooser(String filePath, String hash) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        ArrayList<String> quorumList = new ArrayList();
        try {
            String fileContent = readFile(filePath);
            JSONArray blockHeight = new JSONArray(fileContent);

            int[] hashCharacters = new int[256];
            var randomPositions = new ArrayList<Integer>();
            HashSet<Integer> positionSet = new HashSet<>();
            for (int k = 0; positionSet.size() != 7; k++) {
                hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
                randomPositions.add((((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % blockHeight.length());
                positionSet.add(randomPositions.get(k));
            }

            for (Integer integer : positionSet)
                quorumList.add(blockHeight.getJSONObject(integer).getString("peer-id"));
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumList;
    }

    /**
     * This function is to be initially called to setup the environment of your project
     */
    public static void launch() {
        pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        int syncFlag = 0;
        try {
            executeIPFSCommands("ipfs daemon");
            if (!SYNC_IP.contains("127.0.0.1")) {
                networkInfo();
                syncFlag = 1;
            }

        } catch (MalformedURLException e) {
            FunctionsLogger.error("MalformedURL Exception Occurred", e);
            e.printStackTrace();
        } catch (ProtocolException e) {
            FunctionsLogger.error("Protocol Exception Occurred", e);
            e.printStackTrace();
        } catch (IOException e) {
            FunctionsLogger.error("IO Exception Occurred", e);
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (syncFlag == 1)
            FunctionsLogger.info("Synced Successfully!");
        else
            FunctionsLogger.info("Not synced! Try again after sometime.");
    }

    /**
     * This function checks if the Rubix Working Directory is present or not
     *
     * @return A message
     * @throws JSONException handle all JSON Exceptions
     */
    public static String checkDirectory() throws JSONException {
        setDir();
        File mainDir = new File(dirPath);
        if (!mainDir.exists()) {
            mainDir.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Main Directory Missing");
            result.put("status", "Failed");
            return result.toString();
        }
        setConfig();
        File configFile = new File(configPath);

        if (!configFile.exists()) {
            configFile.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Config File Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        pathSet();
        File dataFolder = new File(DATA_PATH);
        File loggerFolder = new File(LOGGER_PATH);
        File tokensFolder = new File(TOKENS_PATH);
        File tokenChainsFolder = new File(TOKENCHAIN_PATH);
        File walletDataFolder = new File(WALLET_DATA_PATH);

        if (!dataFolder.exists() || !loggerFolder.exists() || !tokenChainsFolder.exists() || !tokensFolder.exists() || !walletDataFolder.exists()) {
            dataFolder.delete();
            loggerFolder.delete();
            tokenChainsFolder.delete();
            tokensFolder.delete();
            walletDataFolder.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Inner Folders Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        File didFile = new File(DATA_PATH + "DID.json");
        File dataTable = new File(DATA_PATH + "DataTable.json");
        if (!didFile.exists() || !dataTable.exists()) {
            didFile.delete();
            dataTable.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("nfo", "DID / Datatable File Missing ");
            result.put("status", "Failed");
            return result.toString();
        }

        String didContent = readFile(DATA_PATH + "DID.json");
        JSONArray didArray = new JSONArray(didContent);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        File didFolder = new File(DATA_PATH + myDID + "/");
        if (!didFolder.exists()) {
            didFolder.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "DID Folder Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        File didImage = new File(DATA_PATH + myDID + "/DID.png");
        File widImage = new File(DATA_PATH + myDID + "/PublicShare.png");
        File pvtImage = new File(DATA_PATH + myDID + "/PrivateShare.png");
        if (!didImage.exists() || !widImage.exists() || !pvtImage.exists()) {
            didImage.delete();
            didImage.delete();
            didImage.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Shares Images Missing");
            result.put("status", "Failed");
            return result.toString();
        }
        JSONObject returnObject = new JSONObject();
        returnObject.put("message", "User successfully registered!");
        returnObject.put("status", "Success");
        return returnObject.toString();
    }

    public static String mineToken(int level, int tokenNumber) {

        String tokenHash = calculateHash(String.valueOf(tokenNumber), "SHA-256");
        String levelHex = Integer.toHexString(level);
        if(level<16)
        levelHex=String.valueOf(0).concat(levelHex);
        String token = String.valueOf(0) + levelHex + tokenHash;
        return token;
    }


    public static String toBinary(int x, int len) {
        if (len > 0) {
            return String.format("%" + len + "s",
                    Integer.toBinaryString(x)).replaceAll(" ", "0");
        }
        return null;
    }

    public static Boolean integrityCheck(String consensusID){
        File file = new File(WALLET_DATA_PATH+"QuorumSignedTransactions.json");
        if(file.exists()) {
            if (getValues(file.getAbsolutePath(), "senderdid", "consensusID", consensusID).equals(""))
                return true;
            else
                return false;
        }
        else
            return true;
    }

    public static Date getCurrentUtcTime() throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        SimpleDateFormat localDateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        return localDateFormat.parse( simpleDateFormat.format(new Date()) );
    }


    public static void updateQuorum(JSONArray quorumArray,JSONArray signedQuorumList,boolean status,int type) throws IOException, JSONException {

        if (type==1) {
            String urlQuorumUpdate = "http://13.76.134.226:9595/updateQuorum";
            URL objQuorumUpdate = new URL(urlQuorumUpdate);
            HttpURLConnection conQuorumUpdate = (HttpURLConnection) objQuorumUpdate.openConnection();

            conQuorumUpdate.setRequestMethod("POST");
            conQuorumUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            conQuorumUpdate.setRequestProperty("Accept", "application/json");
            conQuorumUpdate.setRequestProperty("Content-Type", "application/json");
            conQuorumUpdate.setRequestProperty("Authorization", "null");

            JSONObject dataToSendQuorumUpdate = new JSONObject();
            dataToSendQuorumUpdate.put("completequorum", quorumArray);
            dataToSendQuorumUpdate.put("signedquorum",signedQuorumList);
            dataToSendQuorumUpdate.put("status",status);
            String populateQuorumUpdate = dataToSendQuorumUpdate.toString();

            conQuorumUpdate.setDoOutput(true);
            DataOutputStream wrQuorumUpdate = new DataOutputStream(conQuorumUpdate.getOutputStream());
            wrQuorumUpdate.writeBytes(populateQuorumUpdate);
            wrQuorumUpdate.flush();
            wrQuorumUpdate.close();

            int responseCodeQuorumUpdate = conQuorumUpdate.getResponseCode();
            FunctionsLogger.debug("Sending 'POST' request to URL : " + urlQuorumUpdate);
            FunctionsLogger.debug("Post Data : " + populateQuorumUpdate);
            FunctionsLogger.debug("Response Code : " + responseCodeQuorumUpdate);

            BufferedReader inQuorumUpdate = new BufferedReader(
                    new InputStreamReader(conQuorumUpdate.getInputStream()));
            String outputQuorumUpdate;
            StringBuffer responseQuorumUpdate = new StringBuffer();
            while ((outputQuorumUpdate = inQuorumUpdate.readLine()) != null) {
                responseQuorumUpdate.append(outputQuorumUpdate);
            }
            inQuorumUpdate.close();

        }
    }


    public static JSONArray getQuorum(String betaHash,String gammaHash,String senderDidIpfsHash,String receiverDidIpfsHash,int tokenslength) throws IOException, JSONException {
        JSONArray quorumArray;
        String urlQuorumPick = "http://13.76.134.226:9595/getQuorum";
        URL objQuorumPick = new URL(urlQuorumPick);
        HttpURLConnection conQuorumPick = (HttpURLConnection) objQuorumPick.openConnection();

        conQuorumPick.setRequestMethod("POST");
        conQuorumPick.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conQuorumPick.setRequestProperty("Accept", "application/json");
        conQuorumPick.setRequestProperty("Content-Type", "application/json");
        conQuorumPick.setRequestProperty("Authorization", "null");

        JSONObject dataToSendQuorumPick = new JSONObject();
        dataToSendQuorumPick.put("betahash", betaHash);
        dataToSendQuorumPick.put("gammahash", gammaHash);
        dataToSendQuorumPick.put("sender", senderDidIpfsHash);
        dataToSendQuorumPick.put("receiver", receiverDidIpfsHash);
        dataToSendQuorumPick.put("tokencount",tokenslength);
        String populateQuorumPick = dataToSendQuorumPick.toString();

        conQuorumPick.setDoOutput(true);
        DataOutputStream wrQuorumPick = new DataOutputStream(conQuorumPick.getOutputStream());
        wrQuorumPick.writeBytes(populateQuorumPick);
        wrQuorumPick.flush();
        wrQuorumPick.close();

        int responseCodeQuorumPick = conQuorumPick.getResponseCode();
        FunctionsLogger.debug("Sending 'POST' request to URL : " + urlQuorumPick);
        FunctionsLogger.debug("Post Data : " + populateQuorumPick);
        FunctionsLogger.debug("Response Code : " + responseCodeQuorumPick);

        BufferedReader inQuorumPick = new BufferedReader(
                new InputStreamReader(conQuorumPick.getInputStream()));
        String outputQuorumPick;
        StringBuffer responseQuorumPick = new StringBuffer();
        while ((outputQuorumPick = inQuorumPick.readLine()) != null) {
            responseQuorumPick.append(outputQuorumPick);
        }
        inQuorumPick.close();
        FunctionsLogger.debug(" responsequorumpick " + responseQuorumPick.toString());
        quorumArray = new JSONArray(responseQuorumPick.toString());
        return quorumArray;
    }



    public static void mineUpdate(String didHash,int credits) throws IOException, JSONException {
        String urlMineUpdate = "http://13.76.134.226:9595/updatemine";
        URL objMineUpdate = new URL(urlMineUpdate);
        HttpURLConnection conMineUpdate = (HttpURLConnection) objMineUpdate.openConnection();

        conMineUpdate.setRequestMethod("POST");
        conMineUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conMineUpdate.setRequestProperty("Accept", "application/json");
        conMineUpdate.setRequestProperty("Content-Type", "application/json");
        conMineUpdate.setRequestProperty("Authorization", "null");

        JSONObject dataToSendMineUpdate = new JSONObject();
        dataToSendMineUpdate.put("didhash", didHash);
        dataToSendMineUpdate.put("credits",credits);
        String populateMineUpdate = dataToSendMineUpdate.toString();

        conMineUpdate.setDoOutput(true);
        DataOutputStream wrMineUpdate = new DataOutputStream(conMineUpdate.getOutputStream());
        wrMineUpdate.writeBytes(populateMineUpdate);
        wrMineUpdate.flush();
        wrMineUpdate.close();

        int responseCodeMineUpdate = conMineUpdate.getResponseCode();
        FunctionsLogger.debug("Sending 'POST' request to URL : " + urlMineUpdate);
        FunctionsLogger.debug("Post Data : " + populateMineUpdate);
        FunctionsLogger.debug("Response Code : " + responseCodeMineUpdate);

        BufferedReader inMineUpdate = new BufferedReader(
                new InputStreamReader(conMineUpdate.getInputStream()));
        String outputMineUpdate;
        StringBuffer responseMineUpdate = new StringBuffer();
        while ((outputMineUpdate = inMineUpdate.readLine()) != null) {
            responseMineUpdate.append(outputMineUpdate);
        }
        inMineUpdate.close();

    }

}

