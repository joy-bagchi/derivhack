package com.algorand.demo;

import com.algorand.utils.MongoStore;

public class RunLifecycle
{
    public static void runLifecycle() throws Exception
    {
        MongoStore.dropDatabase();
        CommitExecution.main(new String[] {"./Files/UC1_Block_Trade_BT1.json"});
        CommitAllocation.main(new String[] {"./Files/UC2_Allocation_Trade_AT1.json"});
        CommitAffirmation.main(new String[] {"./Files/UC2_Allocation_Trade_AT1.json"});
        CommitConfirmation.main(new String[] {"./Files/UC2_Allocation_Trade_AT1.json"});
        CommitSettlementEvent.main(new String[] {"./Files/UC2_Allocation_Trade_AT1.json"});
        PortfolioReport.main(new String[] {"./Files/UC6_Portfolio_Instructions_20191016.json"});
    }

    public static void main(String[] args) throws Exception
    {
        runLifecycle();
    }
}
