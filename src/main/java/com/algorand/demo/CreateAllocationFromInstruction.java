package com.algorand.demo;

import com.algorand.cdmvalidators.ValidatedExecutionEvent;
import com.algorand.exceptions.ValidationException;
import com.algorand.utils.ReadAndWrite;
import com.algorand.utils.UniqueIdentifierGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;
import org.isda.cdm.metafields.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class CreateAllocationFromInstruction {

    Event.EventBuilder allocationEventBuilder = Event.builder();
    public Event createAllocationEvent(Event executionEvent, AllocationInstructions allocationInstructions) throws ValidationException, IOException {
        ValidatedExecutionEvent validatedExecutionEvent = new ValidatedExecutionEvent(executionEvent)
                .validatePartyRoles()
                .validateEconomics()
                .validateParties();

        List<Party> parties = Lists.newArrayList();
        parties.addAll(executionEvent.getParty());

        //ObjectMapper mapper = new ObjectMapper();
        //JsonNode instructions = mapper.readTree(instructionContent);
        //JsonNode allocations =  instructions.get("Allocations");
        /*for (Object item : allocations)
        {
            Party party = Party.builder()
                    .setAccount(Account.builder()
                            .setAccountName(FieldWithMetaString.builder()
                                    .setValue(allocations.get("name").toString())
                                    .build())
                            .build())
                    .build();
        }*/
        LocalDate today = LocalDate.now();
        allocationEventBuilder.setAction(ActionEnum.NEW)
                .setEventDate(DateImpl.of(today.getYear(),
                        today.getMonth().getValue(),
                        today.getDayOfMonth()))
                .setMeta(MetaFields.builder()
                        .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                        .build())
                .addEventIdentifier(Identifier.builder()
                        .setMeta(MetaFields.builder()
                                .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                                .build())
                        .setIssuerReference(ReferenceWithMetaParty.builder()
                                .setGlobalReference(validatedExecutionEvent
                                        .getExecutingEntity()
                                        .getMeta()
                                        .getGlobalKey())
                                .build())
                        .addAssignedIdentifier(AssignedIdentifier.builder()
                                .setIdentifier(FieldWithMetaString.builder()
                                        .setValue(UniqueIdentifierGenerator.randomAlphaNumeric(13))
                                        .build())
                                .setVersion(1)
                                .build())
                        .build())
                .setLineage(Lineage.builder()
                        .addExecutionReference(ReferenceWithMetaExecution.builder()
                                .setGlobalReference(
                                        executionEvent
                                        .getPrimitive()
                                        .getExecution().get(0)
                                        .getAfter()
                                        .getExecution()
                                        .getMeta()
                                        .getGlobalKey()
                                )
                                .build())
                        .addEventReference(ReferenceWithMetaEvent.builder()
                                .setGlobalReference(
                                        executionEvent.getMeta().getGlobalKey()
                                )
                                .build())
                        .build())
                .addParty(parties);

        return allocationEventBuilder.build();
    }

    public static void main(String [] args) throws IOException, ValidationException, ParseException {
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();


        String eventFileName = args[0];
        String eventFileContents = ReadAndWrite.readFile(eventFileName);
        Event event = rosettaObjectMapper.readValue(eventFileContents, Event.class);

        String instructionsFileName = args[1];
        String instructionsFileContents = ReadAndWrite.readFile(instructionsFileName);
        AllocationInstructions instructions = rosettaObjectMapper.readValue(
                instructionsFileContents
                , AllocationInstructions.class);

        Event allccationEvent = new CreateAllocationFromInstruction().createAllocationEvent(event, instructions);

        System.out.println(rosettaObjectMapper.writeValueAsString(allccationEvent));
    }
}
