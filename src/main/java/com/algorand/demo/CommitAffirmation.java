package com.algorand.demo;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.algorand.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import com.mongodb.DB;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.isda.cdm.Affirmation;
import org.isda.cdm.Event;
import org.isda.cdm.PartyRoleEnum;
import org.isda.cdm.Trade;

import java.util.List;

public  class CommitAffirmation {

    public static void main(String [] args) throws Exception {
        for (String arg : args)
        {
            commitAffirmation(arg);
        }
    }

    public static void commitAffirmation(String fileName) throws JsonProcessingException
    {

        //Load the database to lookup users
        DB mongoDB = MongoUtils.getDatabase("uers");

        //Load a file with client global keys
        String allocationCDM = ReadAndWrite.readFile(fileName);
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        Event allocationEvent = null;
        try{
            allocationEvent = rosettaObjectMapper
                    .readValue(allocationCDM, Event.class);
        }
        catch(java.io.IOException e){
            e.printStackTrace();
        }

        List<Trade> allocatedTrades = allocationEvent
                .getPrimitive()
                .getAllocation().get(0)
                .getAfter()
                .getAllocatedTrade();

        //Keep track of the trade index
        int tradeIndex = 0;

        //Collect the affirmation transaction id and broker key in a file
        String result = "";
        //For each trade...
        for(Trade trade: allocatedTrades){

            //Get the broker that we need to send the affirmation to
            String brokerReference = trade.getExecution().getPartyRole()
                    .stream()
                    .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                    .map(r -> r.getPartyReference().getGlobalReference())
                    .collect(MoreCollectors.onlyElement());

            User broker = User.getUser(brokerReference);

            //Get the client reference for that trade
            String clientReference = trade.getExecution()
                    .getPartyRole()
                    .stream()
                    .filter(r-> r.getRole()==PartyRoleEnum.CLIENT)
                    .map(r->r.getPartyReference().getGlobalReference())
                    .collect(MoreCollectors.onlyElement());

            // Load the client user, with algorand passphrase
            User user = User.getUser(clientReference);
            String algorandPassphrase = user.algorandPassphrase;

            // Confirm the user has received the global key of the allocation from the broker
            String receivedKey = AlgorandUtils.readEventTransaction( algorandPassphrase, allocationEvent.getMeta().getGlobalKey());
            assert receivedKey == allocationEvent.getMeta().getGlobalKey() : "Have not received allocation event from broker";


            //Compute the affirmation
            Affirmation affirmation = new AffirmImpl().doEvaluate(allocationEvent,tradeIndex).build();

            MongoStore mongoStore = new MongoStore();
            mongoStore.addAffirmationToStore(affirmation);

            //Send the affirmation to the broker
            Transaction transaction = user.sendAffirmationTransaction(broker, affirmation);
            mongoStore.addAlgorandTransactionToStore(affirmation.getIdentifier().get(0).getMeta().getGlobalKey(), transaction, user, broker, "affirmation");

            result += transaction.getTx() + "," + brokerReference +"\n";


            tradeIndex = tradeIndex + 1;
        }
        try{
            ReadAndWrite.writeFile("./Files/AffirmationOutputs.txt", result);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}

     
