package com.algorand.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.isda.cdm.Event;

public interface CDMLocalStore
{
    public abstract void addEventToStore(Event event) throws JsonProcessingException;
    public abstract Event getEventFromStore(String globalKey);
}


