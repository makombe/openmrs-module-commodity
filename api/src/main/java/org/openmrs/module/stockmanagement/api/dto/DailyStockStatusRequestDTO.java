package org.openmrs.module.stockmanagement.api.dto;

import java.util.List;

public class DailyStockStatusRequestDTO {
    private List<DailyStockLineItemDTO> lineItems;

    public List<DailyStockLineItemDTO> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<DailyStockLineItemDTO> lineItems) {
        this.lineItems = lineItems;
    }

}
