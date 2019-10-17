package com.algorand.demo;

import com.algorand.utils.MongoStore;
import com.algorand.utils.MongoUtils;
import com.algorand.utils.ReadAndWrite;
import com.algorand.utils.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import com.mongodb.DB;
import com.rosetta.model.lib.records.Date;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PortfolioReport
{

    public static void main(String [] args) throws Exception, IOException {
        for (String arg : args)
        {
            createReport(arg);
        }
    }

    public static void createReport(String reportFile) throws IOException
    {
        String fileContents = ReadAndWrite.readFile(reportFile);
        JsonNode reportInstructions = new ObjectMapper().readTree(fileContents);
        System.out.println("json tree: " + reportInstructions.toString());
        String clientName = reportInstructions.get("PortfolioInstructions").get("Client").get("account").get("accountName").get("value").asText();
        String dateStr = reportInstructions.get("PortfolioInstructions").get("PortfolioDate").asText();
        ArrayList<Integer> dateParts = new ArrayList<>();
        for(String datePartStr : dateStr.split("-"))
        {
            dateParts.add(Integer.valueOf(datePartStr));
        }
        DateImpl date = new DateImpl(dateParts.get(2), dateParts.get(1), dateParts.get(0));
        Portfolio portfolio = getPortfolio(clientName, date);
    }

    public static Portfolio getPortfolio(String clientName, DateImpl date)
    {
        MongoStore mongoStore = new MongoStore();
        List<Event> events = mongoStore.getEventsByParty(clientName);
        List<Event> allocationEvents = events.stream().filter(event ->
                event.getPrimitive() != null && event.getPrimitive().getAllocation().size() > 0).collect(Collectors.toList());
        BigDecimal quantity = new BigDecimal(0);
        for(Event allocationEvent : allocationEvents)
        {
            for (Trade trade : allocationEvent.getPrimitive().getAllocation().get(0).getAfter().getAllocatedTrade())
            {
                Date tradeDate = trade.getExecution().getTradeDate().getValue();
                if(tradeDate.compareTo(date) <= 0)
                {
                    String clientReference = trade.getExecution()
                            .getPartyRole()
                            .stream()
                            .filter(r -> r.getRole() == PartyRoleEnum.CLIENT)
                            .map(r -> r.getPartyReference().getGlobalReference())
                            .collect(MoreCollectors.onlyElement());
                    User client = User.getUser(clientReference);
                    if (client.name.equals(clientName))
                    {
                        quantity = quantity.add(trade.getExecution().getQuantity().getAmount());
                    }
                }
            }
        }
        Portfolio portfolio = Portfolio.builder()
                .setPortfolioState(PortfolioState.builder()
                        .addPositions(Arrays.asList(Position.builder()
                                .setQuantity(Quantity.builder()
                                        .setAmount(quantity)
                                        .build())
                                .build()))
                        .build())
                .build();
        System.out.println("portfolio: " + portfolio.toString());
        return portfolio;
    }
}
