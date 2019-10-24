package com.algorand.demo;

import com.algorand.algosdk.algod.client.model.Transaction;
import com.algorand.cdmvalidators.ValidatedAllocationEvent;
import com.algorand.cdmvalidators.ValidatedExecutionEvent;
import com.algorand.cdmvalidators.ValidatedTranferPrimitive;
import com.algorand.exceptions.ValidationException;
import com.algorand.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
import com.mongodb.DB;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;
import org.isda.cdm.metafields.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommitSettlementEvent {

    Event.EventBuilder settlementEventBuilder = Event.builder();
    ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();

    public static void main(String [] args) throws Exception {
        Stream.of(args).forEach(CommitSettlementEvent::commitSettlement);
    }

    public static void commitSettlement(String fileName) {
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        //Read the input arguments and read them into files
        String fileContents = ReadAndWrite.readFile(fileName);
        DB mongoDB = MongoUtils.getDatabase("users");
        //Read the event file into a CDM object using the Rosetta object mapper
        try {
            Event allocationEvent = rosettaObjectMapper
                    .readValue(fileContents, Event.class);
            CommitSettlementEvent cse = new CommitSettlementEvent();
            Event settlementEvent = cse.createSettlement(allocationEvent);
            ValidatedTranferPrimitive transfer = new ValidatedTranferPrimitive(settlementEvent, allocationEvent)
                    .validateLineage()
                    //.validateEconomics()
                    .validateSettlementDate();

            transfer.getAllTransfers();
            MongoStore mongoStore = new MongoStore();
            mongoStore.addEventToStore(settlementEvent, "settlement");

            ValidatedAllocationEvent validatedEvent = new ValidatedAllocationEvent(allocationEvent)
                    .validateEconomics()
                    .validateLineage()
                    .validateParties()
                    .validateCDMDataRules();

            List<Party> parties = Lists.newArrayList();
            parties.add(validatedEvent.getCounterParty());
            parties.addAll(validatedEvent.getSubAccounts());
            //Get the allocated trades
            List<Trade> allocatedTrades  = validatedEvent.getAllocatedTrades();

            //Get the executions of the allocated trades
            List<Execution> executions = allocatedTrades.stream()
                    .map(trade -> trade.getExecution())
                    .collect(Collectors.toList());

            User executingBroker = User.getOrCreateUser(validatedEvent.getExecutingEntity(), mongoDB);
            for(Party party: parties) {

                User otherUser = User.getOrCreateUser(party, mongoDB);
                // Get the client

                //Send client the event globalkey  as a  blockchain transaction
                Transaction transaction = executingBroker.sendEventTransaction(otherUser, settlementEvent, "settlement");
                mongoStore.addAlgorandTransactionToStore(MongoStore.getGlobalKey(settlementEvent), transaction, executingBroker, otherUser, "settlement");
            }
            //System.out.println(cse.rosettaObjectMapper.writeValueAsString(settlementEvent));
        }
        catch(IOException  ex)
        {
            ex.printStackTrace();
        }
        catch (ValidationException ex){
            System.out.println("Validation failed with reason");
            ex.getExceptionCollection().forEach(System.out::println);
        }
    }

    public Event createSettlement(Event allocationEvent) {
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
            LocalDate today = LocalDate.now();
            settlementEventBuilder.setEventDate(DateImpl.of(today.getYear(),
                    today.getMonth().getValue(),
                    today.getDayOfMonth()));

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
                transferPrimitives.add(createTransferForUser(allocatedTrade, PartyRoleEnum.CLIENT,  originalExecution.getSideForExecutingEntity().getRole()));
            }
            settlementEventBuilder.setPrimitive(PrimitiveEvent.builder()
                    .addTransfer(transferPrimitives).build());

            List<ReferenceWithMetaExecution> executionReferences = Lists.newArrayList();
            allTrades.forEach(t -> {
                executionReferences.add(ReferenceWithMetaExecution.builder()
                        .setGlobalReference(
                                t.getExecution().getMeta().getGlobalKey())
                        .build());
            });
            Lineage allocationLineage = Lineage.builder()
                    .addEventReference(ReferenceWithMetaEvent.builder()
                            .setGlobalReference(allocationEvent.getMeta().getGlobalKey())
                            .build())
                    .addExecutionReference(executionReferences)
                    .build();
            settlementEventBuilder.setLineage(allocationLineage);
            settlementEventBuilder.setMeta(MetaFields.builder()
                    .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                    .build());
            PartyRole executingBroker = originalTrade.getExecution().getPartyRole().stream()
                    .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                    .collect(MoreCollectors.onlyElement());

            settlementEventBuilder.addEventIdentifier(Identifier.builder()
                    .setIssuerReference(ReferenceWithMetaParty.builder()
                            .setGlobalReference(executingBroker.getPartyReference().getGlobalReference())
                            .build())
                    .addAssignedIdentifier(AssignedIdentifier.builder()
                            .setVersion(1)
                            .setIdentifier(FieldWithMetaString.builder()
                                    .setValue(UniqueIdentifierGenerator.randomAlphaNumeric(13))
                                    .build())
                            .build())
                    .setMeta(MetaFields.builder()
                            .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                            .build())
                    .build());
            settlementEventBuilder.addTimestamp(EventTimestamp.builder()
                    .setDateTime(ZonedDateTime.now())
                    .setQualification(EventTimestampQualificationEnum.EVENT_CREATION_DATE_TIME)
                    .build());

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
                .setIdentifier(FieldWithMetaString.builder()
                        .setValue(UniqueIdentifierGenerator.randomAlphaNumeric(13))
                        .setMeta(MetaFields.builder()
                                .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                                .build())
                        .build())
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
                .setSettlementDate(AdjustableOrAdjustedOrRelativeDate.builder()
                        .setUnadjustedDate(trade
                                .getExecution()
                                .getSettlementTerms()
                                .getSettlementDate()
                                .getAdjustableDate()
                                .getUnadjustedDate())
                        .build())
                .build();
        return tp;
    }
}
