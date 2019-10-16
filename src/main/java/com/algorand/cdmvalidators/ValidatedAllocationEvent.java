package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.validation.ValidationResult;
import org.isda.cdm.*;
import org.isda.cdm.validation.datarule.AllocationOutcomeExecutionClosedDataRule;


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
