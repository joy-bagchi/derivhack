package com.algorand.cdmvalidators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.rosetta.model.lib.records.Date;
import com.rosetta.model.lib.records.DateImpl;
import org.isda.cdm.*;
import org.isda.cdm.metafields.FieldWithMetaDate;
import org.isda.cdm.metafields.FieldWithMetaString;
import org.isda.cdm.metafields.MetaFields;
import org.isda.cdm.metafields.ReferenceWithMetaParty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ValidatedTranferPrimitive extends BaseEventValidator{

    public ValidatedTranferPrimitive(Event event) {
        super(event);
    }

    public TransferPrimitive getTransfer()
    {
        return event.getPrimitive().getTransfer().get(0);
    }

    public List<TransferPrimitive> getAllTransfers()
    {
        return event.getPrimitive().getTransfer();
    }

    public static void main(String[] args) throws JsonProcessingException {
        Party p1 = Party.builder()
                .addPartyId(FieldWithMetaString.builder()
                        .setValue("Client1_ID#2_CAOIBGBYU6QPR")
                        .build())
                .setAccount(Account.builder()
                        .setAccountName(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#2")
                                .build())
                        .setAccountNumber(FieldWithMetaString.builder()
                                .setValue("Client1_ACT#2_BJKXFTGW4BFPY")
                                .build())
                        .setMeta(MetaFields.builder()
                                .setGlobalKey("cHT7po5Q+p3hO3sxj1+ACvyGkYYWIBaA2PpVaRO/dvs=")
                                .build())
                        .build())
                .setMeta(MetaFields.builder()
                        .setGlobalKey("xIBwSYskqfYuo710y1XWHFH1u51bAcAdAnM4LcNheBg=")
                        .setExternalKey("Client1_ID#2_CAOIBGBYU6QPR")
                        .build())
                .setName(FieldWithMetaString.builder()
                        .setValue("Client1")
                        .build())
                .build();

        Party p2 = Party.builder()
                .addPartyId(FieldWithMetaString.builder()
                        .setValue("Broker1_ID#0_4NGIDYZJ4ZBDX")
                        .build())
                .setAccount(Account.builder()
                        .setAccountName(FieldWithMetaString.builder()
                                .setValue("Broker1_ACT#0")
                                .build())
                        .setAccountNumber(FieldWithMetaString.builder()
                                .setValue("Broker1_ACT#0_WWVA12ZJ21IW2")
                                .build())
                        .setMeta(MetaFields.builder()
                                .setGlobalKey("xvRB/LCSyPGS736BhYfTkW1AH56H1bzHHXbqZJepZ0w=")
                                .build())
                        .build())
                .setMeta(MetaFields.builder()
                        .setGlobalKey("3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")
                        .setExternalKey("Broker1_ID#0_4NGIDYZJ4ZBDX")
                        .build())
                .setName(FieldWithMetaString.builder()
                        .setValue("Broker1")
                        .build())
                .build();

        Event event = Event.builder()
                .setAction(ActionEnum.NEW)
                .setEventDate(DateImpl.of(2019,10,18))
                .addEventIdentifier(Identifier.builder()
                        .addAssignedIdentifier(AssignedIdentifier.builder()
                                .setIdentifier(FieldWithMetaString.builder()
                                        .setValue("payment-1")
                                        .build())
                                .setVersion(1)
                                .build())
                        .setIssuerReference(ReferenceWithMetaParty.builder()
                                .setGlobalReference("xIBwSYskqfYuo710y1XWHFH1u51bAcAdAnM4LcNheBg=")
                                .setExternalReference("Client1_ID#2_CAOIBGBYU6QPR")
                                .build())
                        .build())
                .setMessageInformation(MessageInformation.builder()
                        .setMessageId(FieldWithMetaString.builder()
                                .setValue("12345")
                                .setMeta(MetaFields.builder()
                                        .setScheme("http://www.party1.com/message-id")
                                        .build())
                                .build())
                        .build())
                .addParty(p1)
                .addParty(p2)
                .setPrimitive(PrimitiveEvent.builder()
                        .addTransfer(TransferPrimitive.builder()
                                .addSecurityTransfer(SecurityTransferComponent.builder()
                                        .setTransferorTransferee(TransferorTransferee.builder()
                                                .setTransferorPartyReference(ReferenceWithMetaParty.builder()
                                                        .setGlobalReference("xIBwSYskqfYuo710y1XWHFH1u51bAcAdAnM4LcNheBg=")
                                                        .build())
                                                .setTransfereePartyReference(ReferenceWithMetaParty.builder()
                                                        .setGlobalReference("3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")
                                                        .build())
                                                .build())
                                        .setSecurity(Security.builder()
                                                .setBond(Bond.builder()
                                                        .setProductIdentifier(ProductIdentifier.builder()
                                                                .addIdentifier(FieldWithMetaString.builder()
                                                                        .setValue("DH0371475458")
                                                                        .build())
                                                                .setSource(ProductIdSourceEnum.CUSIP)
                                                                .build())
                                                        .build())
                                                .build())
                                        .setQuantity(BigDecimal.valueOf(480000.0))
                                        .build())
                                .setSettlementDate(AdjustableOrAdjustedOrRelativeDate.builder()
                                        .setAdjustedDate(FieldWithMetaDate.builder()
                                                .setValue(DateImpl.of(2019,10,18))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        ValidatedTranferPrimitive validatedTranferPrimitive = new ValidatedTranferPrimitive(event);

        System.out.println(validatedTranferPrimitive.serializeEvent());
    }
}
