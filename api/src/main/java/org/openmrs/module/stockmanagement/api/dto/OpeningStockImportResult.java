package org.openmrs.module.stockmanagement.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpeningStockImportResult {

    private String status;

    private boolean success = false;
    private boolean hasErrorFile = false;
    private int operationsCreated = 0;
    private int itemsImported = 0;
    private List<String> errors = new ArrayList<>();
    private String uploadSessionId;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isHasErrorFile() {
        return hasErrorFile;
    }

    public void setHasErrorFile(boolean hasErrorFile) {
        this.hasErrorFile = hasErrorFile;
    }

    public int getOperationsCreated() {
        return operationsCreated;
    }

    public void setOperationsCreated(int operationsCreated) {
        this.operationsCreated = operationsCreated;
    }

    public int getItemsImported() {
        return itemsImported;
    }

    public void setItemsImported(int itemsImported) {
        this.itemsImported = itemsImported;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public String getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(String uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }
}