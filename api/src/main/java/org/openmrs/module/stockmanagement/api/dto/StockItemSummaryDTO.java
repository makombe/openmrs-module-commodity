
package org.openmrs.module.stockmanagement.api.dto;

import java.math.BigDecimal;

public class StockItemSummaryDTO {

    private Integer stockItemId;
    private BigDecimal currentBalance; // stock on hand as of referenceDate
    private BigDecimal beginningBalance; // stock at start of period (or lifetime)
    private BigDecimal dispensed; // total issued in period (positive)
    private BigDecimal received; // total received in period (positive)

    public Integer getStockItemId() {
        return stockItemId;
    }

    public void setStockItemId(Integer id) {
        this.stockItemId = id;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal val) {
        this.currentBalance = val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getBeginningBalance() {
        return beginningBalance;
    }

    public void setBeginningBalance(BigDecimal val) {
        this.beginningBalance = val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getDispensed() {
        return dispensed;
    }

    public void setDispensed(BigDecimal val) {
        this.dispensed = val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getReceived() {
        return received;
    }

    public void setReceived(BigDecimal val) {
        this.received = val != null ? val : BigDecimal.ZERO;
    }

}