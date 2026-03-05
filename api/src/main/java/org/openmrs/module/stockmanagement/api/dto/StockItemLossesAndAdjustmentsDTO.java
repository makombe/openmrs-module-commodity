package org.openmrs.module.stockmanagement.api.dto;

import java.math.BigDecimal;

public class StockItemLossesAndAdjustmentsDTO {
    private BigDecimal quantity;
    private String typeName;
    private String typeCode;
    private int stockItemId;
	
	private String stockItemUuid;

    public BigDecimal getQuantity() {
        return quantity;
    }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    public String getTypeName() {
        return typeName;
    }
    public void setTypeName(String typeName) {
        this.typeName = typeName;
    
    }
    public String getTypeCode() {
        return typeCode;
    }
    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }
    public int getStockItemId() {
        return stockItemId;
    }
    public void setStockItemId(int stockItemId) {
        this.stockItemId = stockItemId; 
    }
    public String getStockItemUuid() {
        return stockItemUuid;
    }
    public void setStockItemUuid(String stockItemUuid) {
        this.stockItemUuid = stockItemUuid;
    }
    
    
}
