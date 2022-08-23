package com.rubix.TokenTransfer;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import javax.imageio.ImageIO;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Ping.VerifyStakedToken;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import com.rubix.Constants.MiningConstants.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rubix.Constants.*;

import io.ipfs.api.IPFS;

public class TokenReceiver {
	public static Logger TokenReceiverLogger = Logger.getLogger(TokenReceiver.class);

	private static final JSONObject APIResponse = new JSONObject();
	private static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
	private static String SenWalletBin;

	// token limit for each level
	private static final int[] tokenLimit = { 0, 5000000, 2425000, 2303750, 2188563, 2079134, 1975178, 1876419, 1782598,
			1693468, 1608795, 1528355, 1451937, 1379340 };

	/**
	 * Receiver Node: To receive a valid token from an authentic sender
	 *
	 * @return Transaction Details (JSONObject)
	 * @throws IOException   handles IO Exceptions
	 * @throws JSONException handles JSON Exceptions
	 */
	public static String receive() {
		pathSet();
		ServerSocket ss = null;
		Socket sk = null;
		String senderPeerID = null;

		try {
			repo(ipfs);
			String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("PARTS/");
			String PART_TOKEN_PATH = TOKENS_PATH.concat("PARTS/");
			File partFolder = new File(PART_TOKEN_PATH);
			if (!partFolder.exists())
				partFolder.mkdir();
			partFolder = new File(PART_TOKEN_CHAIN_PATH);
			if (!partFolder.exists())
				partFolder.mkdir();
			File partTokensFile = new File(PAYMENTS_PATH.concat("PartsToken.json"));
			if (!partTokensFile.exists()) {
				partTokensFile.createNewFile();
				writeToFile(partTokensFile.toString(), "[]", false);
			}

			int quorumSignVerifyCount = 0;
			JSONObject quorumSignatures = null;

			ArrayList<String> quorumDID = new ArrayList<>();
			PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

			String receiverPeerID = getPeerID(DATA_PATH + "DID.json");

			String receiverDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", receiverPeerID);

			listen(receiverPeerID, RECEIVER_PORT);
			ss = new ServerSocket(RECEIVER_PORT);
			TokenReceiverLogger.debug("Receiver Listening on " + RECEIVER_PORT + " appname " + receiverPeerID);

			sk = ss.accept();
			TokenReceiverLogger.debug("Data Incoming...");
			BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
			PrintStream output = new PrintStream(sk.getOutputStream());
			long startTime = System.currentTimeMillis();

			try {
				senderPeerID = input.readLine();
			} catch (SocketException e) {
				TokenReceiverLogger.warn("Sender Stream Null - Sender Details");
				APIResponse.put("did", "");
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Sender Stream Null - Sender Details");

				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}

			TokenReceiverLogger.debug("Data Received: " + senderPeerID);
			swarmConnectP2P(senderPeerID, ipfs);

			String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
			String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", senderPeerID);

			if (!(senderDidIpfsHash.contains("Qm") && senderWidIpfsHash.contains("Qm"))) {
				output.println("420");
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Sender details not available in network , please sync");
				TokenReceiverLogger.info("Sender details not available in datatable");
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}

			nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
			File senderDIDFile = new File(DATA_PATH + senderDidIpfsHash + "/DID.png");
			if (!senderDIDFile.exists()) {
				output.println("420");
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Sender details not available");
				TokenReceiverLogger.info("Sender details not available");
				/* executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID); */
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}
			TokenReceiverLogger.debug("Sender details authenticated");
			output.println("200");

			String tokenDetails;
			try {
				tokenDetails = input.readLine();
			} catch (SocketException e) {
				TokenReceiverLogger.warn("Sender Stream Null - Token Details");
				APIResponse.put("did", "");
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Sender Stream Null - Token Details");

				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();

			}
			JSONObject tokenObject = new JSONObject(tokenDetails);
			JSONObject TokenDetails = tokenObject.getJSONObject("tokenDetails");
			JSONArray wholeTokens = TokenDetails.getJSONArray("whole-tokens");
			JSONArray wholeTokenChains = TokenDetails.getJSONArray("whole-tokenChains");

			JSONArray partTokens = TokenDetails.getJSONArray("part-tokens");
			JSONObject partTokenChains = TokenDetails.getJSONObject("part-tokenChains");
			JSONArray partTokenChainsHash = TokenDetails.getJSONArray("hashSender");

			JSONArray previousSendersArray = tokenObject.getJSONArray("previousSender");
			JSONArray positionsArray = tokenObject.getJSONArray("positions");

			Double amount = tokenObject.getDouble("amount");
			JSONObject amountLedger = tokenObject.getJSONObject("amountLedger");
			TokenReceiverLogger.debug("Amount Ledger: " + amountLedger);
			int intPart = wholeTokens.length();
			// ? multiple pin check starts
			Double decimalPart = formatAmount(amount - intPart);
			JSONArray doubleSpentToken = new JSONArray();
			boolean tokenOwners = true;
			ArrayList pinOwnersArray = new ArrayList();
			ArrayList previousSender = new ArrayList();
			JSONArray ownersReceived = new JSONArray();
			TokenReceiverLogger.debug("previousSendersArray is " + previousSendersArray.toString());
			TokenReceiverLogger.debug("tokenObject is " + tokenObject.toString());
			TokenReceiverLogger.debug("tokenDetails(base for tokenObj) is " + tokenDetails.toString());

			// previoussenderarray
			// tokenobject
			for (int i = 0; i < wholeTokens.length(); ++i) {
				try {
					TokenReceiverLogger.debug("Checking owners for " + wholeTokens.getString(i) + " Please wait...");
					pinOwnersArray = IPFSNetwork.dhtOwnerCheck(wholeTokens.getString(i));

					if (pinOwnersArray.size() > 2) {

						for (int j = 0; j < previousSendersArray.length(); j++) {
							if (previousSendersArray.getJSONObject(j).getString("token")
									.equals(wholeTokens.getString(i)))
								ownersReceived = previousSendersArray.getJSONObject(j).getJSONArray("sender");
						}

						for (int j = 0; j < ownersReceived.length(); j++) {
							previousSender.add(ownersReceived.getString(j));
						}
						TokenReceiverLogger.debug("Previous Owners: " + previousSender);

						for (int j = 0; j < pinOwnersArray.size(); j++) {
							if (!previousSender.contains(pinOwnersArray.get(j).toString()))
								tokenOwners = false;
						}
					}
				} catch (IOException e) {

					TokenReceiverLogger.debug("Ipfs dht find did not execute");
				}
			}
			if (!tokenOwners) {
				JSONArray owners = new JSONArray();
				for (int i = 0; i < pinOwnersArray.size(); i++)
					owners.put(pinOwnersArray.get(i).toString());
				TokenReceiverLogger.debug("Multiple Owners for " + doubleSpentToken);
				TokenReceiverLogger.debug("Owners: " + owners);
				output.println("420");
				output.println(doubleSpentToken.toString());
				output.println(owners.toString());
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Multiple Owners for " + doubleSpentToken + " " + owners);
				IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}
			// ? multiple pin check ends
			String senderToken = TokenDetails.toString();
			String consensusID = calculateHash(senderToken, "SHA3-256");
			writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
			String consensusIDIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "consensusID", ipfs);
			deleteFile(LOGGER_PATH + "consensusID");

			if (!IPFSNetwork.dhtEmpty(consensusIDIPFSHash, ipfs)) {
				TokenReceiverLogger.debug("consensus ID not unique" + consensusIDIPFSHash);
				output.println("421");
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Consensus ID not unique");
				IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}

			// Check IPFS get for all Tokens
			int ipfsGetFlag = 0;
			ArrayList<String> wholeTokenContent = new ArrayList<>();
			ArrayList<String> wholeTokenChainContent = new ArrayList<>();

			/** Token Authenticity Check - starts */
			HashMap<String, Integer> tokenMaxLimitMap = new HashMap<>();
			HashMap<String, Integer> tokenDetailMap = new HashMap<>();

			for (int i = 0; i < wholeTokens.length(); i++) {

				String tokenChainContent = get(wholeTokenChains.getString(i), ipfs);
				wholeTokenChainContent.add(tokenChainContent);
				String tokenContent = get(wholeTokens.getString(i), ipfs).trim();
				wholeTokenContent.add(tokenContent);
				ipfsGetFlag++;
				String tokenLevel = tokenContent.substring(0, tokenContent.length() - 64);
				String tokenNumberHash = tokenContent.substring(tokenContent.length() - 64);

				int tokenLevelInt = Integer.parseInt(tokenLevel);
				int tokenLimitForLevel = tokenLimit[tokenLevelInt];
				tokenMaxLimitMap.put(tokenNumberHash, tokenLimitForLevel);
				tokenDetailMap.put(tokenNumberHash, -1);
			}
			repo(ipfs);

			if (wholeTokens.length() > 0) {

				if (!(ipfsGetFlag == intPart)) {
					output.println("422");
					APIResponse.put("did", senderDidIpfsHash);
					APIResponse.put("tid", "null");
					APIResponse.put("status", "Failed");
					APIResponse.put("message", "Tokens not verified");
					TokenReceiverLogger.info("Tokens not verified");
					IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
					output.close();
					input.close();
					sk.close();
					ss.close();
					return APIResponse.toString();
				}

				int tokenMaxValue = Collections.max(tokenMaxLimitMap.values());
				TokenReceiverLogger.debug("Token Max Value : " + tokenMaxValue);
				tokenDetailMap = Functions.checkTokenHash(tokenDetailMap, tokenMaxValue);

				if (tokenDetailMap.isEmpty()) {
					TokenReceiverLogger.debug("Invalid Content Found in Token");
					String errorMessage = "Invalid Content Found in Token";
					output.println("426");
					APIResponse.put("did", senderDidIpfsHash);
					APIResponse.put("tid", "null");
					APIResponse.put("status", "Failed");
					APIResponse.put("message", errorMessage);
					TokenReceiverLogger.debug(errorMessage);
					executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
					output.close();
					input.close();
					sk.close();
					ss.close();
					return APIResponse.toString();
				} else {
					for (String tokenContent : tokenDetailMap.keySet()) {
						TokenReceiverLogger.debug(tokenContent);
						TokenReceiverLogger.debug(tokenDetailMap.get(tokenContent));
						TokenReceiverLogger.debug(tokenMaxLimitMap.get(tokenContent));

						if (tokenDetailMap.get(tokenContent) != null
								&& tokenDetailMap.get(tokenContent) > tokenMaxLimitMap.get(tokenContent)) {

							output.println("426");
							APIResponse.put("did", senderDidIpfsHash);
							APIResponse.put("tid", "null");
							APIResponse.put("status", "Failed");
							String errorMessage1 = "Token Number is greater than Token Limit for the Level";
							APIResponse.put("message", errorMessage1);
							TokenReceiverLogger.debug(errorMessage1);
							executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
							output.close();
							input.close();
							sk.close();
							ss.close();
							return APIResponse.toString();
						}
					}
				}

			}
			/** Token Authenticity Check - Ends */

			JSONArray partTokenChainContent = new JSONArray();
			JSONArray partTokenContent = new JSONArray();

			for (int i = 0; i < partTokenChains.length(); i++) {

				partTokenChainContent.put(partTokenChains.getJSONArray(partTokens.getString(i)));
				String TokenContent = get(partTokens.getString(i), ipfs).trim();
				partTokenContent.put(TokenContent);
			}

			boolean chainFlag = true;
			for (int i = 0; i < partTokenChainContent.length(); i++) {
				JSONArray tokenChainContent = partTokenChainContent.getJSONArray(i);
				for (int j = 0; j < tokenChainContent.length(); j++) {
					String previousHash = tokenChainContent.getJSONObject(j).getString("previousHash");
					String nextHash = tokenChainContent.getJSONObject(j).getString("nextHash");
					String rePreviousHash, reNextHash;
					if (tokenChainContent.length() > 1) {
						if (j == 0) {
							rePreviousHash = "";
							String rePrev = calculateHash(new JSONObject().toString(), "SHA3-256");
							reNextHash = calculateHash(tokenChainContent.getJSONObject(j + 1).getString("tid"),
									"SHA3-256");

							if (!((rePreviousHash.equals(previousHash) || rePrev.equals(previousHash))
									&& reNextHash.equals(nextHash))) {
								chainFlag = false;
							}

						} else if (j == tokenChainContent.length() - 1) {
							rePreviousHash = calculateHash(tokenChainContent.getJSONObject(j - 1).getString("tid"),
									"SHA3-256");
							reNextHash = "";

							if (!(rePreviousHash.equals(previousHash) && reNextHash.equals(nextHash))) {
								chainFlag = false;
							}

						} else {
							rePreviousHash = calculateHash(tokenChainContent.getJSONObject(j - 1).getString("tid"),
									"SHA3-256");
							reNextHash = calculateHash(tokenChainContent.getJSONObject(j + 1).getString("tid"),
									"SHA3-256");

							if (!(rePreviousHash.equals(previousHash) && reNextHash.equals(nextHash))) {
								chainFlag = false;
							}
						}
					}
				}
			}

			if (!chainFlag) {
				String errorMessage = "Broken Cheque Chain";
				output.println("423");
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", errorMessage);
				TokenReceiverLogger.debug(errorMessage);
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}

			boolean ownerCheck = true;

			JSONArray allTokens = new JSONArray();
			for (int i = 0; i < wholeTokens.length(); i++)
				allTokens.put(wholeTokens.getString(i));
			for (int i = 0; i < partTokens.length(); i++)
				allTokens.put(partTokens.getString(i));

			JSONArray allTokensChains = new JSONArray();
			for (int i = 0; i < wholeTokenChainContent.size(); i++)
				allTokensChains.put(wholeTokenChainContent.get(i));
			for (int i = 0; i < partTokenChainContent.length(); i++)
				allTokensChains.put(partTokenChainContent.get(i));

			TokenReceiverLogger.debug("allTokenChain is "+allTokensChains.toString());
			
			
			JSONArray invalidTokens = new JSONArray();

			for (int count = 0; count < wholeTokens.length(); count++) {
				String tokens = null;
				TokenReceiverLogger.debug("Json array tokenChain value is " + wholeTokens.get(count).toString());
				JSONArray tokenChain = new JSONArray(allTokensChains.get(count).toString());
				TokenReceiverLogger.debug("tokenchain is " + tokenChain);
				TokenReceiverLogger.debug("tokenchain size is " + tokenChain.length());

				String tokenContent = get(wholeTokens.getString(count), ipfs).trim();
				String tokenLevel = tokenContent.substring(0, tokenContent.length() - 64);
				String tokenNumberHash = tokenContent.substring(tokenContent.length() - 64);

				int tokenLevelInt = Integer.parseInt(tokenLevel);
				int tokenLimitForLevel = tokenLimit[tokenLevelInt];
				int tokenLevelValue = (int) Math.pow(2, tokenLevelInt + 2);
				int minumumStakeHeight = tokenLevelValue * 4;

				// ! check quorum signs for previous transaction for the tokenchain to verify
				// ! the ownership of sender for the token
				JSONObject lastObject = new JSONObject();
				
				if (tokenChain.length() > 0 ) {
					
					lastObject = tokenChain.getJSONObject(tokenChain.length() - 1);
					
				}
				TokenReceiverLogger.debug("Last Object = " + lastObject.toString());
				
				
				if (tokenChain.length() > 0 && lastObject.has("owner") && !lastObject.has(MiningConstants.STAKED_TOKEN) &&  
							( (tokenLevelInt == 4 && (tokenDetailMap.get(tokenNumberHash) >= 1204400)) || (tokenLevelInt >= 5)) ) {

						TokenReceiverLogger.debug("Checking ownership");
						String owner = lastObject.getString("owner");
						tokens = allTokens.getString(count);
						String hashString = tokens.concat(senderDidIpfsHash);
						String hashForPositions = calculateHash(hashString, "SHA3-256");
						String ownerIdentity = hashForPositions.concat(positionsArray.getString(count));
						String ownerRecalculated = calculateHash(ownerIdentity, "SHA3-256");

						TokenReceiverLogger.debug("Ownership Here Sender Calculation");
						TokenReceiverLogger.debug("tokens: " + tokens);
						TokenReceiverLogger.debug("hashString: " + hashString);
						TokenReceiverLogger.debug("hashForPositions: " + hashForPositions);
						TokenReceiverLogger.debug("p1: " + positionsArray.getString(count));
						TokenReceiverLogger.debug("ownerIdentity: " + ownerIdentity);
						TokenReceiverLogger.debug("ownerIdentityHash: " + ownerRecalculated);

						if (!owner.equals(ownerRecalculated)) {
							ownerCheck = false;
							invalidTokens.put(tokens);
						}

						if (ownerCheck && (tokenChain.length() < minumumStakeHeight)) {
							// && (tokenNumber > 1204400)
							JSONObject genesiObject = tokenChain.getJSONObject(0);
							JSONArray stakeDataArray = genesiObject.getJSONArray(MiningConstants.MINE_ID);

							int randomNumber = new Random().nextInt(15);
							JSONObject genesisSignaturesContent = genesiObject
									.getJSONObject(MiningConstants.QUORUM_SIGN_CONTENT);
							Iterator randomKey = genesisSignaturesContent.keys();
							for (int i = 0; i < randomNumber; i++) {
								randomKey.next();
							}
							/**
							 * String randomKeyString = randomKey.next().toString(); JSONObject
							 * verificationPick = new JSONObject(); verificationPick.put("did",
							 * randomKeyString); verificationPick.put("hash",
							 * genesiObject.getString("tid")); verificationPick.put("signature",
							 * genesisSignaturesContent.getString(randomKeyString));
							 * 
							 * if (verificationPick.getString("hash").equals(genesiObject.getString("tid")))
							 * {
							 * 
							 * if (Authenticate.verifySignature(verificationPick.toString())) {
							 * TokenReceiverLogger.debug("Staking check (3) successful"); } else {
							 * TokenReceiverLogger.debug( "Staking check (3) failed: Could not verify
							 * genesis credit signature"); ownerCheck = false; invalidTokens.put(tokens); }
							 * } else { TokenReceiverLogger.debug( "Staking check (3) failed: Genesis TID is
							 * not equal to the hash of the genesis signature"); ownerCheck = false;
							 * invalidTokens.put(tokens); }
							 */
							// else {
							// TokenReceiverLogger.debug("Staking check (3) failed: Genesis Signature not
							// found");
							// ownerCheck = false;
							// invalidTokens.put(tokens);
							// }

							if (stakeDataArray.length() == 3) {

								JSONObject oneOfThreeStake = stakeDataArray.getJSONObject(0);
								JSONObject twoOfThreeStake = stakeDataArray.getJSONObject(1);
								JSONObject threeOfThreeStake = stakeDataArray.getJSONObject(2);

								String[] stakedTokenTC = new String[3];
								String[] stakedTokenSignTC = new String[3];
								String[] stakerDIDTC = new String[3];
								String[] mineIDTC = new String[3];
								String[] mineIDSignTC = new String[3];

								stakedTokenTC[0] = oneOfThreeStake.getString(MiningConstants.STAKED_TOKEN);
								stakedTokenSignTC[0] = oneOfThreeStake.getString(MiningConstants.STAKED_TOKEN_SIGN);
								stakerDIDTC[0] = oneOfThreeStake.getString(MiningConstants.STAKED_QUORUM_DID);
								mineIDTC[0] = oneOfThreeStake.getString(MiningConstants.MINE_ID);
								mineIDSignTC[0] = oneOfThreeStake.getString(MiningConstants.MINE_ID_SIGN);

								stakedTokenTC[1] = twoOfThreeStake.getString(MiningConstants.STAKED_TOKEN);
								stakedTokenSignTC[1] = twoOfThreeStake.getString(MiningConstants.STAKED_TOKEN_SIGN);
								stakerDIDTC[1] = twoOfThreeStake.getString(MiningConstants.STAKED_QUORUM_DID);
								mineIDTC[1] = twoOfThreeStake.getString(MiningConstants.MINE_ID);
								mineIDSignTC[1] = twoOfThreeStake.getString(MiningConstants.MINE_ID_SIGN);

								stakedTokenTC[2] = threeOfThreeStake.getString(MiningConstants.STAKED_TOKEN);
								stakedTokenSignTC[2] = threeOfThreeStake.getString(MiningConstants.STAKED_TOKEN_SIGN);
								stakerDIDTC[2] = threeOfThreeStake.getString(MiningConstants.STAKED_QUORUM_DID);
								mineIDTC[2] = threeOfThreeStake.getString(MiningConstants.MINE_ID);
								mineIDSignTC[2] = threeOfThreeStake.getString(MiningConstants.MINE_ID_SIGN);
								TokenReceiverLogger.debug("mineIDTC length is "+mineIDTC.length);

								for (int stakeCount = 0; stakeCount < mineIDTC.length; stakeCount++) {

									String mineIDContent = get(mineIDTC[stakeCount], ipfs);
									JSONObject mineIDContentJSON = new JSONObject(mineIDContent);
									TokenReceiverLogger.debug(mineIDContentJSON.toString());

									JSONObject stakeData = mineIDContentJSON.getJSONObject(MiningConstants.STAKE_DATA);

									String stakerDIDMineData = stakeData.getString(MiningConstants.STAKED_QUORUM_DID);
									String stakedTokenMineData = stakeData.getString(MiningConstants.STAKED_TOKEN);
									String stakedTokenSignMineData = stakeData
											.getString(MiningConstants.STAKED_TOKEN_SIGN);

									TokenReceiverLogger.debug(stakerDIDTC[stakeCount]);
									TokenReceiverLogger.debug(stakedTokenTC[stakeCount]);
									TokenReceiverLogger.debug(stakedTokenSignTC[stakeCount]);

									TokenReceiverLogger.debug(stakerDIDMineData);
									TokenReceiverLogger.debug(stakedTokenMineData);
									TokenReceiverLogger.debug(stakedTokenSignMineData);

									if (stakerDIDTC[stakeCount].equals(stakerDIDMineData)
											&& stakedTokenTC[stakeCount].equals(stakedTokenMineData)
											&& stakedTokenSignTC[stakeCount].equals(stakedTokenSignMineData)) {
										
										TokenReceiverLogger.debug("array n non array data are same");

										JSONObject detailsToVerify = new JSONObject();
										detailsToVerify.put("did", stakerDIDTC[stakeCount]);
										detailsToVerify.put("hash", mineIDTC[stakeCount]);
										detailsToVerify.put("signature", mineIDSignTC[stakeCount]);
										TokenReceiverLogger.debug("detailsToVerify - "+detailsToVerify.toString());
										
										if (Authenticate.verifySignature(detailsToVerify.toString())) {

											boolean minedTokenStatus = true;
											ArrayList<String> ownersArray = IPFSNetwork
													.dhtOwnerCheck(stakedTokenTC[stakeCount]);
											TokenReceiverLogger.debug("dht owner are "+ownersArray.toString()+" size is "+ownersArray.size());
											for (int i = 0; i < ownersArray.size(); i++) {
												if (ownersArray.get(i).equals(stakerDIDTC[stakeCount])) {
													minedTokenStatus = false;
												}
											}
											if (!minedTokenStatus) {
												TokenReceiverLogger.debug("Staked token is not found with staker DID: "
														+ stakerDIDTC[stakeCount]);
												ownerCheck = false;
												invalidTokens.put(tokens);
											}

										} else {
											TokenReceiverLogger.debug(
													"Staking check (2) failed - unable to verify mine ID signature by staker: "
															+ stakerDIDTC[stakeCount]);
											ownerCheck = false;
											invalidTokens.put(tokens);
										}

										TokenReceiverLogger.debug("MineID Verification Successful with Staking node: "
												+ stakerDIDTC[stakeCount]);
									} else {
										TokenReceiverLogger.debug("Staking check (2) failed");
										ownerCheck = false;
										invalidTokens.put(tokens);
									}

									TokenReceiverLogger.debug("Staking check (2) successful for count "+stakeCount);
									// } else {
									// TokenReceiverLogger.debug(
									// "Staking check (2) failed: Could not verify mine ID signature");
									// ownerCheck = false;
									// invalidTokens.put(tokens);
									// }
								}

							} else {
								ownerCheck = false;
								TokenReceiverLogger.debug("Staked Token is not available!");

							}

						}
					}
					else if (tokenChain.length() > 0 && lastObject.has(MiningConstants.STAKED_TOKEN)) {

						Boolean minedTokenStatus = true;

						String mineID = lastObject.getString(MiningConstants.MINE_ID);

						String mineIDContent = get(mineID, ipfs);
						JSONObject mineIDContentJSON = new JSONObject(mineIDContent);

						JSONObject stakeData = mineIDContentJSON.getJSONObject(MiningConstants.STAKE_DATA);

						ArrayList<String> ownersArray = IPFSNetwork
								.dhtOwnerCheck(stakeData.getString(MiningConstants.STAKED_TOKEN));
						for (int i = 0; i < ownersArray.size(); i++) {
							if (!VerifyStakedToken.Contact(ownersArray.get(i), SEND_PORT + 16,
									stakeData.getString(MiningConstants.STAKED_TOKEN),
									mineIDContentJSON.getString("tokenContent"))) {
								minedTokenStatus = false;
							}
						}
						if (!minedTokenStatus) {
							TokenReceiverLogger.debug("Staking check failed: Found staked token but token height < 46");
							ownerCheck = false;
							invalidTokens.put(tokens);
						}

						TokenReceiverLogger.debug(
								"Staking check failed: Found staked token but unable to transfer while mined token height is not satisfied for the network");
						ownerCheck = false;
						invalidTokens.put(tokens);

						// JSONObject tokenToVerify = new JSONObject();
						// if (mineIDContentJSON.has(MiningConstants.STAKE_DATA)) {

						// JSONObject stakeData =
						// mineIDContentJSON.getJSONObject(MiningConstants.STAKE_DATA);
						// String stakerDID = stakeData.getString(STAKED_QUORUM_DID);
						// String stakedToken = stakeData.getString(STAKED_TOKEN);
						// String stakedTokenSign = stakeData.getString(STAKED_TOKEN_SIGN);

						// tokenToVerify.put("did", senderDidIpfsHash);
						// tokenToVerify.put("hash", stakedToken);
						// tokenToVerify.put("signature", stakedTokenSign);

						// if (Authenticate.verifySignature(tokenToVerify.toString())) {

						// ArrayList<String> ownersArray = IPFSNetwork.dhtOwnerCheck(stakedToken);
						// for (int i = 0; i < ownersArray.size(); i++) {
						// if (!VerifyStakedToken.Contact(ownersArray.get(i), SEND_PORT + 16,
						// mineIDContentJSON.getString("tokenContent"))) {
						// minedTokenStatus = false;
						// }
						// }
						// if (!minedTokenStatus) {
						// TokenReceiverLogger
						// .debug("Staking check failed: Found staked token but token height < 46");
						// ownerCheck = false;
						// invalidTokens.put(tokens);
						// }

						// TokenReceiverLogger.debug(
						// "Staking check failed: Found staked token but unable to transfer while mined
						// token height is not satisfied for the network");
						// ownerCheck = false;
						// invalidTokens.put(tokens);

						// } else {
						// TokenReceiverLogger.debug(
						// "Staking check failed: Found staked token but unable to verify staked token
						// height");
						// ownerCheck = false;
						// invalidTokens.put(tokens);
						// }
						// }

					}
				
			}

			if (!ownerCheck) {
				TokenReceiverLogger.debug("Ownership Check Failed");
				String errorMessage = "Ownership Check Failed";
				output.println("424");
				output.println(invalidTokens.toString());
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", errorMessage);
				TokenReceiverLogger.debug(errorMessage);
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			} else
				TokenReceiverLogger.debug("Ownership Check Passed");

			// -------------------------------------------------------

			boolean partsAvailable = true;
			for (int i = 0; i < partTokenChainContent.length(); i++) {
				Double senderCount = 0.000D, receiverCount = 0.000D;
				JSONArray tokenChainContent = partTokenChainContent.getJSONArray(i);
				for (int k = 0; k < tokenChainContent.length(); k++) {
					if (tokenChainContent.getJSONObject(k).has("role")) {
						if (tokenChainContent.getJSONObject(k).getString("role").equals("Sender")
								&& tokenChainContent.getJSONObject(k).getString("sender").equals(senderDidIpfsHash)) {
							senderCount += tokenChainContent.getJSONObject(k).getDouble("amount");
						} else if (tokenChainContent.getJSONObject(k).getString("role").equals("Receiver")
								&& tokenChainContent.getJSONObject(k).getString("receiver").equals(senderDidIpfsHash)) {
							receiverCount += tokenChainContent.getJSONObject(k).getDouble("amount");
						}
					}
				}
				FunctionsLogger.debug("Sender Parts: " + formatAmount(senderCount));
				FunctionsLogger.debug("Receiver Parts: " + formatAmount(receiverCount));
				Double availableParts = receiverCount - senderCount;

				availableParts = formatAmount(availableParts);
				availableParts += amountLedger.getDouble(partTokens.getString(i));
				availableParts = formatAmount(availableParts);

				if (availableParts > 1.000D) {
					TokenReceiverLogger.debug("Token wholly spent: " + partTokens.getString(i));
					TokenReceiverLogger.debug("Parts: " + availableParts);
					partsAvailable = false;
				}
			}
			if (!partsAvailable) {
				String errorMessage = "Token wholly spent already";
				output.println("425");
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", errorMessage);
				TokenReceiverLogger.debug(errorMessage);
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();
			}
			output.println("200");

			String senderDetails;
			try {
				senderDetails = input.readLine();
			} catch (SocketException e) {
				TokenReceiverLogger.warn("Sender Stream Null - Sender Details");
				APIResponse.put("did", "");
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Sender Stream Null - Sender Details");

				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();

			}
			JSONObject SenderDetails = new JSONObject(senderDetails);
			String senderSignature = SenderDetails.getString("sign");
			String tid = SenderDetails.getString("tid");
			String comment = SenderDetails.getString("comment");
			String Status = SenderDetails.getString("status");
			String QuorumDetails = SenderDetails.getString("quorumsign");

			BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + senderDidIpfsHash + "/PublicShare.png"));
			SenWalletBin = PropImage.img2bin(senderWidImage);

			TokenReceiverLogger.debug("Verifying Quorum ...  ");
			TokenReceiverLogger.debug("Please wait, this might take a few seconds");

			if (!Status.equals("Consensus Failed")) {
				boolean yesQuorum = false;
				if (Status.equals("Consensus Reached")) {
					quorumSignatures = new JSONObject(QuorumDetails);
					String selectQuorumHash = calculateHash(senderToken, "SHA3-256");
					String verifyQuorumHash = calculateHash(selectQuorumHash.concat(receiverDidIpfsHash), "SHA3-256");

					Iterator<String> keys = quorumSignatures.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						quorumDID.add(key);
					}

					for (String quorumDidIpfsHash : quorumDID) {
						syncDataTable(quorumDidIpfsHash, null);
						String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
								quorumDidIpfsHash);

						nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
					}

					for (int i = 0; i < quorumSignatures.length(); i++) {

						JSONObject detailsForVerify = new JSONObject();
						detailsForVerify.put("did", quorumDID.get(i));
						detailsForVerify.put("hash", verifyQuorumHash);
						detailsForVerify.put("signature", quorumSignatures.getString(quorumDID.get(i)));
						boolean val = Authenticate.verifySignature(detailsForVerify.toString());
						if (val)
							quorumSignVerifyCount++;
					}
					TokenReceiverLogger.debug("Verified Quorum Count " + quorumSignVerifyCount);
					yesQuorum = quorumSignVerifyCount >= quorumSignatures.length();
				}
				JSONArray wholeTokenChainHash = new JSONArray();
				for (int i = 0; i < intPart; i++)
					wholeTokenChainHash.put(wholeTokenChains.getString(i));

				String hash = calculateHash(
						wholeTokens.toString() + wholeTokenChainHash.toString() + partTokens.toString()
								+ partTokenChainsHash.toString() + receiverDidIpfsHash + senderDidIpfsHash + comment,
						"SHA3-256");
				TokenReceiverLogger.debug("Hash to verify Sender: " + hash);
				JSONObject detailsForVerify = new JSONObject();
				detailsForVerify.put("did", senderDidIpfsHash);
				detailsForVerify.put("hash", hash);
				detailsForVerify.put("signature", senderSignature);

				boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
				TokenReceiverLogger.debug("Quorum Auth : " + yesQuorum + " Sender Auth : " + yesSender);
				if (!(yesSender && yesQuorum)) {
					output.println("420");
					APIResponse.put("did", senderDidIpfsHash);
					APIResponse.put("tid", tid);
					APIResponse.put("status", "Failed");
					APIResponse.put("message", "Sender / Quorum not verified");
					TokenReceiverLogger.info("Sender / Quorum not verified");
					IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
					output.close();
					input.close();
					sk.close();
					ss.close();
					return APIResponse.toString();
				}

				repo(ipfs);
				TokenReceiverLogger.debug("Sender and Quorum Verified");
				output.println("200");

				String pinDetails;
				try {
					pinDetails = input.readLine();
				} catch (SocketException e) {
					TokenReceiverLogger.warn("Sender Stream Null - Pinning Status");
					APIResponse.put("did", "");
					APIResponse.put("tid", "null");
					APIResponse.put("status", "Failed");
					APIResponse.put("message", "Sender Stream Null - Pinning Status");

					output.close();
					input.close();
					sk.close();
					ss.close();
					return APIResponse.toString();

				}
				if (pinDetails.equals("Unpinned")) {
					int pinCount = 0;
					for (int i = 0; i < intPart; i++) {

						Path tokensPath = Paths.get(TOKENS_PATH + wholeTokens.get(i));
						Files.write(tokensPath, wholeTokenContent.get(i).getBytes());
						add(TOKENS_PATH + wholeTokens.get(i), ipfs);
						pin(wholeTokens.get(i).toString(), ipfs);
						pinCount++;

					}

					for (int i = 0; i < partTokens.length(); i++) {
						File tokenFile = new File(PART_TOKEN_PATH + partTokens.getString(i));
						if (!tokenFile.exists())
							tokenFile.createNewFile();

						writeToFile(PART_TOKEN_PATH + partTokens.getString(i), partTokenContent.getString(i), false);
						String tokenHash = add(PART_TOKEN_PATH + partTokens.getString(i), ipfs);
						pin(tokenHash, ipfs);

					}

					if (pinCount == intPart) {
						TokenReceiverLogger.debug("Pinned All Tokens");
						output.println("Successfully Pinned");

						String essentialShare;
						try {
							essentialShare = input.readLine();
						} catch (SocketException e) {
							TokenReceiverLogger.warn("Sender Stream Null - EShare Details");
							APIResponse.put("did", "");
							APIResponse.put("tid", "null");
							APIResponse.put("status", "Failed");
							APIResponse.put("message", "Sender Stream Null - EShare Details");

							output.close();
							input.close();
							sk.close();
							ss.close();
							return APIResponse.toString();

						}
						long endTime = System.currentTimeMillis();
						for (int i = 0; i < intPart; i++) {
							String wholeToken = wholeTokens.getString(i);
							String hashString = wholeToken.concat(receiverDidIpfsHash);
							String hashForPositions = calculateHash(hashString, "SHA3-256");

							BufferedImage pvt = ImageIO
									.read(new File(DATA_PATH.concat(receiverDidIpfsHash).concat("/PrivateShare.png")));
							String firstPrivate = PropImage.img2bin(pvt);
							int[] privateIntegerArray1 = strToIntArray(firstPrivate);
							String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
							String positions = "";
							for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
								positions += privateBinary.charAt(j);
							}
							String ownerIdentity = hashForPositions.concat(positions);
							String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");

							TokenReceiverLogger.debug("Ownership Here");
							TokenReceiverLogger.debug("tokens: " + wholeTokens.getString(i));
							TokenReceiverLogger.debug("hashString: " + hashString);
							TokenReceiverLogger.debug("hashForPositions: " + hashForPositions);
							TokenReceiverLogger.debug("p1: " + positions);
							TokenReceiverLogger.debug("ownerIdentity: " + ownerIdentity);
							TokenReceiverLogger.debug("ownerIdentityHash: " + ownerIdentityHash);

							ArrayList<String> groupTokens = new ArrayList<>();
							for (int k = 0; k < intPart; k++) {
								if (!wholeTokens.getString(i).equals(wholeTokens.getString(k)))
									groupTokens.add(wholeTokens.getString(k));
							}

							JSONArray arrToken = new JSONArray();
							JSONObject objectToken = new JSONObject();
							objectToken.put("tokenHash", wholeTokens.getString(i));
							arrToken.put(objectToken);
							JSONArray arr1 = new JSONArray(wholeTokenChainContent.get(i));
							JSONObject obj2 = new JSONObject();
							obj2.put("senderSign", senderSignature);
							obj2.put("sender", senderDidIpfsHash);
							obj2.put("group", groupTokens);
							obj2.put("comment", comment);
							obj2.put("tid", tid);
							obj2.put("owner", ownerIdentityHash);
							arr1.put(obj2);

							writeToFile(TOKENCHAIN_PATH + wholeTokens.getString(i) + ".json", arr1.toString(), false);

						}

						for (int i = 0; i < partTokens.length(); i++) {
							JSONObject chequeObject = new JSONObject();
							chequeObject.put("sender", senderDidIpfsHash);
							chequeObject.put("receiver", receiverDidIpfsHash);
							chequeObject.put("parent-token", partTokens.getString(i));
							chequeObject.put("parent-chain", partTokenChains.getJSONArray(partTokens.getString(i)));
							Double partAmount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));
							chequeObject.put("amount", partAmount);
							chequeObject.put("tid", tid);

							writeToFile(LOGGER_PATH.concat(partTokens.getString(i)), chequeObject.toString(), false);
							String chequeHash = IPFSNetwork.add(LOGGER_PATH.concat(partTokens.getString(i)), ipfs);
							deleteFile(LOGGER_PATH.concat(partTokens.getString(i)));

							String partsTokens = partTokens.getString(i);
							String hashString = partsTokens.concat(receiverDidIpfsHash);
							String hashForPositions = calculateHash(hashString, "SHA3-256");
							BufferedImage pvt = ImageIO
									.read(new File(DATA_PATH.concat(receiverDidIpfsHash).concat("/PrivateShare.png")));
							String firstPrivate = PropImage.img2bin(pvt);
							int[] privateIntegerArray1 = strToIntArray(firstPrivate);
							String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
							String positions = "";
							for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
								positions += privateBinary.charAt(j);
							}

							String ownerIdentity = hashForPositions.concat(positions);
							String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");

							TokenReceiverLogger.debug("Ownership Here");
							TokenReceiverLogger.debug("tokens: " + partTokens.getString(i));
							TokenReceiverLogger.debug("hashString: " + hashString);
							TokenReceiverLogger.debug("hashForPositions: " + hashForPositions);
							TokenReceiverLogger.debug("p1: " + positions);
							TokenReceiverLogger.debug("ownerIdentity: " + ownerIdentity);
							TokenReceiverLogger.debug("ownerIdentityHash: " + ownerIdentityHash);

							JSONObject newPartObject = new JSONObject();
							newPartObject.put("senderSign", senderSignature);
							newPartObject.put("sender", senderDidIpfsHash);
							newPartObject.put("receiver", receiverDidIpfsHash);
							newPartObject.put("comment", comment);
							newPartObject.put("tid", tid);
							newPartObject.put("nextHash", "");
							newPartObject.put("owner", ownerIdentityHash);
							if (partTokenChainContent.getJSONArray(i).length() == 0)
								newPartObject.put("previousHash", "");
							else
								newPartObject.put("previousHash",
										calculateHash(partTokenChainContent.getJSONArray(i)
												.getJSONObject(partTokenChainContent.getJSONArray(i).length() - 1)
												.getString("tid"), "SHA3-256"));

							newPartObject.put("amount", partAmount);
							newPartObject.put("cheque", chequeHash);
							newPartObject.put("role", "Receiver");

							File chainFile = new File(
									PART_TOKEN_CHAIN_PATH.concat(partTokens.getString(i)).concat(".json"));
							if (chainFile.exists()) {

								String readChain = readFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json");
								JSONArray readChainArray = new JSONArray(readChain);
								readChainArray.put(partTokenChainContent.getJSONArray(i)
										.getJSONObject(partTokenChainContent.getJSONArray(i).length() - 1));
								readChainArray.put(newPartObject);

								writeToFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json",
										readChainArray.toString(), false);

							} else {
								partTokenChainContent.getJSONArray(i).put(newPartObject);
								writeToFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json",
										partTokenChainContent.getJSONArray(i).toString(), false);
							}
						}

						JSONObject transactionRecord = new JSONObject();
						transactionRecord.put("role", "Receiver");
						transactionRecord.put("tokens", allTokens);
						transactionRecord.put("txn", tid);
						transactionRecord.put("quorumList", quorumSignatures.keys());
						transactionRecord.put("senderDID", senderDidIpfsHash);
						transactionRecord.put("receiverDID", receiverDidIpfsHash);
						transactionRecord.put("Date", getCurrentUtcTime());
						transactionRecord.put("totalTime", (endTime - startTime));
						transactionRecord.put("comment", comment);
						transactionRecord.put("essentialShare", essentialShare);
						amount = formatAmount(amount);
						transactionRecord.put("amount-received", amount);

						JSONArray transactionHistoryEntry = new JSONArray();
						transactionHistoryEntry.put(transactionRecord);
						updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json",
								transactionHistoryEntry.toString());

						for (int i = 0; i < wholeTokens.length(); i++) {
							String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
							JSONArray bankArray = new JSONArray(bankFile);
							JSONObject tokenObject1 = new JSONObject();
							tokenObject1.put("tokenHash", wholeTokens.getString(i));
							bankArray.put(tokenObject1);
							Functions.writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankArray.toString(), false);
							Path tokensPath = Paths.get(TOKENS_PATH + wholeTokens.get(i));
							Files.write(tokensPath, wholeTokenContent.get(i).getBytes());
						}

						String partsFile = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
						JSONArray partsReadArray = new JSONArray(partsFile);

						for (int i = 0; i < partTokens.length(); i++) {
							boolean writeParts = true;
							for (int j = 0; j < partsReadArray.length(); j++) {
								if (partsReadArray.getJSONObject(j).getString("tokenHash")
										.equals(partTokens.getString(i)))
									writeParts = false;
							}
							if (writeParts) {
								JSONObject partObject = new JSONObject();
								partObject.put("tokenHash", partTokens.getString(i));
								partsReadArray.put(partObject);
							}
						}
						writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partsReadArray.toString(), false);

						TokenReceiverLogger.info("Transaction ID: " + tid + "Transaction Successful");
						output.println("Send Response");
						APIResponse.put("did", senderDidIpfsHash);
						APIResponse.put("tid", tid);
						APIResponse.put("status", "Success");
						APIResponse.put("tokens", wholeTokens);
						APIResponse.put("comment", comment);
						APIResponse.put("message", "Transaction Successful");
						TokenReceiverLogger.info(" Transaction Successful");
						executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
						output.close();
						input.close();
						sk.close();
						ss.close();
						return APIResponse.toString();

					}
					output.println("count mistmatch");
					APIResponse.put("did", senderDidIpfsHash);
					APIResponse.put("tid", "null");
					APIResponse.put("status", "Failed");
					APIResponse.put("message", "count mismacth");
					TokenReceiverLogger.info(" Transaction failed");
					executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
					output.close();
					input.close();
					sk.close();
					ss.close();
					return APIResponse.toString();

				}

				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Failed to unpin");
				TokenReceiverLogger.info(" Transaction failed");
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				output.close();
				input.close();
				sk.close();
				ss.close();
				return APIResponse.toString();

			}
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Consensus failed at Sender side");
			TokenReceiverLogger.info(" Transaction failed");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
			output.close();
			input.close();
			sk.close();
			ss.close();
			return APIResponse.toString();

		} catch (Exception e) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
			TokenReceiverLogger.error("Exception Occurred", e);
			return APIResponse.toString();
		} finally {
			try {
				ss.close();
				sk.close();
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
			} catch (Exception e) {
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
				TokenReceiverLogger.error("Exception Occurred", e);
			}

		}
	}
}