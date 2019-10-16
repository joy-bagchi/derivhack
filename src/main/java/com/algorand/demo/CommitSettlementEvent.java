package com.algorand.demo;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.algorand.cdmvalidators.ValidatedAllocationEvent;
import com.algorand.cdmvalidators.ValidatedExecutionEvent;
import com.algorand.exceptions.ValidationException;
import com.algorand.utils.MongoStore;
import com.algorand.utils.MongoUtils;
import com.algorand.utils.ReadAndWrite;
import com.algorand.utils.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
import com.mongodb.DB;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.isda.cdm.*;
import org.isda.cdm.metafields.ReferenceWithMetaEvent;
import org.isda.cdm.metafields.ReferenceWithMetaExecution;
import org.isda.cdm.metafields.ReferenceWithMetaParty;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CommitSettlementEvent {

    Event.EventBuilder settlementEventBuilder = Event.builder();;
    ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();

    public static void main(String [] args) {
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        //Read the input arguments and read them into files
        String fileName = args[0];
        String fileContents = ReadAndWrite.readFile(fileName);

        //Read the event file into a CDM object using the Rosetta object mapper
        try {
            Event allocationEvent = rosettaObjectMapper
                    .readValue(fileContents, Event.class);
            CommitSettlementEvent cse = new CommitSettlementEvent();
            Event settlementEvent = cse.createSettlement(allocationEvent);
            System.out.println(cse.rosettaObjectMapper.writeValueAsString(settlementEvent));
        }
        catch(IOException ex)
        {

        }
    }

    public Event createSettlement(Event allocationEvent) {

        //Read the input arguments and read them into files
        
        //Read the event file into a CDM object using the Rosetta object mapper
        try {
            settlementEventBuilder.setAction(ActionEnum.NEW);
            //Add any new parties to the database, and commit the event to their own private databases
            List<Party> parties = allocationEvent.getParty();
            User user;
            DB mongoDB = MongoUtils.getDatabase("users");
            parties.parallelStream()
                    .map(party -> User.getOrCreateUser(party, mongoDB))
                    .collect(Collectors.toList());

            settlementEventBuilder.addParty(parties);

            ValidatedAllocationEvent validatedEvent = new ValidatedAllocationEvent(allocationEvent)
                    .validateEconomics()
                    .validateLineage()
                    .validateParties()
                    .validateCDMDataRules();

            //Get the allocated trades
            List<Trade> allocatedTrades = validatedEvent.getAllocatedTrades();
            Trade originalTrade = validatedEvent.getOriginalTrades();
            List<Trade> allTrades = Lists.newArrayList();
            allTrades.addAll(allocatedTrades);
            allTrades.add(originalTrade);
            List<TransferPrimitive> transferPrimitives = Lists.newArrayList();

            ValidatedExecutionEvent originalExecution = new ValidatedExecutionEvent(allocationEvent, originalTrade.getExecution());

            transferPrimitives.add(createTransferForUser(originalTrade, PartyRoleEnum.COUNTERPARTY, originalExecution.getSideForExecutingEntity().getRole()));
            for(Trade allocatedTrade: allocatedTrades)
            {
                transferPrimitives.add(createTransferForUser(originalTrade, PartyRoleEnum.CLIENT,  originalExecution.getSideForExecutingEntity().getRole()));
            }
            settlementEventBuilder.setPrimitive(PrimitiveEvent.builder()
                    .addTransfer(transferPrimitives).build());

            List<ReferenceWithMetaExecution> executionReferences = Lists.newArrayList();
            allTrades.forEach(t -> {
                executionReferences.add(ReferenceWithMetaExecution.builder()
                        .setGlobalReference(
                                t.getExecution()
                                 .getIdentifier().get(0).getMeta().getGlobalKey())
                        .build());
            });
            Lineage allocationLineage = Lineage.builder()
                    .addEventReference(ReferenceWithMetaEvent.builder()
                            .setGlobalReference(allocationEvent.getEventIdentifier().get(0)
                                    .getIssuerReference()
                                    .getGlobalReference())
                            .build())
                    .addExecutionReference(executionReferences)
                    .build();
            settlementEventBuilder.setLineage(allocationLineage);

        }
        catch (ValidationException e) {
            System.out.println("Validation failed with ");
            e.getExceptionCollection().forEach(System.out::println);
        }
        return settlementEventBuilder.build();
    }

    private TransferPrimitive createTransferForUser(Trade trade, PartyRoleEnum facing, PartyRoleEnum buyerseller)
    {
        PartyRole executingBroker = trade.getExecution().getPartyRole().stream()
                .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                .collect(MoreCollectors.onlyElement());
        PartyRole otherParty = trade.getExecution().getPartyRole().stream()
                .filter(r -> r.getRole() == facing)
                .collect(MoreCollectors.onlyElement());
        ReferenceWithMetaParty securityTrasferee = ReferenceWithMetaParty.builder()
                .setGlobalReference(otherParty
                        .getPartyReference()
                        .getGlobalReference())
                .build();
        ReferenceWithMetaParty  securityTrasferor = ReferenceWithMetaParty.builder()
                .setGlobalReference(executingBroker
                        .getPartyReference()
                        .getGlobalReference())
                .build();
        if(buyerseller == PartyRoleEnum.SELLER || facing == PartyRoleEnum.COUNTERPARTY) {
                ReferenceWithMetaParty temp = securityTrasferee;
                securityTrasferee = securityTrasferor;
                securityTrasferor = temp;
        }

        TransferPrimitive tp = TransferPrimitive.builder()
                .setStatus(TransferStatusEnum.SETTLED)
                .setSettlementType(TransferSettlementEnum.DELIVERY_VERSUS_PAYMENT)
                .addSecurityTransfer(SecurityTransferComponent.builder()
                        .setQuantity(trade.getExecution().getQuantity().getAmount())
                        .setSecurity(Security.builder()
                                .setBond(trade.getExecution().getProduct().getSecurity().getBond())
                                .build())
                        .setTransferorTransferee(TransferorTransferee.builder()
                                .setTransfereePartyReference(securityTrasferee)
                                .setTransferorPartyReference(securityTrasferor)
                                .build())
                        .build())
                .addCashTransfer(CashTransferComponent.builder()
                        .setAmount(trade.getExecution().getSettlementTerms().getSettlementAmount())
                        .setPayerReceiver(PayerReceiver.builder()
                                .setPayerPartyReference(securityTrasferee)
                                .setReceiverPartyReference(securityTrasferor)
                                .build())
                        .build())
                .build();
        return tp;
    }
}
