package org.openmrs.module.stockmanagement.api.dto;

import org.openmrs.module.stockmanagement.api.model.StockItem.ItemType;

import java.util.Date;
import java.util.List;

public class OrderItemSearchFilter {
	
	private Integer id;
	
	private String uuid;
	
	private List<Integer> encounterIds;
	
	private List<String> encounterUuids;
	
	private Date orderDateMin;
	
	private Date orderDateMax;
	
	private List<Integer> orderIds;
	
	private List<String> orderUuids;
	
	private String orderNumber;
	
	private List<Integer> patientIds;

	/**
	 * New canonical item-type filter.
	 * <p>
	 * Takes precedence over the legacy {@link #isDrug} flag when both are present.
	 * Use {@link #resolveEffectiveItemType()} in DAO/service code rather than
	 * reading either field directly.
	 */
	private ItemType itemType;

	/**
	 * Legacy boolean filter kept for backward compatibility.
	 * New code should use {@link #setItemType(ItemType)} instead.
	 *
	 * @deprecated Use {@link #itemType} instead.
	 */
	@Deprecated
	private Boolean isDrug;
	
	private List<Integer> stockItemIds;
	
	private List<String> stockItemUuids;
	
	private List<Integer> drugIds;
	
	private List<String> drugUuids;
	
	private List<String> conceptUuids;
	
	private List<Integer> conceptIds;
	
	private boolean searchEitherDrugOrConceptStockItems = false;
	
	private List<Integer> createdFromLocationIds;
	
	private List<String> createdFromLocationUuids;
	
	private List<String> createdFromPartyUuids;
	
	private List<Integer> fulfilmentLocationIds;
	
	private List<String> fulfilmentLocationUuids;
	
	private List<String> fulfilmentPartyUuids;
	
	private Integer startIndex;
	
	private Integer limit;
	
	private boolean includeVoided = false;

	/**
	 * Returns the canonical item type set on this filter.
	 *
	 * @see #resolveEffectiveItemType() for the value DAO/service code should use.
	 */
	public ItemType getItemType() {
		return itemType;
	}

	/**
	 * Sets the canonical item-type filter and keeps the legacy {@link #isDrug}
	 * flag in sync so that any existing code that still reads {@code isDrug}
	 * behaves correctly.
	 * <p>
	 * Pass {@code null} to remove the type filter (return all types).
	 */
	public void setItemType(ItemType itemType) {
		this.itemType = itemType;
		if (itemType == null) {
			this.isDrug = null;
		} else if (itemType == ItemType.PHARMACEUTICAL) {
			this.isDrug = true;
		} else {
			// NON_PHARMACEUTICAL and LAB_COMMODITY both map to isDrug=false for
			// legacy code; LAB_COMMODITY items have no drug association.
			this.isDrug = false;
		}
	}

	/**
	 * Returns the legacy isDrug flag.
	 *
	 * @deprecated Use {@link #resolveEffectiveItemType()} instead.
	 */
	@Deprecated
	public Boolean getIsDrug() {
		return isDrug;
	}

	/**
	 * Sets the legacy isDrug flag and derives {@link #itemType} from it so the
	 * two representations stay consistent.
	 * <p>
	 * Will never overwrite an existing {@link ItemType#LAB_COMMODITY} value with
	 * {@link ItemType#NON_PHARMACEUTICAL}, preventing accidental reclassification
	 * of lab-commodity filters by code paths that still use this method.
	 *
	 * @deprecated Use {@link #setItemType(ItemType)} instead.
	 */
	@Deprecated
	public void setIsDrug(Boolean isDrug) {
		this.isDrug = isDrug;
		// Guard: don't overwrite an explicitly set LAB_COMMODITY or OTHER with a
		// coerced value — both carry semantic meaning a boolean cannot express.
		if (this.itemType == ItemType.LAB_COMMODITY || this.itemType == ItemType.OTHER) {
			return;
		}
		if (isDrug == null) {
			this.itemType = null;
		} else {
			this.itemType = isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
		}
	}

	/**
	 * Resolves the effective {@link ItemType} to apply in a DAO/service query.
	 * <p>
	 * Resolution priority:
	 * <ol>
	 *   <li>Returns {@link #itemType} if explicitly set.</li>
	 *   <li>Derives the type from the legacy {@link #isDrug} boolean if set.</li>
	 *   <li>Returns {@code null} when neither field is set (no type filter).</li>
	 * </ol>
	 * DAO implementations should call this method rather than reading
	 * {@code isDrug} and {@code itemType} separately.
	 */
	public ItemType resolveEffectiveItemType() {
		if (itemType != null) {
			return itemType;
		}
		if (isDrug != null) {
			return isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
		}
		return null;
	}
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public List<Integer> getOrderIds() {
		return orderIds;
	}
	
	public void setOrderIds(List<Integer> orderIds) {
		this.orderIds = orderIds;
	}
	
