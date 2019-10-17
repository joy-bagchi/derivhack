package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.google.common.collect.MoreCollectors;
import com.rosetta.model.lib.records.Date;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ValidatedExecutionEvent extends BaseEventValidator {

    private Execution execution;
    private Predicate<Event> executionEventPredicate;

    public ValidatedExecutionEvent(Event event, Execution execution)
    {
        super(event);
        this.execution = execution;
        executionEventPredicate = e -> {return true;};
    }

    public ValidatedExecutionEvent(Event event) throws ValidationException {
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
        validate(executionEventPredicate);
        return this.execution;
    }

    public Party getClient() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForClient().getPartyReference().getGlobalReference();
        return event.getParty().stream()
                .filter(p -> p.getMeta().getGlobalKey().equals(globalReference))
                .collect(MoreCollectors.onlyElement());
    }

    public Party getExecutingEntity() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForExecutingEntity().getPartyReference().getGlobalReference();
        return event.getParty().stream()
                .filter(p -> p.getMeta().getGlobalKey().equals(globalReference))
                .collect(MoreCollectors.onlyElement());
    }

    public Party getCounterparty() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForCounterparty().getPartyReference().getGlobalReference();
        return event.getParty().stream()
                .filter(p -> p.getMeta().getGlobalKey().equals(globalReference))
                .collect(MoreCollectors.onlyElement());
    }


    public PartyRole getSideForClient() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForClient().getPartyReference().getGlobalReference();
        return findSideByPartyGlobalReference(globalReference);
    }

    public PartyRole getSideForExecutingEntity() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForExecutingEntity().getPartyReference().getGlobalReference();
        return findSideByPartyGlobalReference(globalReference);
    }

    public PartyRole getSideForCounterparty() throws ValidationException
    {
        validate(executionEventPredicate);
        String globalReference = getPartyRoleForCounterparty().getPartyReference().getGlobalReference();
        return findSideByPartyGlobalReference(globalReference);
    }

    public BigDecimal getAccruedInterest() throws ValidationException {
        validate(executionEventPredicate);
        return execution.getPrice().getAccruedInterest();
    }

    public ActualPrice getGrossPrice() throws ValidationException {
        validate(executionEventPredicate);
        return execution.getPrice().getGrossPrice();
    }

    public ActualPrice getNetPrice() throws ValidationException {
        validate(executionEventPredicate);
        return execution.getPrice().getNetPrice();
    }

    public Product getProduct() throws ValidationException {
        validate(executionEventPredicate);
        return execution.getProduct();
    }

    public ValidatedExecutionEvent validateParties()
    {
        executionEventPredicate = executionEventPredicate
                .and(this::validateExactlyThreeParties);
        return this;
    }

    // Validations available publicly.
    public ValidatedExecutionEvent validatePartyRoles()
    {
        try {
            //Check:
            // 1. there are exactly 3 parties
            // 2. Exactly 1 party exists for each role  client,  executing entity and  counterparty.
            // 3. Client and Executing Entity are on the same side of the trade.
            // 4. Executing Entity and Counterparty are on the opposite sides of the trade
            executionEventPredicate = executionEventPredicate
                    .and(this::validateExactlyOneClient)
                    .and(this::validateExactlyOneCounterparty)
                    .and(this::validateExactlyOneExecutingEntity)
                    .and(this::validateClientHasSameSideAsExecutingEntity)
                    .and(this::validateExecutingEntityHasOppositeSideAsCounterparty);
        }
        catch(IllegalArgumentException | NoSuchElementException ex)
        {
            addException(ex.getMessage());
        }
        return this;
    }

    public ValidatedExecutionEvent validateEconomics()
    {
        executionEventPredicate = executionEventPredicate
                .and(this::validateTradeDateNotInFuture);
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
        if (findSideByPartyGlobalReference(clientGlobalReference).getRole() ==
                findSideByPartyGlobalReference(executingEntityGlobalReference).getRole()
        ) return true;

        addException("Client Role and Executing Entity should be on same side, but they are on opposite sides");
        return false;
    }

    private boolean validateExecutingEntityHasOppositeSideAsCounterparty(Event event)
    {
        String executingEntityGlobalReference = getPartyRoleForExecutingEntity().getPartyReference().getGlobalReference();
        String counterpartyGlobalReference = getPartyRoleForCounterparty().getPartyReference().getGlobalReference();

        if (findSideByPartyGlobalReference(executingEntityGlobalReference).getRole() !=
                findSideByPartyGlobalReference(counterpartyGlobalReference).getRole()
        ) return true;

        addException("Executing Entity and Counterparty should be opposite sides, but they are on same side");
        return false;
    }

    private boolean validateTradeDateNotInFuture(Event event) {
        Date tradeDate = execution.getTradeDate().getValue();
        LocalDate today = LocalDate.now();
        if(tradeDate.toLocalDate().isAfter(today)) {
            addException("Trade date cannot be in the future ");
            return false;
        }
        return true;
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

    private PartyRole findSideByPartyGlobalReference(String globalReference)
    {
        return  execution.getPartyRole()
                .stream()
                .filter(r -> r.getRole() == PartyRoleEnum.BUYER || r.getRole() == PartyRoleEnum.SELLER)
                .filter(r -> r.getPartyReference().getGlobalReference().equals(globalReference))
                .collect(MoreCollectors.onlyElement());
    }
    //-----------------------------------End Utility methods-------------------------------
}
