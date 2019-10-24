package com.algorand.utils;


import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.algod.client.model.Transaction;
import com.algorand.algosdk.crypto.Address;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoCommandException;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.isda.cdm.Affirmation;
import org.isda.cdm.Confirmation;
import org.isda.cdm.Event;
import org.isda.cdm.Party;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class User{
	public String globalKey;
	public String algorandID;
	public String algorandPassphrase;
	public String name;

	@JsonDeserialize(using = PartyDeserializer.class)
	@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
	public Party party;

	public static User getOrCreateUser(Party party,DB mongoDB){
		String partyKey = party.getMeta().getGlobalKey();
		Jongo jongo = new Jongo(mongoDB);
		MongoCollection users = jongo.getCollection("users");

		User foundUser = users.findOne("{party.meta.globalKey: '" + partyKey + "'}").as(User.class);
		if (foundUser == null){
			return new User(party,mongoDB);
		}
		else{
			return foundUser;
		}
	}

	public static User getUser(String partyKey){
		DB mongoDB = MongoUtils.getDatabase("users");
		Jongo jongo = new Jongo(mongoDB);
		MongoCollection users = jongo.getCollection("users");

		User foundUser = users.findOne("{party.meta.globalKey: '" + partyKey + "'}").as(User.class);
		System.out.println("key: " + partyKey);
		System.out.println(foundUser);
		return foundUser;
	}

	public User(){};

	public User(Party party, String globalKey, String algorandID, String algorandPassphrase, String name){
		this.party = party;
		this.globalKey = globalKey;
		this.algorandID = algorandID;
		this.algorandPassphrase = algorandPassphrase;
		this.name = name;

	}

	public  User(Party party,DB mongoDB){
		try{
			Jongo jongo = new Jongo(mongoDB);
			MongoCollection users = jongo.getCollection("users");

			Account algorandInfo = AlgorandUtils.createAccount();
			this.algorandID = algorandInfo.getAddress().toString();
			this.algorandPassphrase = algorandInfo.toMnemonic();
			this.party = party;
			this.globalKey = party.getMeta().getGlobalKey();
			this.name = party.getAccount().getAccountName().getValue();

			users.save(this);

		}
		catch(Exception e){
			e.printStackTrace();
			this.algorandID = null;
			this.algorandPassphrase = null;
			this.party = null;
			this.globalKey = null;
			this.name = null;
		}

	}

	/**
	 * Send a CDM Event to a specified user on the Distributed Ledger (Blockchain)
	 * @param user: The recipient user object for this transaction
	 * @param event: The CDM Event being sent to the user
	 * @param type: The type of the Event Primitive
	 * @return:  Returns an AlgoRand Transaction object corresponding to this commit
	 */
	public Transaction sendEventTransaction(User user, Event event, String type) {

		com.algorand.algosdk.algod.client.model.Transaction result = null;
		try{
			ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
			rosettaObjectMapper.setSerializationInclusion(Include.NON_NULL);

			String indexNotes = "{\"senderKey\": \" "+ this.globalKey + "\", \"type\": \""+type+"\", \"globalKey\": \"" + event.getMeta().getGlobalKey() + "\"}" ;
			String receiverAddress = user.algorandID;
			String senderSecret = this.algorandPassphrase;
			byte[] notes = indexNotes.getBytes();

			//String notes = rosettaObjectMapper
			//				.writeValueAsString(event);

			result = AlgorandUtils.signAndSubmit(Globals.ALGOD_API_ADDR, Globals.ALGOD_API_TOKEN, senderSecret,  receiverAddress,  notes,  BigInteger.valueOf(1000));
		}
		catch(Exception e){

			System.out.println("Caught an exception in sendTransaction");
			e.printStackTrace();
		}
		return result;
	}

	public Transaction sendAffirmationTransaction(User user, Affirmation affirmation) {

		com.algorand.algosdk.algod.client.model.Transaction result = null;
		try{

			ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
			rosettaObjectMapper.setSerializationInclusion(Include.NON_NULL);

			String receiverAddress = user.algorandID;
			String senderSecret = this.algorandPassphrase;
			String notes = rosettaObjectMapper
					.writeValueAsString(affirmation);

			result = AlgorandUtils.signAndSubmit(Globals.ALGOD_API_ADDR, Globals.ALGOD_API_TOKEN, senderSecret,  receiverAddress,  notes.getBytes(),BigInteger.valueOf(1000));
		}
		catch(Exception e){

			System.out.println("Caught an exception in sendTransaction");
			e.printStackTrace();
		}
		return result;
	}

	public Transaction sendConfirmationTransaction(User user, Confirmation confirmation) {

		com.algorand.algosdk.algod.client.model.Transaction result = null;
		try{

			ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
			rosettaObjectMapper.setSerializationInclusion(Include.NON_NULL);

			String receiverAddress = user.algorandID;
			String senderSecret = this.algorandPassphrase;
			String notes = rosettaObjectMapper.writeValueAsString(confirmation);
			result = AlgorandUtils.signAndSubmit(Globals.ALGOD_API_ADDR, Globals.ALGOD_API_TOKEN, senderSecret,  receiverAddress,  notes.getBytes(),BigInteger.valueOf(1000));
		}
		catch(Exception e){
			System.out.println("Caught an exception in sendTransaction");
			e.printStackTrace();
		}
		return result;
	}

	public ArrayList<String> createAlgorandAccount() throws Exception{
		Account act = new Account();

		//Get the new account address
		Address addr = act.getAddress();

		//Get the backup phrase
		String backup = act.toMnemonic();
		ArrayList<String> result = new ArrayList<String>();
		result.add(addr.toString());
		result.add(backup);
		return result;
	}

	public static void createMongoAccount(DB db, String username, String password){
		Map<String, Object> commandArguments = new HashMap<>();
		commandArguments.put("createUser", username);
		commandArguments.put("pwd", password);
		String[] roles = { "readWrite" };
		commandArguments.put("roles", roles);
		BasicDBObject command = new BasicDBObject(commandArguments);
		try{
			db.command(command);
		}
		catch(MongoCommandException e){
			System.out.println(command);
			throw(e);
		}
		db.getCollection(username);
	}

	public static void main(String [] args) throws IOException{

		DB mongoDB = MongoUtils.getDatabase("users");

		String partyObject = ReadAndWrite.readFile("./Files/PartyTest.json");
		System.out.println(partyObject);
		ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
		Party party = rosettaObjectMapper.readValue(partyObject, Party.class);
		User user = getOrCreateUser(party,mongoDB);
	}
}