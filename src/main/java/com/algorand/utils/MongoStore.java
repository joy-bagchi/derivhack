package com.algorand.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.bson.Document;
import org.isda.cdm.Event;

import java.io.IOException;

import static com.mongodb.client.model.Filters.eq;

public class MongoStore implements CDMLocalStore
{

    private MongoDatabase database;
    private MongoCollection<Document> cdmGlobalKeyToEventCollection;
    private ObjectMapper rosettaMapper;

    public MongoStore()
    {
        MongoClient mongoClient = new MongoClient( "localhost" );
        this.database = mongoClient.getDatabase("cdmstore");
        this.cdmGlobalKeyToEventCollection = this.database.getCollection("cdmGlobalKeyToEvent");
        this.rosettaMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
    }

    @Override
    public void addEventToStore(Event event) throws JsonProcessingException
    {
        Document document = new Document(
                "globalKey", event.getEventIdentifier().get(0).getMeta().getGlobalKey())
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

        String eventJson = (String) document.get("eventJson");
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

    public static void main(String[] args)
    {
        String fileName = "./Files/UC1_block_execute_BT1.json";
        String fileContents = ReadAndWrite.readFile(fileName);

        //Read the event file into a CDM object using the Rosetta object mapper
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        Event inputEvent = null;
        try
        {
            inputEvent = rosettaObjectMapper.readValue(fileContents, Event.class);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        MongoStore store = new MongoStore();

        try
        {
            store.addEventToStore(inputEvent);
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }

        Event outputEvent = store.getEventFromStore(inputEvent.getEventIdentifier().get(0).getMeta().getGlobalKey());
        System.out.println("found event: " + outputEvent.toString());
    }
}
