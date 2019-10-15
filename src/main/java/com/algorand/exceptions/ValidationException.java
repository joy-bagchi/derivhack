package com.algorand.exceptions;

import com.google.common.collect.Lists;
import org.isda.cdm.Event;

import java.util.List;

public class ValidationException extends Exception{
    private Event underlyingEvent;
    private List<String> exceptionCollection = Lists.newArrayList();

    public ValidationException(String cause, Event event)
    {
        this.underlyingEvent = event;
        exceptionCollection.add(cause);
    }

    public ValidationException(List<String> causes, Event event)
    {
        this.underlyingEvent = event;
        exceptionCollection.addAll(causes);
    }

    public Event getUnderlyingEvent()
    {
        return underlyingEvent;
    }

    public List<String> getExceptionCollection()
    {
        return exceptionCollection;
    }
}
