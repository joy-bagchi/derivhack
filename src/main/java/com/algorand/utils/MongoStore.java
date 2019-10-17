package com.algorand.utils;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.bson.Document;
import org.isda.cdm.Affirmation;
import org.isda.cdm.Confirmation;
import org.isda.cdm.Event;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

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

    protected void addToEventStore(String globalKey, String stage, String json)
    {
        Document document = new Document(
                "globalKey", globalKey)
                .append("stage", stage)
                .append("eventJson", json);
        this.cdmGlobalKeyToEventCollection.insertOne(document);
        System.out.println("added event to mongo: globalKey=" + globalKey + ", stage=" + stage);
    }

    @Override
    public void addEventToStore(Event event, String stage) throws JsonProcessingException
    {
        String globalKey = MongoStore.getGlobalKey(event);
        this.addToEventStore(globalKey, stage, this.rosettaMapper.writeValueAsString(event));
    }

    @Override
    public void addAffirmationToStore(Affirmation affirmation) throws JsonProcessingException
    {
        String globalKey = affirmation.getIdentifier().get(0).getMeta().getGlobalKey();
        this.addToEventStore(globalKey, "affirmation", this.rosettaMapper.writeValueAsString(affirmation));
    }

    @Override
    public void addConfirmationToStore(Confirmation confirmation) throws JsonProcessingException
    {
        String globalKey = confirmation.getIdentifier().get(0).getMeta().getGlobalKey();
        this.addToEventStore(globalKey, "confirmation", this.rosettaMapper.writeValueAsString(confirmation));
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
        System.out.println("got JSON from mongo global key \"" + globalKey + "\": " + eventJson);
        Event event = null;
        try
        {
            event = this.rosettaMapper.readValue(eventJson, Event.class);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out.println("got event from mongo JSON from global key \"" + globalKey + "\": " + event.toString());
        return event;
    }

    @Override
    public void addAlgorandTransactionToStore(String globalKey, Transaction transaction, User sender, User receiver, String stage)
    {
        String transactionId = transaction.getTx();
        Document document = new Document(
                "cdmGlobalKey", globalKey)
                .append("stage", stage)
                .append("algorandTransactionId", transactionId)
                .append("senderName", sender.name)
                .append("receiverName", receiver.name)
                .append("algorandSenderId", sender.algorandID)
                .append("algorandReceiverId", receiver.algorandID);
        this.cdmGlobalKeyToAlgorandTransactionCollection.insertOne(document);
        System.out.println("added algorand transaction to mongo: globalKey=" + globalKey +", transactionId=" +
                transactionId + ", senderName=" + sender.name + ", receiverName=" + receiver.name + ", senderId=" +
                sender.algorandID + ", receiverId=" + receiver.algorandID);
    }

    @Override
    public void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, List<User> senders, List<User> receivers, String stage)
    {
        for(int transactionId = 0; transactionId < transactions.size(); transactionId++)
        {
            Transaction transaction = transactions.get(0);
            User sender = senders.get(0);
            User receiver = receivers.get(0);
            this.addAlgorandTransactionToStore(globalKey, transaction, sender, receiver, stage);
        }
    }

    @Override
    public void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, User sender, List<User> receivers, String stage)
    {
        this.addAlgorandTransactionsToStore(globalKey, transactions, Collections.nCopies(receivers.size(), sender), receivers, stage);
    }

    protected List<String> getGlobalKeysByParty(String partyName)
    {
        List<String> globalKeys = new ArrayList<>();
        for(Document curDocument : this.cdmGlobalKeyToAlgorandTransactionCollection.find(
                or(eq("senderName", partyName), eq("receiverName", partyName))))
        {
            globalKeys.add(curDocument.getString("cdmGlobalKey"));
        }
        return globalKeys;
    }

    @Override
    public List<Event> getEventsByParty(String partyName)
    {
        List<String> globalKeys = this.getGlobalKeysByParty(partyName);
        ArrayList<Event> events = new ArrayList<>();
        for(String globalKey: globalKeys)
        {
            events.add(this.getEventFromStore(globalKey));
        }
        return events;
    }
}
