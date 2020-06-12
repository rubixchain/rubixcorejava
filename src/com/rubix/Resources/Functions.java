package com.rubix.Resources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import static com.rubix.Resources.IPFSNetwork.forward;


public class Functions {

    public static String DATA_PATH = "", SHARES_PATH = "";

    /**
     * This method sets the required paths used in the functions
     */

    //change path set with username

    public static void pathSet(String username) throws IOException, JSONException, InterruptedException {
        String OSName = getOsName();
        System.out.println("OSNAME: " + OSName);
        BufferedReader configFR = null;
        if (OSName.contains("Windows"))
            configFR = new BufferedReader(new FileReader(new File("C:\\Rubix"+username+"\\config.json")));
        else if (OSName.contains("Mac"))
            configFR = new BufferedReader(new FileReader(new File("/Applications/Rubix"+username+"/config.json")));
        else if (OSName.contains("Linux"))
            configFR = new BufferedReader(new FileReader(new File("/home/" + getSystemUser() + "/Rubix"+username+"/config.json/")));
        else
            System.out.println("[Functions] Error : Cannot Set Paths, OS Not Supported");

        String configFileContent = "";
        if (configFR != null)
            configFileContent = configFR.readLine();
        else
            System.out.println("Empty File");

        JSONArray pathsArray = new JSONArray(configFileContent);
        JSONObject pathsObject = pathsArray.getJSONObject(0);

        DATA_PATH = pathsObject.getString("DATA_PATH");
        SHARES_PATH = pathsObject.getString("SHARES_PATH");
    }

    /**
     * This method gets the currently logged in username
     * @return lineID
     * @throws InterruptedException Handles Interrupted Exceptions
     * @throws IOException Handles IO Exceptions
     */
    public static String getSystemUser() throws InterruptedException, IOException {
        Process processID = Runtime.getRuntime().exec("whoami");
        InputStreamReader inputStreamReader = new InputStreamReader(processID.getInputStream());
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String lineID = bufferedReader.readLine();
        processID.waitFor();
        inputStreamReader.close();
        bufferedReader.close();
        return lineID;
    }

