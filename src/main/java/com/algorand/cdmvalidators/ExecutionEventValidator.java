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
import java.util.stream.Collectors;

public class ExecutionEventValidator extends BaseEventValidator {

    private Execution execution;
    private Predicate<Event> executionEventPredicate;


    public ExecutionEventValidator(Event event) throws ValidationException {
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
                    .and(this::validateExactlyOneClient)
                    .and(this::validateExactlyOneCounterparty)
                    .and(this::validateExactlyOneExecutingEntity)
                    .and(this::validateClientHasSameSideAsExecutingEntity)
                    .and(this::validateClientHasOppositeSideAsCounterparty)
                    .and(this::validateExecutingEntityHasOppositeSideAsCounterparty);
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

    //----------------------------------- Validation Rules Implementation---------------------------

    private boolean validateExactlyThreeParties(Event event)
    {
        if(event.getParty().size() != 3)
        {
            addException("Expect exactly 3 unique parties, and found " + event.getParty().size());
            return false;
        }
        return true;
    }

    private boolean validateExactlyOneClient(Event event)
    {
        if(getPartyRoleByRoleType(PartyRoleEnum.CLIENT).size() != 1)
        {
            addException("Expect exactly 1 Client, and found " + getPartyRoleByRoleType(PartyRoleEnum.CLIENT).size());
            return false;
        }
        return true;
    }

    private boolean validateExactlyOneExecutingEntity(Event event)
    {
        if(getPartyRoleByRoleType(PartyRoleEnum.EXECUTING_ENTITY).size() != 1)
        {
            addException("Expect exactly 1 Executing Entity, and found " + getPartyRoleByRoleType(PartyRoleEnum.EXECUTING_ENTITY).size());
            return false;
        }
        return true;
    }

    private boolean validateExactlyOneCounterparty(Event event)
    {
        if(getPartyRoleByRoleType(PartyRoleEnum.COUNTERPARTY).size() != 1)
        {
            addException("Expect exactly 1 Counterparty, and found " + getPartyRoleByRoleType(PartyRoleEnum.COUNTERPARTY).size());
            return false;
        }
        return true;
    }

    private boolean validateClientHasSameSideAsExecutingEntity(Event event)
    {
        String clientGlobalReference = getPartyRoleForClient().getPartyReference().getGlobalReference();
        String executingEntityGlobalReference = getPartyRoleForExecutingEntity().getPartyReference().getGlobalReference();
        if (findBuyerSellerByPartyGlobalReference(clientGlobalReference).getRole() ==
                findBuyerSellerByPartyGlobalReference(executingEntityGlobalReference).getRole()
        ) return true;

        addException("Client Role and Executing Broker should be on same side, but they are on opposite sides");
        return false;
    }

    private boolean validateClientHasOppositeSideAsCounterparty(Event event)
    {
        String clientGlobalReference = getPartyRoleForClient().getPartyReference().getGlobalReference();
        String counterpartyGlobalReference = getPartyRoleForCounterparty().getPartyReference().getGlobalReference();
        if (findBuyerSellerByPartyGlobalReference(clientGlobalReference).getRole() !=
                findBuyerSellerByPartyGlobalReference(counterpartyGlobalReference).getRole()
        ) return true;

        addException("Client and Counterparty should be on opposite sides, but they are on same side");
        return false;
    }

    private boolean validateExecutingEntityHasOppositeSideAsCounterparty(Event event)
    {
        String executingEntityGlobalReference = getPartyRoleForExecutingEntity().getPartyReference().getGlobalReference();
        String counterpartyGlobalReference = getPartyRoleForCounterparty().getPartyReference().getGlobalReference();

        if (findBuyerSellerByPartyGlobalReference(executingEntityGlobalReference).getRole() !=
                findBuyerSellerByPartyGlobalReference(counterpartyGlobalReference).getRole()
        ) return true;

        addException("Executing Entity and Counterparty should be opposite sides, but they are on same side");
        return false;
    }
    //-----------------------------------End  Validation Rules Implementation-------------------------

    //-----------------------------------Utility methods-------------------------------
    private  List<PartyRole> getPartyRoleByRoleType(PartyRoleEnum role)
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == role)
                .collect(Collectors.toList());
    }

    private  PartyRole getPartyRoleForClient()
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.CLIENT)
                .collect(MoreCollectors.onlyElement());
    }

    private  PartyRole getPartyRoleForExecutingEntity()
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.EXECUTING_ENTITY)
                .collect(MoreCollectors.onlyElement());
    }

    private  PartyRole getPartyRoleForCounterparty()
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.COUNTERPARTY)
                .collect(MoreCollectors.onlyElement());
    }

    private PartyRole findBuyerSellerByPartyGlobalReference(String globalReference)
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.BUYER || r.getRole() == PartyRoleEnum.SELLER)
                .filter(r -> r.getPartyReference().getGlobalReference().equals(globalReference))
                .collect(MoreCollectors.onlyElement());
    }
    //-----------------------------------End Utility methods-------------------------------
}
