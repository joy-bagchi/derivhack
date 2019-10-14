package com.algorand.cdmvalidators;

import com.algorand.exceptions.ExecutionException;
import com.google.common.collect.MoreCollectors;
import org.isda.cdm.Event;
import org.isda.cdm.Execution;
import org.isda.cdm.PartyRole;
import org.isda.cdm.PartyRoleEnum;

public class ExecutionEventValidator {
    private Event executionEvent;
    private Execution execution;

    public ExecutionEventValidator(Event event) throws IllegalArgumentException
    {
        if(event.getPrimitive().getExecution() == null)
            throw new IllegalArgumentException("The provided event does not contain a primitive of type 'execution'");
        this.executionEvent = event;
        this.execution = executionEvent
                .getPrimitive()
                .getExecution().get(0)
                .getAfter()
                .getExecution();
    }

    public Execution getExecution()
    {
        return execution;
    }

    public ExecutionEventValidator validateParties() throws ExecutionException
    {
        PartyRole executingRole = execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                .collect(MoreCollectors.onlyElement());

        PartyRole counterPartyRole = execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.COUNTERPARTY)
                .collect(MoreCollectors.onlyElement());

        PartyRole clientRole = execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.CLIENT)
                .collect(MoreCollectors.onlyElement());
        return this;
    }

    public ExecutionEventValidator validateEconomics() throws ExecutionException
    {
        return this;
    }

}
