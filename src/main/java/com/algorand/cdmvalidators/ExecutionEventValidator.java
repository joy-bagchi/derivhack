package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.google.common.collect.MoreCollectors;
import org.isda.cdm.Event;
import org.isda.cdm.Execution;
import org.isda.cdm.PartyRole;
import org.isda.cdm.PartyRoleEnum;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class ExecutionEventValidator extends BaseEventValidator {

    private Execution execution;
    private Predicate<Event> executionEventPredicate;


    public ExecutionEventValidator(Event event) throws IllegalArgumentException, ValidationException {
        super(event);
        if(event.getPrimitive().getExecution() == null)
            throw new ValidationException("The provided event does not contain a primitive of type 'execution'", event);
        this.execution = event
                .getPrimitive()
                .getExecution().get(0)
                .getAfter()
                .getExecution();
        executionEventPredicate = e -> {return true;};
    }

    public Execution getExecution() throws ValidationException
    {
        if(!validate(executionEventPredicate) && hasException()) {
            List<String> exceptions = getExceptions();
            ValidationException ex = new ValidationException(exceptions, event);
            clearExceptions();
            throw ex;
        }
        return this.execution;
    }

    public ExecutionEventValidator validateParties()
    {
        try {
            //Check:
            // 1. there are exactly 3 parties
            // 2. Exactly 1 party exists for each role  client,  executing broker and  counterparty.
            // 3. Client and Executing broker are on the same side of the trade.
            executionEventPredicate = executionEventPredicate
                    .and(this::validateExactlyThreeParties)
                    .and(this::validateExactlyThreeParties)
                    .and(this::validateClientHasSameRoleAsExecutingEntity)
                    .and(this::validateClientHasOppositeRoleAsCounterparty)
                    .and(this::validateExecutingBrokerHasOppositeRoleAsCounterparty);
        }
        catch(IllegalArgumentException | NoSuchElementException ex)
        {
            addException(ex.getMessage());
        }
        return this;
    }

    public ExecutionEventValidator validateEconomics()
    {
        return this;
    }

    //----------------------------------- Validation rules---------------------------

    private boolean validateExactlyThreeParties(Event event)
    {
        if(event.getParty().size() != 3)
        {
            addException("Expect exactly 3 unique parties and found " + event.getParty().size());
            return false;
        }
        return true;
    }

    private boolean validateClientHasSameRoleAsExecutingEntity(Event event)
    {
        if (getPartyRole(PartyRoleEnum.CLIENT).getRole() !=
                getPartyRole(PartyRoleEnum.EXECUTING_ENTITY).getRole()) {
            addException("Client Role and Executing Broker roles should be same, but they are different");
            return false;
        }
        return true;
    }

    private boolean validateClientHasOppositeRoleAsCounterparty(Event event)
    {
        if (getPartyRole(PartyRoleEnum.CLIENT).getRole() ==
                getPartyRole(PartyRoleEnum.COUNTERPARTY).getRole()) {
            addException("Client Role and Counter roles should be opposite, but they are on same side");
            return false;
        }
        return true;
    }

    private boolean validateExecutingBrokerHasOppositeRoleAsCounterparty(Event event)
    {
        if (getPartyRole(PartyRoleEnum.EXECUTING_ENTITY).getRole() ==
                getPartyRole(PartyRoleEnum.COUNTERPARTY).getRole()) {
            addException("Client Role and Counter roles should be opposite, but they are on same side");
            return false;
        }
        return true;
    }
    //-----------------------------------End Private Validation primitive-------------------------



    protected  PartyRole getPartyRole(PartyRoleEnum role)
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == role)
                .collect(MoreCollectors.onlyElement());
    }
}
