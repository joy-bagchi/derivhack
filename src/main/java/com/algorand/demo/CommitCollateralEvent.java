package com.algorand.demo;

import com.algorand.utils.MongoUtils;
import com.algorand.utils.UniqueIdentifierGenerator;
import com.algorand.utils.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mongodb.DB;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;
import org.isda.cdm.metafields.FieldWithMetaString;
import org.isda.cdm.metafields.MetaFields;
import org.isda.cdm.metafields.ReferenceWithMetaParty;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CommitCollateralEvent {

    Event.EventBuilder collateralEventBuilder;
    static ObjectMapper jsonmapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
        Event collateralEvent = new CommitCollateralEvent().createCollateralEvent(null);
        System.out.println(rosettaObjectMapper.writeValueAsString(collateralEvent));
    }

    public Event createCollateralEvent(JsonNode root)
    {
        collateralEventBuilder = Event.builder();

        collateralEventBuilder.setAction(ActionEnum.NEW);
        //Add any new parties to the database, and commit the event to their own private databases
        List<Party> parties = Lists.newArrayList();
        parties.add(Party.builder()
                .addPartyId(FieldWithMetaString.builder()
                        .setValue("Client1_ID#0_NH90YY6QYVYHM")
                        .build())
                .setName(FieldWithMetaString.builder()
                        .setValue("Client1")
                        .build())
                .setAccount(Account.builder()
                        .setAccountName(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#0")
                                .build())
                        .setAccountNumber(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#0_NQQVRTLFIT4HZ")
                                .build())
                        .setMeta(MetaFields.builder()
                                .setGlobalKey("BRsIov0pBnhQgjuC37YUVLXz5yno3Xkhu7yCZF+3uxY=")
                                .build())
                        .build())
                .setMeta(MetaFields.builder()
                        .setGlobalKey("GkATYHcJ0uWdD0klwf0/RCreumcOlWEKDvfHzLfNZiA=")
                        .setExternalKey("Client1_ACT#0_NQQVRTLFIT4HZ")
                        .build())
                .build());

        parties.add(Party.builder()
                .addPartyId(FieldWithMetaString.builder()
                        .setValue("Client1_ID#8_EX8XKP4E2WVIE")
                        .build())
                .setName(FieldWithMetaString.builder()
                        .setValue("Client1")
                        .build())
                .setAccount(Account.builder()
                        .setAccountName(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#8")
                                .build())
                        .setAccountNumber(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#8_8SI1G6PUNY9DJ")
                                .build())
                        .setMeta(MetaFields.builder()
                                .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                                .build())
                        .build())
                .setMeta(MetaFields.builder()
                        .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                        .setExternalKey("Client1_ACT#0_NQQVRTLFIT4HZ")
                        .build())
                .build());
        User user;
        DB mongoDB = MongoUtils.getDatabase("users");
        parties.parallelStream()
                .map(party -> User.getOrCreateUser(party, mongoDB))
                .collect(Collectors.toList());

        collateralEventBuilder.addParty(parties);
        LocalDate today = LocalDate.now();
        collateralEventBuilder.setEventDate(DateImpl.of(today.getYear(),
                today.getMonth().getValue(),
                today.getDayOfMonth()));

        //Get the allocated trades
        TransferPrimitive primitive = createTransferForUser(parties);
        collateralEventBuilder.setPrimitive(PrimitiveEvent.builder()
                .addTransfer(primitive).build());

        collateralEventBuilder.setMeta(MetaFields.builder()
                .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                .build());

        collateralEventBuilder.addEventIdentifier(Identifier.builder()
                .setIssuerReference(ReferenceWithMetaParty.builder()
                        .setGlobalReference(parties.get(0).getMeta().getGlobalKey())
                        .build())
                .addAssignedIdentifier(AssignedIdentifier.builder()
                        .setVersion(1)
                        .setIdentifier(FieldWithMetaString.builder()
                                .setValue(UniqueIdentifierGenerator.randomAlphaNumeric(13))
                                .build())
                        .build())
                .setMeta(MetaFields.builder()
                        .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                        .build())
                .build());

        collateralEventBuilder.addTimestamp(EventTimestamp.builder()
                .setDateTime(ZonedDateTime.now())
                .setQualification(EventTimestampQualificationEnum.EVENT_CREATION_DATE_TIME)
                .build());


        return collateralEventBuilder.build();
    }


    private TransferPrimitive createTransferForUser(List<Party> parties)
    {
        TransferPrimitive tp = TransferPrimitive.builder()
                .setIdentifier(FieldWithMetaString.builder()
                        .setValue(UniqueIdentifierGenerator.randomAlphaNumeric(13))
                        .setMeta(MetaFields.builder()
                                .setGlobalKey(UniqueIdentifierGenerator.randomHash())
                                .build())
                        .build())
                .setStatus(TransferStatusEnum.SETTLED)
                .setSettlementType(TransferSettlementEnum.NOT_CENTRAL_SETTLEMENT)
                .addSecurityTransfer(SecurityTransferComponent.builder()
                        .setQuantity(new BigDecimal(150000.00))
                        .setSecurity(Security.builder()
                                .setBond(Bond.builder()
                                        .setProductIdentifier(ProductIdentifier.builder()
                                                .setSource(ProductIdSourceEnum.CUSIP)
                                                .addIdentifier(FieldWithMetaString.builder()
                                                        .setValue("DH0371475458")
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .setTransferorTransferee(TransferorTransferee.builder()
                                .setTransferorPartyReference(ReferenceWithMetaParty.builder()
                                        .setGlobalReference(parties.get(0).getMeta().getGlobalKey())
                                        .build())
                                .setTransfereePartyReference(ReferenceWithMetaParty.builder()
                                        .setGlobalReference(parties.get(0).getMeta().getGlobalKey())
                                        .build())
                                .build())
                        .build())
                .build();
        return tp;
    }

}
