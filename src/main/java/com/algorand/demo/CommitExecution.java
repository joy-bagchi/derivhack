
package com.algorand.demo;
import com.algorand.cdmvalidators.ExecutionEventValidator;
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

import java.util.stream.Stream;

public  class CommitExecution {

    public static void main(String [] args) throws Exception {
        Stream.of(args).forEach(CommitExecution::commitTrade);
    }

    public static void commitTrade(String tradeFile) {

        try {
            //Read the input arguments and read them into files
            String fileName = tradeFile;
            String fileContents = ReadAndWrite.readFile(fileName);

            //Read the event file into a CDM object using the Rosetta object mapper
            ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
            Event event = rosettaObjectMapper.readValue(fileContents, Event.class);

            //Create Algorand Accounts for all parties
            // and persist accounts to filesystem/database
            List<Party> parties = event.getParty();
            User user;
            DB mongoDB = MongoUtils.getDatabase("users");
            parties.parallelStream()
                    .map(party -> User.getOrCreateUser(party, mongoDB))
                    .collect(Collectors.toList());

            //Get the execution
            Execution execution = new ExecutionEventValidator(event)
                    .validateParties()
                    .validateEconomics()
                    .getExecution();

            String executingPartyReference = execution.getPartyRole()
                    .stream()
                    .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                    .map(r -> r.getPartyReference().getGlobalReference())
                    .collect(MoreCollectors.onlyElement());

            // Get the executing party
            Party executingParty = event.getParty().stream()
                    .filter(p -> executingPartyReference.equals(p.getMeta().getGlobalKey()))
                    .collect(MoreCollectors.onlyElement());

            // Get all other parties
            List<Party> otherParties = event.getParty().stream()
                    .filter(p -> !executingPartyReference.equals(p.getMeta().getGlobalKey()))
                    .collect(Collectors.toList());

            // Find or create the executing user
            User executingUser = User.getOrCreateUser(executingParty, mongoDB);

            //Send all other parties the contents of the event as a set of blockchain transactions
            List<User> users = otherParties.
                    parallelStream()
                    .map(p -> User.getOrCreateUser(p, mongoDB))
                    .collect(Collectors.toList());

            List<Transaction> transactions = users
                    .parallelStream()
                    .map(u -> executingUser.sendEventTransaction(u, event, "execution"))
                    .collect(Collectors.toList());
        }
        catch (ValidationException ex)
        {
            System.out.println("Exception occurred for Event ID : "+ ex.getUnderlyingEvent().getMeta().getGlobalKey());
            System.out.println("Exception Reasons : ");
            ex.getExceptionCollection().stream().forEach(System.out::println);
        }
        catch (IOException ex)
        {
            System.out.println("I/O Exception: "+ ex.getMessage());
        }
    }

}