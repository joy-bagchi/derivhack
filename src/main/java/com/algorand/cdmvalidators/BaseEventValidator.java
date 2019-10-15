package com.algorand.cdmvalidators;

import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
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

    public BaseEventValidator(Event event)
    {
        this.event = event;
    }

    public boolean validate(Predicate<Event> eventValidationPredicate)
    {
        return eventValidationPredicate.test(this.event);
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
