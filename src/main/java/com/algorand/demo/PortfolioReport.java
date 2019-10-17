package com.algorand.demo;

import com.algorand.utils.MongoStore;
import com.algorand.utils.ReadAndWrite;
import com.algorand.utils.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.isda.cdm.*;
import org.isda.cdm.metafields.FieldWithMetaString;
import org.isda.cdm.metafields.ReferenceWithMetaParty;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortfolioReport
{

    public static void main(String [] args) throws Exception {
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
        String[] datePartStrs = dateStr.split("-");
        Integer[] dateParts = new Integer[]{Integer.valueOf(datePartStrs[0]), Integer.valueOf(datePartStrs[1]), Integer.valueOf(datePartStrs[2])};
        LocalDate reportDateExecution = LocalDate.of(dateParts[0], dateParts[1], dateParts[2]);
        LocalDate reportDateSettlement = LocalDate.of(dateParts[0], dateParts[1], dateParts[2]-1); // for now, all trades settle one day after the trade date
        Portfolio executionPortfolio = getPortfolio(clientName, reportDateExecution);
        Portfolio settlementPortfolio = getPortfolio(clientName, reportDateSettlement);
        System.out.println("execution portfolio: " + executionPortfolio.toString());
        System.out.println("settlement portfolio: " + settlementPortfolio.toString());

        createPortfolioReport(executionPortfolio, reportFile);
    }

    public static Portfolio getPortfolio(String clientName, LocalDate portfolioDate)
    {
        MongoStore mongoStore = new MongoStore();
        List<Event> events = mongoStore.getEventsByParty(clientName);
        List<Event> allocationEvents = new ArrayList<>();
        for(Event event : events)
        {
            PrimitiveEvent primitive = event.getPrimitive();
            if(primitive != null)
            {
                List<AllocationPrimitive> allocationPrimitives = primitive.getAllocation();
                if(allocationPrimitives != null)
                {
                    if (primitive.getAllocation().size() > 0)
                    {
                        allocationEvents.add(event);
                    }
                }
            }
        }
        HashMap<Product, BigDecimal> quantitiesByProduct = new HashMap<>();
        for(Event allocationEvent : allocationEvents)
        {
            for (Trade trade : allocationEvent.getPrimitive().getAllocation().get(0).getAfter().getAllocatedTrade())
            {
                LocalDate tradeDate = trade.getExecution().getTradeDate().getValue().toLocalDate();
                if(tradeDate.compareTo(portfolioDate) <= 0)
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
                        Product product = trade.getExecution().getProduct();
                        if(quantitiesByProduct.containsKey(product))
                        {
                            quantitiesByProduct.put(product, quantitiesByProduct.get(product).add(trade.getExecution().getQuantity().getAmount()));
                        } else
                        {
                            quantitiesByProduct.put(product, trade.getExecution().getQuantity().getAmount());
                        }
                    }
                }
            }
        }

        List<Position> positions = new ArrayList<>();
        for(Map.Entry<Product, BigDecimal> entry : quantitiesByProduct.entrySet())
        {
            positions.add(Position.builder()
                    .setProduct(entry.getKey())
                    .setQuantity(Quantity.builder()
                            .setAmount(entry.getValue())
                            .build())
                    .build());
        }
        Portfolio portfolio = Portfolio.builder()
                .setPortfolioState(PortfolioState.builder()
                        .addPositions(positions)
                        .build())
                .setAggregationParameters(AggregationParameters.builder()
                        .addParty(ReferenceWithMetaParty.builder()
                                .setValue(Party.builder()
                                        .setAccount(Account.builder()
                                                .setAccountName(FieldWithMetaString.builder()
                                                        .setValue(clientName)
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
        System.out.println("portfolio: " + portfolio.toString());
        return portfolio;
    }

    public static void createPortfolioReport(Portfolio portfolio, String reportName) throws IOException
    {
        new File("./Files/Reports").mkdir();
        String fileName = String.valueOf(Paths.get(reportName).getFileName());
        try(

            BufferedWriter writer = Files.newBufferedWriter(Paths.get("./Files/Reports/" + fileName + ".csv"));
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withHeader("Client", "Product", "Quantity"));
        )
        {

            for(Position position : portfolio.getPortfolioState().getPositions())
            {
                csvPrinter.printRecord(
                        portfolio.getAggregationParameters().getParty().get(0).getValue().getAccount().getAccountName().getValue(),
                        position.getProduct().getSecurity().getBond().getProductIdentifier().getIdentifier().get(0).getValue(),
                        position.getQuantity().getAmount().toString()
                );
            }
        }
    }
}
