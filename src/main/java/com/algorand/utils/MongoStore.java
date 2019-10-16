package com.algorand.utils;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.bson.Document;
import org.isda.cdm.Event;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class MongoStore extends CDMLocalStore
{

    private static String DB_NAME = "cdmstore";

    private MongoCollection<Document> cdmGlobalKeyToEventCollection;
    private MongoCollection<Document> cdmGlobalKeyToAlgorandTransactionCollection;
    private ObjectMapper rosettaMapper;

    public MongoStore()
    {
        MongoClient mongoClient = MongoStore.getClient();
        MongoDatabase database = mongoClient.getDatabase(MongoStore.DB_NAME);
        this.cdmGlobalKeyToEventCollection = database.getCollection("cdmGlobalKeyToEvent");
        this.cdmGlobalKeyToAlgorandTransactionCollection = database.getCollection("cdmGlobalKeyToAlgorandTransaction");
        this.rosettaMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
    }

    private static MongoClient getClient()
    {
        return new MongoClient("localhost");
    }

    public static void dropDatabase()
    {
        MongoClient mongoClient = MongoStore.getClient();
        mongoClient.dropDatabase(MongoStore.DB_NAME);
    }

    @Override
    public void addEventToStore(Event event) throws JsonProcessingException
    {
        Document document = new Document(
                "globalKey", MongoStore.getGlobalKey(event))
                .append("eventJson", this.rosettaMapper.writeValueAsString(event));
        this.cdmGlobalKeyToEventCollection.insertOne(document);
    }

    @Override
    public Event getEventFromStore(String globalKey)
    {
        Document document = null;
        boolean duplicateFound = false;
        for(Document curDocument : this.cdmGlobalKeyToEventCollection.find(eq("globalKey", globalKey)))
        {
            if(document == null)
            {
                document = curDocument;
            } else
            {
                duplicateFound = true;
            }
        }
        if(duplicateFound)
        {
            System.out.println("WARNING: more than one event for key " + globalKey);
        }

        String eventJson = document.getString("eventJson");
        Event event = null;
        try
        {
            event = this.rosettaMapper.readValue(eventJson, Event.class);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return event;
    }

    @Override
    public void addAlgorandTransactionToStore(String globalKey, String algorandTransactionId, String algorandSenderId, String algorandReceiverId)
    {
        Document document = new Document(
                "cdmGlobalKey", globalKey)
                .append("algorandTransactionId", algorandTransactionId)
                .append("algorandSenderId", algorandSenderId)
                .append("algorandReceiverId", algorandReceiverId);
        this.cdmGlobalKeyToAlgorandTransactionCollection.insertOne(document);
    }

    @Override
    public void addAlgorandTransactionToStore(String globalKey, Transaction transaction, User sender, User receiver)
    {
        this.addAlgorandTransactionToStore(globalKey, transaction.getTx(), sender.algorandID, receiver.algorandID);
    }

    @Override
    public void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, List<User> senders, List<User> receivers)
    {
        for(int transactionId = 0; transactionId < transactions.size(); transactionId++)
        {
            Transaction transaction = transactions.get(0);
            User sender = senders.get(0);
            User receiver = receivers.get(0);
            this.addAlgorandTransactionToStore(globalKey, transaction, sender, receiver);
        }
    }

    @Override
    public void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, User sender, List<User> receivers)
    {
        this.addAlgorandTransactionsToStore(globalKey, transactions, Collections.nCopies(receivers.size(), sender), receivers);
    }

}
