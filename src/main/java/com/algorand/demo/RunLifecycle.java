package com.algorand.demo;

import com.algorand.utils.MongoStore;

public class RunLifecycle
{
    static String[] executionFiles = new String[]{
            "./Files/UC1_Block_Trade_BT1.json",
//            "./Files/UC1_Block_Trade_BT2.json",
//            "./Files/UC1_Block_Trade_BT3.json",
//            "./Files/UC1_Block_Trade_BT4.json",
            "./Files/UC1_Block_Trade_BT5.json",
//            "./Files/UC1_Block_Trade_BT6.json",
//            "./Files/UC1_Block_Trade_BT7.json",
            "./Files/UC1_Block_Trade_BT8.json",
//            "./Files/UC1_Block_Trade_BT9.json",
//            "./Files/UC1_Block_Trade_BT10.json",
    };
    static String[] allocationFiles = new String[]{
            "./Files/UC2_Allocation_Trade_AT1.json",
//            "./Files/UC2_Allocation_Trade_AT2.json",
//            "./Files/UC2_Allocation_Trade_AT3.json",
//            "./Files/UC2_Allocation_Trade_AT4.json",
            "./Files/UC2_Allocation_Trade_AT5.json",
//            "./Files/UC2_Allocation_Trade_AT6.json",
//            "./Files/UC2_Allocation_Trade_AT7.json",
            "./Files/UC2_Allocation_Trade_AT8.json",
//            "./Files/UC2_Allocation_Trade_AT9.json",
//            "./Files/UC2_Allocation_Trade_AT10.json",
    };
    static String[] affirmationFiles = allocationFiles;
    static String[] confirmationFiles = allocationFiles;
    static String[] settlementFiles = allocationFiles;
    static String[] collateralFiles = new String[]{};
    static String[] reportFiles = new String[]{
            "./Files/UC6_Portfolio_Instructions_20191016.json",
            "./Files/UC6_Portfolio_Instructions_20191017.json"
    };

    public static void runLifecycle() throws Exception
    {
        MongoStore.dropDatabase();
        CommitExecution.main(executionFiles);
        CommitAllocation.main(allocationFiles);
        CommitAffirmation.main(affirmationFiles);
        CommitConfirmation.main(confirmationFiles);
        CommitSettlementEvent.main(settlementFiles);
        CommitCollateralEvent.main(collateralFiles);
        PortfolioReport.main(reportFiles);
    }

    public static void main(String[] args) throws Exception
    {
        runLifecycle();
    }
}
