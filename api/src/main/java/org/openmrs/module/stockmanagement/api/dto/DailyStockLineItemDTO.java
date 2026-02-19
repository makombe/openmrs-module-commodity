package org.openmrs.module.stockmanagement.api.dto;

import java.math.BigDecimal;
import java.util.Date;

public class DailyStockLineItemDTO {
    private String uuid;
    private String productCode;
    private BigDecimal stockOnHand = BigDecimal.ZERO; // ← default to ZERO
    private BigDecimal quantityReceived = BigDecimal.ZERO; // ← default to ZERO
    private BigDecimal quantityDispensed = BigDecimal.ZERO; // ← default to ZERO
    private String notes;
    private Date reportDate;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public BigDecimal getStockOnHand() {
        return stockOnHand;
    }

    public void setStockOnHand(BigDecimal stockOnHand) {
        this.stockOnHand = stockOnHand;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(BigDecimal quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public BigDecimal getQuantityDispensed() {
        return quantityDispensed;
    }

    public void setQuantityDispensed(BigDecimal quantityDispensed) {
        this.quantityDispensed = quantityDispensed;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }
}