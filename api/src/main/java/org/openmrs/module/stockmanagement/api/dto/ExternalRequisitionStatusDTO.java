package org.openmrs.module.stockmanagement.api.dto;

import java.util.Date;

public class ExternalRequisitionStatusDTO {

    private Integer messageId;
    private String message;
    private Integer creator;
    private Integer retired;
    private String status;
    private String source;
    private Date dateCreated;
    private Date dateUpdated;
    private String uuid;
    private String operationNumber;
    private String receiptNumber;
    private String receiptMessage;
    private String deliveryStatus;
    private String podNotificationStatus;

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getCreator() {
        return creator;
    }

    public void setCreator(Integer creator) {
        this.creator = creator;
    }

    public Integer getRetired() {
        return retired;
    }

    public void setRetired(Integer retired) {
        this.retired = retired;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getOperationNumber() {
        return operationNumber;
    }

    public void setOperationNumber(String operationNumber) {
        this.operationNumber = operationNumber; 
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getReceiptMessage() {
        return receiptMessage;
    }

    public void setReceiptMessage(String receiptMessage) {
        this.receiptMessage = receiptMessage;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getPodNotificationStatus() {
        return podNotificationStatus;
    }

    public void setPodNotificationStatus(String podNotificationStatus) {
        this.podNotificationStatus = podNotificationStatus;
    }
}