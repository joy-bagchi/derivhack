package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.validation.ValidationResult;
import org.isda.cdm.*;
import org.isda.cdm.validation.datarule.AllocationOutcomeExecutionClosedDataRule;


import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

public class ValidatedAllocationEvent extends BaseEventValidator{

    private Predicate<Event> allocationEventPredicate;
    public ValidatedAllocationEvent(Event event) throws ValidationException {
        super(event);
        if(event.getPrimitive().getAllocation() == null)
            throw new ValidationException("The provided event does not contain a primitive of type 'allocation'", event);
        allocationEventPredicate = e -> {return true;};
    }

    public List<Trade> getAllocatedTrades() throws ValidationException {
        validate(allocationEventPredicate);
        return event
                .getPrimitive()
                .getAllocation().get(0)
                .getAfter()
                .getAllocatedTrade();
    }

    public Trade getOriginalTrades() throws ValidationException {
        validate(allocationEventPredicate);
        return event
                .getPrimitive()
                .getAllocation().get(0)
                .getAfter()
                .getOriginalTrade();
    }
    public ValidatedAllocationEvent validateEconomics()
    {
        allocationEventPredicate = allocationEventPredicate
                .and(this::validateQuantitySumMatchOriginal);
        return this;
    }

    public ValidatedAllocationEvent validateParties() {
        allocationEventPredicate = allocationEventPredicate
                .and(this::validateTotalNumberOfParties)
                .and(this::validatePartyRolesForAllocatedTrades);
        return this;
    }

    public ValidatedAllocationEvent validateLineage() {
        allocationEventPredicate = allocationEventPredicate
                .and(this::validateLineageMatchesAfterOriginalTrade)
                .and(this::validateLineageMatchesBeforeExecution);
        return this;
    }

    public ValidatedAllocationEvent validateCDMDataRules() {
        allocationEventPredicate = allocationEventPredicate
                .and(this::validateAllocationOutcomeExecutionClosed);
        return this;
    }

    //-------------------------Validation rules-------------------------------
    private boolean validateQuantitySumMatchOriginal(Event event) {
        List<Trade> allocatedTrades = event.getPrimitive().getAllocation().get(0).getAfter().getAllocatedTrade();
        BigDecimal originalQuantity = event.getPrimitive()
                .getAllocation().get(0)
                .getBefore().getExecution()
                .getQuantity().getAmount();
        BigDecimal sumOfAllocatedQuantity = allocatedTrades.stream().map(
                t -> t.getExecution().getQuantity().getAmount()).reduce(BigDecimal::add).get();
        if(sumOfAllocatedQuantity.equals(originalQuantity)) return  true;
        addException("Allocated quantity does not add up to original quantity (Allocated Sum: "
                + sumOfAllocatedQuantity +
                ", Original Quantity: " + originalQuantity + ").");
        return false;
    }

    private boolean validateTotalNumberOfParties(Event event) {
        int baseParties = 3; //Client, ExecutingBroker, Counterparty
        int totalAllocatedTrades = event
                .getPrimitive()
                .getAllocation().get(0)
                .getAfter()
                .getAllocatedTrade().size();
        //Total parties must match baseParties(3) +  totalAllocatedTrades (1 sub account per allocated trade)
        if(event.getParty().size() == (baseParties+totalAllocatedTrades)) return true;
        addException("Total parties expected: " + (baseParties+totalAllocatedTrades) + ", found: " + event.getParty().size());
        return false;
    }

    private boolean validatePartyRolesForAllocatedTrades(Event event) {
        List<Trade> allocatedTrades = event.getPrimitive().getAllocation().get(0).getAfter().getAllocatedTrade();
        for(Trade trade : allocatedTrades){
            try {
                new ValidatedExecutionEvent(event, trade.getExecution())
                        .validatePartyRoles()
                        .getExecution();
            }
            catch (ValidationException e)
            {
                e.getExceptionCollection().stream().forEach(this::addException);
                return false;
            }
        };
        return true;
    }

    private boolean validateLineageMatchesAfterOriginalTrade(Event evt)
    {
        //we can safely except to have a lineage since we have checked that the primitive is allocation
        if(evt.getLineage().getExecutionReference().get(0).getGlobalReference()
            .equals(
                evt.getPrimitive().getAllocation().get(0)
                        .getAfter()
                        .getOriginalTrade()
                        .getExecution()
                        .getMeta()
                        .getGlobalKey()
            )) return true;
        addException("GlobalReference in lineage does not match GlobalKey in after->OriginalTrade");
        return false;
    }

    private boolean validateLineageMatchesBeforeExecution(Event evt)
    {
        if(evt.getLineage().getExecutionReference().get(0).getGlobalReference().equals(
                evt.getPrimitive().getAllocation().get(0)
                        .getBefore()
                        .getExecution()
                        .getMeta()
                        .getGlobalKey()
                )) return true;
        addException("GlobalReference in lineage does not match GlobalKey in after->OriginalTrade");
        return false;
    }

    private boolean validateAllocationOutcomeExecutionClosed(Event evt)
    {
        AllocationOutcomeExecutionClosedDataRule rule = new AllocationOutcomeExecutionClosedDataRule();
        ValidationResult<AllocationOutcome> vr =  rule.validate(RosettaPath.valueOf(
                "primitive.allocation.*.after.originalTrade.execution.closedState.state"),
                evt.getPrimitive().getAllocation().get(0).getAfter());
        if(vr.isSuccess()) return true;
        addException(vr.getFailureReason().get());
        return false;
    }


    //-------------------------Validation rules end-------------------------------
}
