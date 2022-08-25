package com.rubix.Datum;

import java.util.List;

public class TokenChain {
	public String sender;
	public String senderSign;
	public String comment;
	public String tid;
	public String[] group;
	public String owner;
	public String blockHash;
	
	
	public String getSender() {
		return sender;
	}
	public void setSender(String sender) {
		this.sender = sender;
	}
	public String getSenderSign() {
		return senderSign;
	}
	public void setSenderSign(String senderSign) {
		this.senderSign = senderSign;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public String getTid() {
		return tid;
	}
	public void setTid(String tid) {
		this.tid = tid;
	}
	public String[] getGroup() {
		return group;
	}
	public void setGroup(String[] group) {
		this.group = group;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public String getBlockHash() {
		return blockHash;
	}
	public void setBlockHash(String blockHash) {
		this.blockHash = blockHash;
	}
	
}

