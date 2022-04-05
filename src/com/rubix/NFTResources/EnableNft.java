package com.rubix.NFTResources;

import static com.rubix.Resources.Functions.*;

import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;

public class EnableNft {

    public static String NFT_TOKENS_PATH="";
    public static String NFT_TOKENCHAIN_PATH="",NFT_SALE_CONTRACT_PATH="";
    public static String NFT_EXPLORER_IP="";
    public static int SELLER_PORT, BUYER_PORT; 


    public static void nftPathSet()
    {
        setDir();
        NFT_TOKENS_PATH=dirPath + File.separator + "Wallet" + File.separator+"NFTTOKENS"+File.separator;
        NFT_TOKENCHAIN_PATH=dirPath + File.separator + "Wallet" + File.separator+"NFTTOKENCHAINS"+File.separator;
        NFT_SALE_CONTRACT_PATH=dirPath + File.separator + "Wallet" + File.separator+"NFTTOKENS"+File.separator+"NFTSALECONTRACT"+File.separator;
        BUYER_PORT=15013;
        SELLER_PORT=15012;

        NFT_EXPLORER_IP="https://deamon-nft-explorer.azurewebsites.net";

    }

    public static void enableNft()
    {
        
        nftPathSet();
        pathSet();
        JSONArray initiate = new JSONArray();
        File nft_tokens_folder= new File(NFT_TOKENS_PATH);
        File nft_tokencain_folder= new File(NFT_TOKENCHAIN_PATH);
        File nftTransactionHistoryFile = new File(WALLET_DATA_PATH + "nftTransactionHistory.json");
        File nftSaleContractFolder = new File(NFT_SALE_CONTRACT_PATH);
        

        if(!nft_tokens_folder.exists())
        {
            nft_tokens_folder.mkdirs();
        }
        if(!nft_tokencain_folder.exists())
        {
            nft_tokencain_folder.mkdirs();
        }
        if(!nftSaleContractFolder.exists())
        {
            nftSaleContractFolder.mkdirs();
        }
        if(!nftTransactionHistoryFile.exists())
        {
            writeToFile(WALLET_DATA_PATH + "nftTransactionHistory.json", initiate.toString(), Boolean.valueOf(false)); 
        }

    }
}
