package com.algorand.demo;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.algorand.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import com.mongodb.DB;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.isda.cdm.*;

import java.util.List;

public class CommitConfirmation {
    public static void main(String[] args){

        //Load the database to lookup users
        DB mongoDB = MongoUtils.getDatabase("users");

        //Load a file with client global keys
        String allocationFile = args[0];
        String allocationCDM = ReadAndWrite.readFile(allocationFile);
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

            User broker = User.getUser(brokerReference,mongoDB);

            //Get the client reference for that trade
            String clientReference = trade.getExecution()
                    .getPartyRole()
                    .stream()
                    .filter(r-> r.getRole()==PartyRoleEnum.CLIENT)
                    .map(r->r.getPartyReference().getGlobalReference())
                    .collect(MoreCollectors.onlyElement());

            // Load the client user, with algorand passphrase
            User user = User.getUser(clientReference,mongoDB);
            String algorandPassphrase = user.algorandPassphrase;

            // Confirm the user has received the global key of the allocation from the broker
            String receivedKey = AlgorandUtils.readEventTransaction( algorandPassphrase, allocationEvent.getMeta().getGlobalKey());
            assert receivedKey == allocationEvent.getMeta().getGlobalKey() : "Have not received allocation event from broker";

            //Compute the affirmation
            Confirmation confirmation = new ConfirmImpl().doEvaluate(allocationEvent,tradeIndex).build();

            //Send the affirmation to the broker
            Transaction transaction = user.sendConfirmationTransaction(broker, confirmation);
            result += transaction.getTx() + "," + brokerReference +"\n";

            tradeIndex = tradeIndex + 1;
        }
        try{
            ReadAndWrite.writeFile("./Files/ConfirmationOutputs.txt", result);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}