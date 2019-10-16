package com.algorand.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.isda.cdm.Event;
import com.algorand.algosdk.algod.client.model.Transaction;

import java.util.List;

public abstract class CDMLocalStore
{
    public abstract void addEventToStore(Event event) throws JsonProcessingException;
    public abstract Event getEventFromStore(String globalKey);
    public abstract void addAlgorandTransactionToStore(String globalKey, Transaction transaction, User sender, User receiver, String stage);
    public abstract void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, User sender, List<User> receivers, String stage);
    public abstract void addAlgorandTransactionsToStore(String globalKey, List<Transaction> transactions, List<User> senders, List<User> receivers, String stage);

    public static String getGlobalKey(Event event)
    {
        return event.getEventIdentifier().get(0).getMeta().getGlobalKey();
    }
}


