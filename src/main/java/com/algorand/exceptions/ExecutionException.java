package com.algorand.exceptions;

public class ExecutionException extends Exception{
    public ExecutionException(){}
    public ExecutionException(String cause)
    {
        super(cause);
    }
}