    /**
     * This method calculates different types of hashes as mentioned in the passed parameters for the mentioned message
     * @param message Input string to be hashed
     * @param algorithm Specification of the algorithm used for hashing
     * @return (String) hash
     * @throws NoSuchAlgorithmException Handles NoSuchAlgorithm Exceptions
     */
    public static String calculateHash(String message, String algorithm) throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] c = new byte[messageBytes.length];
        System.arraycopy(messageBytes, 0, c, 0, messageBytes.length);
        final byte[] hashBytes = digest.digest(messageBytes);
        return bytesToHex(hashBytes);

    }

    /**
     * This method Converts the string passed to it into an integer array
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
     * @param filePath Location of the file to be read
     * @return File Content as string
     * @throws IOException Handles IO Exceptions
     */
    public static String readFile(String filePath) throws IOException {

        FileReader fileReader = new FileReader(filePath);
        int i;
        StringBuilder fileContent = new StringBuilder();
        while ((i = fileReader.read()) != -1)
            fileContent.append((char) i);
        fileReader.close();
        return fileContent.toString();
    }

    /**
     *This method writes the mentioned data into the file passed to it
     * This also allows to take a decision on whether or not to append the data to the already existing content in the file
     * @param filePath Location of the file to be read and written into
     * @param data Data to be added
     * @param appendStatus Decides whether or not to append the new data into the already existing data
     * @throws IOException Handles IO Exceptions
     */
    public static void writeToFile(String filePath, String data, Boolean appendStatus) throws IOException {

        File writeFile = new File(filePath);
        FileWriter fw;

        fw = new FileWriter(writeFile, appendStatus);

        fw.write(data);
        fw.close();

    }


    /**
     * This method allows transfer of a single message to a selected recipient and receive a response
     * It uses Libp2p stack to establish a connection between two nodes
     * Then a socket is bound to the port IPFS is listening on
     * @param message Message to be sent
     * @param receiverPeerID Identity of the Receiver
     * @param appNameExt Extention to the application name that the receiver is already listening on
     * @param port Port number for the nodes to get connected
     * @return Reply message from the receiver
     * @throws IOException Handles IO Exceptions
     * @throws InterruptedException Handles Interrupted Exceptions
     */
    public static String singleDataTransfer(JSONObject message, String receiverPeerID, String appNameExt, int port,String  username) throws IOException, InterruptedException {
        String applicationName = receiverPeerID.concat(appNameExt);
        forward(applicationName, port, receiverPeerID,username);

        Socket socket = new Socket("127.0.0.1", port);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintStream out = new PrintStream(socket.getOutputStream());
        out.println(message);
        String receiverProof;
        while ((receiverProof = in.readLine()) == null) {
        }
        in.close();
        out.close();
        socket.close();

        return receiverProof;
    }

    /**
     * This method helps to sign a data with the selectively disclosed private share
     * @param filePath Location of the Private share
     * @param hash Data to be signed on
     * @return Signature for the data
     * @throws IOException Handles IO Exceptions
     * @throws JSONException Handles JSON Exceptions
     */
    public static String getSignFromShares(String filePath, String hash) throws IOException, JSONException {
        int[] randomPos = randomPositions(hash, 32);
        int[] finalPositions = finalPositions(randomPos, 32);

        String fileContent = Functions.readFile(filePath);
        JSONArray jsonArrayQuorum = new JSONArray(fileContent);

        String firstPrivate = jsonArrayQuorum.getJSONObject(0).getString("val");
        String secondPrivate = jsonArrayQuorum.getJSONObject(1).getString("val");
        String thirdPrivate = jsonArrayQuorum.getJSONObject(2).getString("val");

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        int[] privateIntegerArray2 = strToIntArray(secondPrivate);
        int[] privateIntegerArray3 = strToIntArray(thirdPrivate);

        int[] partOneSign = getPrivatePosition(finalPositions, privateIntegerArray1);
        int[] partTwoSign = getPrivatePosition(finalPositions, privateIntegerArray2);
        int[] partThreeSign = getPrivatePosition(finalPositions, privateIntegerArray3);

        String signOneString = intArrayToStr(partOneSign);
        String signTwoString = intArrayToStr(partTwoSign);
        String signThreeString = intArrayToStr(partThreeSign);

        return signOneString + signTwoString + signThreeString;
    }

    /**
     * This method updates the mentioned JSON file
     * It can add a new data or remove an existing data
     * @param operation Decides whether to add or remove data
     * @param filePath Locatio nof the JSON file to be updated
     * @param data Data to be added or removed
     * @throws IOException Handles IO Exceptions
     * @throws JSONException Handles JSON Exceptions
     */
    public static void updateJSON(String operation, String filePath, String data) throws IOException, JSONException {

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
                    writeToFile(filePath, contentArray.toString(), false);
                }
            }

            if (operation.equals("add")) {
                JSONArray newData = new JSONArray(data);
                for (int i = 0; i < newData.length(); i++)
                    contentArray.put(newData.getJSONObject(i));
                writeToFile(filePath, contentArray.toString(), false);
            }
    }

    /**
     * This method gets you a required data from a JSON file with a tag to be compared with
     * @param filePath Location of the JSON file
     * @param get Data to be fetched from the file
     * @param tagName Name of the tag to be compared with
     * @param value Value of the tag to be compared with
     * @return Data that is fetched from the JSON file
     */
    public static String getValues(String filePath, String get, String tagName, String value) {

        String resultString = "";
        JSONParser jsonParser = new JSONParser();

        try (FileReader reader = new FileReader(filePath)) {
            Object obj = jsonParser.parse(reader);
            org.json.simple.JSONArray List = (org.json.simple.JSONArray) obj;
            for (Object o : List) {
                org.json.simple.JSONObject js = (org.json.simple.JSONObject) o;

                String itemCompare = js.get(tagName).toString();
                if (value.equals(itemCompare)) {
                    resultString = js.get(get).toString();
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return resultString;
    }

    /**
     * This method gets the Operating System that is being run the your computer
     * @return Name of the running Operating System
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * This method identifies the Peer ID of the system by IPFS during installation
     * @param filePath Location of the file in which your IPFS Peer ID is stored
     * @return Your system's Peer ID assigned by IPFS
     * @throws IOException Handles IO Exception
     * @throws JSONException Handles JSON Exceptions
     */
    public static String getPeerID(String filePath) throws IOException, JSONException {
        String fileContent = Functions.readFile(filePath);
        JSONArray fileContentArray = new JSONArray(fileContent);
        JSONObject fileContentArrayJSONObject = fileContentArray.getJSONObject(0);
        return fileContentArrayJSONObject.getString("peer-id");
    }


    /**
     * This method gets the desired bits from your private shares
     * @param positions Desired positions numbers
     * @param privateArray Private shares as Integer Array
     * @return Final signature
     */
    public static int[] getPrivatePosition(int[] positions, int[] privateArray) {
        int[] PrivatePosition = new int[2048];
        for (int k = 0; k < 2048; k++) {
            int a = positions[k];
            int b = privateArray[a];
            PrivatePosition[k] = b;
        }
        return PrivatePosition;
    }

    /**
     * This function picks desired number of random positions bt applying a well dispersed formula of the data passed
     * @param hash Data from which positions are taken
     * @param numberOfPositions Desired number of positions
     * @return Set of well dispersed random positions
     */
    public static int[] randomPositions(String hash, int numberOfPositions) {
        int[] hashCharacters = new int[256];
        int[] randomPositions = new int[32];
        int[] finalPositions = new int[256];
        for (int k = 0; k < numberOfPositions; k++) {
            hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
            randomPositions[k] = (((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % 2048;

            finalPositions[k] = (randomPositions[k] / 64) * 64;

        }
        return finalPositions;
    }


    /**
     * This functions extends the random positions into 64 times longer
     * @param randomPositions Array of random positions
     * @param positionsCount Number of positions required
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
     * @param fileName Location of the file to be deleted
     * @throws IOException Handles IO Exception
     */
    public static void deleteFile(String fileName) throws IOException {

        Files.deleteIfExists(Paths.get(fileName));
        System.out.println("[Functions] File Deletion successful");
    }


    /**
     * This functions picks the required number of quorum members from the mentioned file
     * @param filePath Location of the file
     * @param hash Data from which positions are chosen
     * @return List of chosen members from the file
     * @throws JSONException Handles JSON Exceptions
     * @throws IOException Handles IO Exceptions
     */
    public static ArrayList<String> quorumChooser(String filePath, String hash) throws JSONException, IOException {

        String fileContent = readFile(filePath);
        JSONArray blockHeight = new JSONArray(fileContent);
        ArrayList<String> quorumList = new ArrayList();

        int[] hashCharacters = new int[256];
        var randomPositions = new ArrayList<Integer>();
        HashSet<Integer> positionSet = new HashSet<>();
        for (int k = 0; positionSet.size() != 7; k++) {
            hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
            randomPositions.add((((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % blockHeight.length());
            positionSet.add(randomPositions.get(k));
        }

        for (Integer integer : positionSet)
            quorumList.add(blockHeight.getJSONObject( integer).getString("peer-id"));

        return quorumList;
    }
}

