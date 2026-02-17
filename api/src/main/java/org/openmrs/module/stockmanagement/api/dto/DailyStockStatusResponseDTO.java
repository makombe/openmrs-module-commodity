package org.openmrs.module.stockmanagement.api.dto;

import java.util.Date;
import java.util.List;

public class DailyStockStatusResponseDTO {
    private String status;
    private String message;
    private Date reportDate;
    private List<DailyStockLineItemDTO> lineItems;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }

    public List<DailyStockLineItemDTO> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<DailyStockLineItemDTO> lineItems) {
        this.lineItems = lineItems;
    }

}