	public List<String> getOrderUuids() {
		return orderUuids;
	}
	
	public void setOrderUuids(List<String> orderUuids) {
		this.orderUuids = orderUuids;
	}
	
	public String getOrderNumber() {
		return orderNumber;
	}
	
	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}
	
	public List<Integer> getStockItemIds() {
		return stockItemIds;
	}
	
	public void setStockItemIds(List<Integer> stockItemIds) {
		this.stockItemIds = stockItemIds;
	}
	
	public List<String> getStockItemUuids() {
		return stockItemUuids;
	}
	
	public void setStockItemUuids(List<String> stockItemUuids) {
		this.stockItemUuids = stockItemUuids;
	}
	
	public List<Integer> getDrugIds() {
		return drugIds;
	}
	
	public void setDrugIds(List<Integer> drugIds) {
		this.drugIds = drugIds;
	}
	
	public List<String> getDrugUuids() {
		return drugUuids;
	}
	
	public void setDrugUuids(List<String> drugUuids) {
		this.drugUuids = drugUuids;
	}
	
	public List<String> getConceptUuids() {
		return conceptUuids;
	}
	
	public void setConceptUuids(List<String> conceptUuids) {
		this.conceptUuids = conceptUuids;
	}
	
	public List<Integer> getConceptIds() {
		return conceptIds;
	}
	
	public void setConceptIds(List<Integer> conceptIds) {
		this.conceptIds = conceptIds;
	}
	
	public List<Integer> getCreatedFromLocationIds() {
		return createdFromLocationIds;
	}
	
	public void setCreatedFromLocationIds(List<Integer> createdFromLocationIds) {
		this.createdFromLocationIds = createdFromLocationIds;
	}
	
	public List<String> getCreatedFromLocationUuids() {
		return createdFromLocationUuids;
	}
	
	public void setCreatedFromLocationUuids(List<String> createdFromLocationUuids) {
		this.createdFromLocationUuids = createdFromLocationUuids;
	}
	
	public List<String> getCreatedFromPartyUuids() {
		return createdFromPartyUuids;
	}
	
	public void setCreatedFromPartyUuids(List<String> createdFromPartyUuids) {
		this.createdFromPartyUuids = createdFromPartyUuids;
	}
	
	public List<Integer> getFulfilmentLocationIds() {
		return fulfilmentLocationIds;
	}
	
	public void setFulfilmentLocationIds(List<Integer> fulfilmentLocationIds) {
		this.fulfilmentLocationIds = fulfilmentLocationIds;
	}
	
	public List<String> getFulfilmentLocationUuids() {
		return fulfilmentLocationUuids;
	}
	
	public void setFulfilmentLocationUuids(List<String> fulfilmentLocationUuids) {
		this.fulfilmentLocationUuids = fulfilmentLocationUuids;
	}
	
	public List<String> getFulfilmentPartyUuids() {
		return fulfilmentPartyUuids;
	}
	
	public void setFulfilmentPartyUuids(List<String> fulfilmentPartyUuids) {
		this.fulfilmentPartyUuids = fulfilmentPartyUuids;
	}
	
	public boolean getSearchEitherDrugOrConceptStockItems() {
		return searchEitherDrugOrConceptStockItems;
	}
	
	public void setSearchEitherDrugOrConceptStockItems(boolean searchEitherDrugOrConceptStockItems) {
		this.searchEitherDrugOrConceptStockItems = searchEitherDrugOrConceptStockItems;
	}
	
	public List<Integer> getPatientIds() {
		return patientIds;
	}
	
	public void setPatientIds(List<Integer> patientIds) {
		this.patientIds = patientIds;
	}
	
	public boolean getIncludeVoided() {
		return includeVoided;
	}
	
	public void setIncludeVoided(boolean includeVoided) {
		this.includeVoided = includeVoided;
	}
	
	public Integer getLimit() {
		return limit;
	}
	
	public void setLimit(Integer limit) {
		this.limit = limit;
	}
	
	public Integer getStartIndex() {
		return startIndex;
	}
	
	public void setStartIndex(Integer startIndex) {
		this.startIndex = startIndex;
	}
	
	public List<Integer> getEncounterIds() {
		return encounterIds;
	}
	
	public void setEncounterIds(List<Integer> encounterIds) {
		this.encounterIds = encounterIds;
	}
	
	public List<String> getEncounterUuids() {
		return encounterUuids;
	}
	
	public void setEncounterUuids(List<String> encounterUuids) {
		this.encounterUuids = encounterUuids;
	}
	
	public Date getOrderDateMin() {
		return orderDateMin;
	}
	
	public void setOrderDateMin(Date orderDateMin) {
		this.orderDateMin = orderDateMin;
	}
	
	public Date getOrderDateMax() {
		return orderDateMax;
	}
	
	public void setOrderDateMax(Date orderDateMax) {
		this.orderDateMax = orderDateMax;
	}
}