package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.algorand.utils.User;
import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
import org.isda.cdm.*;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ValidatedTranferPrimitive extends BaseEventValidator{

    private Event allocationEvent;
    private Predicate<Event> transferPredicate;
    private List<TransferPrimitive> transfers;
    private ValidatedAllocationEvent validatedAllocationEvent;
    public ValidatedTranferPrimitive(Event event, Event allocationEvent) throws ValidationException {
        super(event);
        this.allocationEvent = allocationEvent;
        validatedAllocationEvent = new ValidatedAllocationEvent(allocationEvent)
                .validateEconomics()
                .validateLineage()
                .validateParties()
                .validateCDMDataRules();
        transferPredicate = e->{return true;};
        if(event.getPrimitive().getTransfer() == null)
            throw new ValidationException("The event does not contain any transferPrimitive", event);
        transfers = event.getPrimitive().getTransfer();
    }

    public TransferPrimitive getTransfer() throws ValidationException {
        validate(transferPredicate);
        return event.getPrimitive().getTransfer().get(0);
    }

    public TransferPrimitive getTransfer(int idx) throws ValidationException {
        validate(transferPredicate);
        return event.getPrimitive().getTransfer().get(idx);
    }

    public List<TransferPrimitive> getAllTransfers() throws ValidationException {
        validate(transferPredicate);
        return event.getPrimitive().getTransfer();
    }

    public ValidatedTranferPrimitive validateEconomics()
    {
        transferPredicate = transferPredicate
        .and(this::validateZeroNetCashForExecutingBroker);
        return this;
    }

    public ValidatedTranferPrimitive validateSettlementDate()
    {
        transferPredicate = transferPredicate
                .and(this::validateAllSettlementDates);
        return this;
    }

    public ValidatedTranferPrimitive validateLineage()
    {
        transferPredicate = transferPredicate
                .and(this::validateLineageToAllocation);
        return this;
    }


    private boolean validateZeroNetCashForExecutingBroker(Event event) {
        try {
            String executingEntityReference = validatedAllocationEvent.getExecutingEntity().getMeta().getGlobalKey();
            Double totalPayed = event
                    .getPrimitive()
                    .getTransfer()
                    .stream().filter(
                            t -> t
                            .getCashTransfer()
                            .get(0)
                            .getPayerReceiver()
                            .getPayerPartyReference()
                            .getGlobalReference().equals(executingEntityReference))
                    .map(t -> t
                            .getCashTransfer().get(0)
                            .getAmount().getAmount().doubleValue())
                    .reduce(Double::sum).get();

            Double totalReceived = event
                    .getPrimitive()
                    .getTransfer()
                    .stream()
                    .filter(t -> t
                            .getCashTransfer()
                            .get(0)
                            .getPayerReceiver()
                            .getReceiverPartyReference()
                            .getGlobalReference().equals(executingEntityReference))
                    .map(t -> t
                            .getCashTransfer().get(0)
                            .getAmount().getAmount().doubleValue())
                    .reduce(Double::sum).get();
            if(totalPayed.doubleValue() == totalReceived.doubleValue()) return true;
            addException("Executing broker total payed and received are not equal (Payed: " + totalPayed +
                    ", Received: "+ totalReceived);
        } catch (ValidationException e) {
            e.getExceptionCollection().forEach(this::addException);
        }
        return false;
    }

    private boolean validateLineageToAllocation(Event event) {
        if(event.getLineage().getEventReference().get(0).getGlobalReference().equals(
                allocationEvent.getMeta().getGlobalKey()
        )) return true;
        addException("Lineage to Allocation event failed: (Expected: " +
                allocationEvent.getMeta().getGlobalKey() +
                ", Got: " + event.getLineage().getEventReference().get(0).getGlobalReference() + ").");
        return false;
    }

    private boolean validateAllSettlementDates(Event event) {
        if(transfers.stream().allMatch(t -> t.getSettlementDate()
                .getUnadjustedDate()
                .toLocalDate()
                .isAfter(
                    LocalDate.now()
                )
        )) {
            addException("Settlement dates cannot be in the future");
            return false;
        }
        return true;
    }
}
