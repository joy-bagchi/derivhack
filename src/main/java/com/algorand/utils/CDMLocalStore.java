package com.algorand.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.isda.cdm.Affirmation;
import org.isda.cdm.Confirmation;
import org.isda.cdm.Event;
import com.algorand.algosdk.algod.client.model.Transaction;

import java.util.List;

public abstract class CDMLocalStore
{
    public abstract void addEventToStore(Event event, String stage) throws JsonProcessingException;
    public abstract void addAffirmationToStore(Affirmation affirmation) throws JsonProcessingException;
    public abstract void addConfirmationToStore(Confirmation confirmation) throws JsonProcessingException;
    public abstract Event getEventFromStore(String globalKey);
    public abstract void addAlgorandTransactionToStore(String globalKey, Transaction transaction, User sender, User receiver, String stage);
    public abstract void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, User sender, List<User> receivers, String stage);
    public abstract void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, List<User> senders, List<User> receivers, String stage);

    public static String getGlobalKey(Event event)
    {
        return event.getMeta().getGlobalKey();
    }
}


