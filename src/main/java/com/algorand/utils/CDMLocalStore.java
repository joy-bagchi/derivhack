package com.algorand.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.isda.cdm.Affirmation;
import org.isda.cdm.Confirmation;
import org.isda.cdm.Event;

public interface CDMLocalStore
{
    public void addEventToStore(Event event) throws JsonProcessingException;
    public Event getEventFromStore(String globalKey);
    public void addAffirmToStore(Affirmation event) throws JsonProcessingException;
    public Affirmation getAffirmFromStore(String globalKey);
    public void addConfirmToStore(Confirmation event) throws JsonProcessingException;
    public Confirmation getConfirmFromStore(String globalKey);
}


