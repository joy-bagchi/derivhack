package com.algorand.demo;
import com.algorand.cdmvalidators.ValidatedAllocationEvent;
import com.algorand.exceptions.ValidationException;
import com.algorand.utils.*;
import com.algorand.algosdk.algod.client.model.Transaction;

import com.mongodb.DB;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;

import java.io.IOException;
import java.util.*;

import org.isda.cdm.*;

import java.util.stream.Collectors;
import com.google.common.collect.MoreCollectors;

public  class CommitAllocation {

    public static void main(String [] args) {
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        //Read the input arguments and read them into files
        String fileName = args[0];
        String fileContents = ReadAndWrite.readFile(fileName);

        //Read the event file into a CDM object using the Rosetta object mapper
        try {
            Event event = rosettaObjectMapper
                    .readValue(fileContents, Event.class);
            MongoStore mongoStore = new MongoStore();
            mongoStore.addEventToStore(event, "allocation");
            //Add any new parties to the database, and commit the event to their own private databases
            List<Party> parties = event.getParty();
            DB mongoDB = MongoUtils.getDatabase("users");
            parties.parallelStream()
                    .map(party -> User.getOrCreateUser(party,mongoDB))
                    .collect(Collectors.toList());


            ValidatedAllocationEvent validatedEvent = new ValidatedAllocationEvent(event)
                    .validateEconomics()
                    .validateLineage()
                    .validateParties()
                    .validateCDMDataRules();

            //Get the allocated trades
            List<Trade> allocatedTrades  = validatedEvent.getAllocatedTrades();

            //Get the executions of the allocated trades
            List<Execution> executions = allocatedTrades.stream()
                    .map(trade -> trade.getExecution())
                    .collect(Collectors.toList());

            //For each execution, the executing party (broker) sends
            // a notification of the allocation event to each client account

            //Collect a list of clients that need to affirm the allocation
            String clients="";

            for(Execution execution: executions){
                //Get the executing party reference
                String executingPartyReference = execution.getPartyRole()
                        .stream()
                        .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                        .map(r -> r.getPartyReference().getGlobalReference())
                        .collect(MoreCollectors.onlyElement());

                // Get the client
                String clientReference = execution.getPartyRole()
                        .stream()
                        .filter(r -> r.getRole() == PartyRoleEnum.CLIENT)
                        .map(r -> r.getPartyReference().getGlobalReference())
                        .collect(MoreCollectors.onlyElement());

                // Get the executing user
                User executingUser = User.getUser(executingPartyReference,mongoDB);

                // Get the client
                User clientUser = User.getUser(clientReference,mongoDB);

                //Send client the contents of the event as a set of blockchain transactions
                Transaction transaction = executingUser.sendEventTransaction(clientUser, event, "allocation");
                mongoStore.addAlgorandTransactionToStore(MongoStore.getGlobalKey(event), transaction, executingUser, clientUser, "allocation");

                clients += clientReference + "\n";
            }

            //Write client references to a file consumed by "CommitAffirmation.java"
            ReadAndWrite.writeFile("./Files/AffirmationInputs.txt",clients);
        } catch (IOException e) {
            e.printStackTrace();
        }
        catch (ValidationException e)
        {
            System.out.println("Validation failed with " );
            e.getExceptionCollection().forEach(System.out::println);
        }
    }
}
