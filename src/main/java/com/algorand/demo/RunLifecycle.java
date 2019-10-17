package com.algorand.demo;

import com.algorand.utils.MongoStore;

public class RunLifecycle
{
    public static void runLifecycle() throws Exception
    {
        String[] executionFiles = new String[]{"./Files/UC1_Block_Trade_BT1.json", "./Files/UC1_Block_Trade_BT5.json", "./Files/UC1_Block_Trade_BT8.json"};
        String[] allocationFiles = new String[]{"./Files/UC2_Allocation_Trade_AT1.json", "./Files/UC2_Allocation_Trade_AT5.json", "./Files/UC2_Allocation_Trade_AT8.json"};
        String[] affirmationFiles = allocationFiles;
        String[] confirmationFiles = allocationFiles;
        String[] settlementFiles = allocationFiles;
        String[] reportFiles = new String[]{"./Files/UC6_Portfolio_Instructions_20191016.json", "./Files/UC6_Portfolio_Instructions_20191017.json"};

        MongoStore.dropDatabase();
        CommitExecution.main(executionFiles);
        CommitAllocation.main(allocationFiles);
        CommitAffirmation.main(affirmationFiles);
        CommitConfirmation.main(confirmationFiles);
        CommitSettlementEvent.main(settlementFiles);
        PortfolioReport.main(reportFiles);
    }

    public static void main(String[] args) throws Exception
    {
        runLifecycle();
    }
}
