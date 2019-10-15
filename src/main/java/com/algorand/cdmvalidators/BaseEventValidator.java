package com.algorand.cdmvalidators;

import com.algorand.exceptions.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import org.isda.cdm.Event;
import org.isda.cdm.PartyRole;
import org.isda.cdm.PartyRoleEnum;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public abstract class BaseEventValidator {
    protected Event event;
    private List<String> exceptions = Lists.newArrayList();
    private boolean isValidated = false;
    private ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();

    public BaseEventValidator(Event event)
    {
        this.event = event;
    }

    public String  serializeEvent() throws JsonProcessingException {
        return rosettaObjectMapper.writeValueAsString(event);
    }


    public void validate(Predicate<Event> eventValidationPredicate) throws ValidationException {
        if(!isValidated)
            isValidated = eventValidationPredicate.test(this.event);
        if(!isValidated) {
            List<String> exceptions = getExceptions();
            ValidationException ex = new ValidationException(exceptions, event);
            clearExceptions();
            throw ex;
        }
    }

    public void addException(String exception)
    {
        exceptions.add(exception);
    }

    public boolean hasException()
    {
        return exceptions.size() != 0;
    }

    public List<String> getExceptions()
    {
        return Collections.unmodifiableList(exceptions);
    }

    protected void clearExceptions()
    {
        exceptions.clear();
    }

}
