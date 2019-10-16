package com.algorand.demo;

import com.algorand.utils.MongoStore;

public class RunLifecycle
{
    public static void runLifecycle() throws Exception
    {
        MongoStore.dropDatabase();
        CommitExecution.main(new String[] {"./Files/UC1_block_execute_BT1.json"});
        CommitAllocation.main(new String[] {"./Files/UC2_allocation_execution_AT1.json"});
    }

    public static void main(String[] args) throws Exception
    {
        runLifecycle();
    }
}
