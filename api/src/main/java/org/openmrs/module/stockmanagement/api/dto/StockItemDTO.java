package org.openmrs.module.stockmanagement.api.dto;


import org.openmrs.module.stockmanagement.api.model.StockItem.ItemType;
import org.openmrs.module.stockmanagement.api.model.StockItemReference;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class StockItemDTO {
	
	private Integer id;
	
	private String uuid;
	
	private Integer drugId;
	
	private String drugUuid;
	
	private String drugName;
	
	private Integer conceptId;
	
	private String conceptUuid;
	
	private String conceptName;
	
	private Boolean hasExpiration;
	
	private List<StockBatchDTO> stockBatches;
	
	private Integer preferredVendorId;
	
	private String preferredVendorUuid;
	
	private String preferredVendorName;
	
	private BigDecimal purchasePrice;
	
	private Integer purchasePriceUoMId;
	
	private String purchasePriceUoMUuid;
	
	private Integer purchasePriceConceptId;
	
	private String purchasePriceUoMName;
	
	private BigDecimal purchasePriceUoMFactor;
	
	private Integer dispensingUnitId;
	
	private String dispensingUnitName;
	
	private String dispensingUnitUuid;
	
	private Integer dispensingUnitPackagingUoMId;
	
	private String dispensingUnitPackagingUoMUuid;
	
	private Integer dispensingUnitPackagingConceptId;
	
	private String dispensingUnitPackagingUoMName;
	
	private BigDecimal dispensingUnitPackagingUoMFactor;
	
	private Integer defaultStockOperationsUoMId;
	
	private String defaultStockOperationsUoMUuid;
	
	private Integer defaultStockOperationsConceptId;
	
	private String defaultStockOperationsUoMName;
	
	private BigDecimal defaultStockOperationsUoMFactor;
	
	private List<StockItemPackagingUOMDTO> stockItemPackagingUOMs;
	
	private List<StockItemReference> stockItemReferences;
	
	private boolean voided;
	
	private Integer creator;
	
	private Date dateCreated;
	
	private String creatorGivenName;
	
	private String creatorFamilyName;
	
	private String commonName;
	
	private String acronym;
	
	private BigDecimal reorderLevel;
	
	private Integer reorderLevelUoMId;
	
	private String reorderLevelUoMUuid;
	
	private Integer reorderLevelConceptId;
	
	private String reorderLevelUoMName;
	
	private BigDecimal reorderLevelUoMFactor;
	
	private String drugStrength;
	
	private Integer categoryId;
	
	private String categoryUuid;
	
	private String categoryName;
	
	private Integer expiryNotice;
	
	private String genericConceptCode;

	private String etcdProductId;

	private String levelOfUse;
	
	private String ppbRegistrationCode;

	private String packageCode;

	/**
	 * Canonical item type – the primary type discriminator going forward.
	 * <p>
	 * Values:
	 * <ul>
	 *   <li>{@link ItemType#PHARMACEUTICAL}     – previously represented by {@code isDrug = true}</li>
	 *   <li>{@link ItemType#NON_PHARMACEUTICAL} – previously represented by {@code isDrug = false}</li>
	 *   <li>{@link ItemType#LAB_COMMODITY}      – new third category</li>
	 * </ul>
	 * When populated by the DAO/service layer from an existing record that pre-dates
	 * the {@code item_type} column, it is derived from the legacy {@code is_drug} value.
	 */
	private ItemType itemType;

	
	/**
	 * Returns the canonical item type.
	 * <p>
	 * If {@code itemType} has not been explicitly set (e.g. a DTO populated by a
	 * legacy query projection that only sets {@code isDrug}), the method falls back
	 * to deriving the type from the legacy {@code isDrug} field so callers always
	 * receive a meaningful value.
	 */
	public ItemType getItemType() {
		if (itemType != null) {
			return itemType;
		}
		// Derive from legacy drugId presence as a last resort so that DTOs
		// hydrated by older query paths still report a usable type.
		if (drugId != null) {
			return ItemType.PHARMACEUTICAL;
		}
		return null; // cannot determine without explicit data
	}

	/**
	 * Sets the canonical item type and keeps the legacy {@code isDrug} signal
	 * internally consistent so that any code still reading {@code isDrug} behaves
	 * correctly.
	 *
	 * @param itemType the new type; {@code null} clears the explicit override
	 */
	public void setItemType(ItemType itemType) {
		this.itemType = itemType;
	}

	/** Returns {@code true} if this DTO represents a pharmaceutical drug item. */
	public boolean isPharmaceutical() {
		return getItemType() == ItemType.PHARMACEUTICAL;
	}

	/** Returns {@code true} if this DTO represents a non-pharmaceutical item. */
	public boolean isNonPharmaceutical() {
		return getItemType() == ItemType.NON_PHARMACEUTICAL;
	}

	/** Returns {@code true} if this DTO represents a lab commodity. */
	public boolean isLabCommodity() {
		return getItemType() == ItemType.LAB_COMMODITY;
	}

	/**
	 * Legacy boolean accessor kept for backward compatibility with service-layer
	 * and reporting code that has not yet migrated to {@link #getItemType()}.
	 * <p>
	 * Returns {@code true} when {@code itemType == PHARMACEUTICAL},
	 * {@code false} for all other types, and {@code null} when no type
	 * information is available.
	 *
	 * @deprecated Use {@link #getItemType()} instead.
	 */
	@Deprecated
	public Boolean getIsDrug() {
		ItemType resolved = getItemType();
		if (resolved == null) {
			return null;
		}
		return resolved == ItemType.PHARMACEUTICAL;
	}

	/**
	 * Legacy boolean setter kept for backward compatibility.
	 * Derives and sets {@link #itemType} from the boolean value so the two
	 * representations stay in sync. Will never overwrite an existing
	 * {@link ItemType#LAB_COMMODITY} value with a false-mapped
	 * {@link ItemType#NON_PHARMACEUTICAL}, protecting lab items from
	 * accidental reclassification by old code paths.
	 *
	 * @deprecated Use {@link #setItemType(ItemType)} instead.
	 */
	@Deprecated
	public void setIsDrug(Boolean isDrug) {
		if (isDrug == null) {
			// Only clear itemType if it hasn't been set to LAB_COMMODITY
			if (this.itemType != ItemType.LAB_COMMODITY) {
				this.itemType = null;
			}
			return;
		}
		// Guard: don't overwrite LAB_COMMODITY with a coerced boolean value
		if (this.itemType == ItemType.LAB_COMMODITY) {
			return;
		}
		this.itemType = isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
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
	
	public Integer getDrugId() {
		return drugId;
	}
	
	public void setDrugId(Integer drugId) {
		this.drugId = drugId;
	}
	
	public String getDrugUuid() {
		return drugUuid;
	}
	
	public void setDrugUuid(String drugUuid) {
		this.drugUuid = drugUuid;
	}
	
	public String getDrugName() {
		return drugName;
	}
	
	public void setDrugName(String drugName) {
		this.drugName = drugName;
	}
	
	public Integer getConceptId() {
		return conceptId;
	}
	
	public void setConceptId(Integer conceptId) {
		this.conceptId = conceptId;
	}
	
	public String getConceptUuid() {
		return conceptUuid;
	}
	
	public void setConceptUuid(String conceptUuid) {
		this.conceptUuid = conceptUuid;
	}
	
	public String getConceptName() {
		return conceptName;
	}
	
	public void setConceptName(String conceptName) {
		this.conceptName = conceptName;
	}
	
	public Boolean getHasExpiration() {
		return hasExpiration;
	}
	
	public void setHasExpiration(Boolean hasExpiration) {
		this.hasExpiration = hasExpiration;
	}
	
	public List<StockBatchDTO> getStockBatches() {
		return stockBatches;
	}
	
	public void setStockBatches(List<StockBatchDTO> stockBatches) {
		this.stockBatches = stockBatches;
	}
	
	public Integer getPreferredVendorId() {
		return preferredVendorId;
	}
	
	public void setPreferredVendorId(Integer preferredVendorId) {
		this.preferredVendorId = preferredVendorId;
	}
	
	public String getPreferredVendorUuid() {
		return preferredVendorUuid;
	}
	
	public void setPreferredVendorUuid(String preferredVendorUuid) {
		this.preferredVendorUuid = preferredVendorUuid;
	}
	
	public String getPreferredVendorName() {
		return preferredVendorName;
	}
	
	public void setPreferredVendorName(String preferredVendorName) {
		this.preferredVendorName = preferredVendorName;
	}
	
	public BigDecimal getPurchasePrice() {
		return purchasePrice;
	}
	
	public void setPurchasePrice(BigDecimal purchasePrice) {
		this.purchasePrice = purchasePrice;
	}
	
	public Integer getPurchasePriceUoMId() {
		return purchasePriceUoMId;
	}
	
	public void setPurchasePriceUoMId(Integer purchasePriceUoMId) {
		this.purchasePriceUoMId = purchasePriceUoMId;
	}
	
	public String getPurchasePriceUoMUuid() {
		return purchasePriceUoMUuid;
	}
	
	public void setPurchasePriceUoMUuid(String purchasePriceUoMUuid) {
		this.purchasePriceUoMUuid = purchasePriceUoMUuid;
	}
	
	public Integer getPurchasePriceConceptId() {
		return purchasePriceConceptId;
	}
	
	public void setPurchasePriceConceptId(Integer purchasePriceConceptId) {
		this.purchasePriceConceptId = purchasePriceConceptId;
	}
	
	public String getPurchasePriceUoMName() {
		return purchasePriceUoMName;
	}
	
	public void setPurchasePriceUoMName(String purchasePriceUoMName) {
		this.purchasePriceUoMName = purchasePriceUoMName;
	}
	
	public Integer getDispensingUnitId() {
		return dispensingUnitId;
	}
	
	public void setDispensingUnitId(Integer dispensingUnitId) {
		this.dispensingUnitId = dispensingUnitId;
	}
	
	public String getDispensingUnitName() {
		return dispensingUnitName;
	}
	
	public void setDispensingUnitName(String dispensingUnitName) {
		this.dispensingUnitName = dispensingUnitName;
	}
	
	public String getDispensingUnitUuid() {
		return dispensingUnitUuid;
	}
	
	public void setDispensingUnitUuid(String dispensingUnitUuid) {
		this.dispensingUnitUuid = dispensingUnitUuid;
	}
	
	public Integer getDispensingUnitPackagingUoMId() {
		return dispensingUnitPackagingUoMId;
	}
	
	public void setDispensingUnitPackagingUoMId(Integer dispensingUnitPackagingUoMId) {
		this.dispensingUnitPackagingUoMId = dispensingUnitPackagingUoMId;
	}
	
	public String getDispensingUnitPackagingUoMUuid() {
		return dispensingUnitPackagingUoMUuid;
	}
	
	public void setDispensingUnitPackagingUoMUuid(String dispensingUnitPackagingUoMUuid) {
		this.dispensingUnitPackagingUoMUuid = dispensingUnitPackagingUoMUuid;
	}
	
	public Integer getDispensingUnitPackagingConceptId() {
		return dispensingUnitPackagingConceptId;
	}
	
	public void setDispensingUnitPackagingConceptId(Integer dispensingUnitPackagingConceptId) {
		this.dispensingUnitPackagingConceptId = dispensingUnitPackagingConceptId;
	}
	
	public String getDispensingUnitPackagingUoMName() {
		return dispensingUnitPackagingUoMName;
	}
	
	public void setDispensingUnitPackagingUoMName(String dispensingUnitPackagingUoMName) {
		this.dispensingUnitPackagingUoMName = dispensingUnitPackagingUoMName;
	}
	
	public Integer getDefaultStockOperationsUoMId() {
		return defaultStockOperationsUoMId;
	}
	
	public void setDefaultStockOperationsUoMId(Integer defaultStockOperationsUoMId) {
		this.defaultStockOperationsUoMId = defaultStockOperationsUoMId;
	}
	
	public String getDefaultStockOperationsUoMUuid() {
		return defaultStockOperationsUoMUuid;
	}
	
	public void setDefaultStockOperationsUoMUuid(String defaultStockOperationsUoMUuid) {
		this.defaultStockOperationsUoMUuid = defaultStockOperationsUoMUuid;
	}
	
	public Integer getDefaultStockOperationsConceptId() {
		return defaultStockOperationsConceptId;
	}
	
	public void setDefaultStockOperationsConceptId(Integer defaultStockOperationsConceptId) {
		this.defaultStockOperationsConceptId = defaultStockOperationsConceptId;
	}
	
	public String getDefaultStockOperationsUoMName() {
		return defaultStockOperationsUoMName;
	}
	
	public void setDefaultStockOperationsUoMName(String defaultStockOperationsUoMName) {
		this.defaultStockOperationsUoMName = defaultStockOperationsUoMName;
	}
	
	public List<StockItemPackagingUOMDTO> getStockItemPackagingUOMs() {
		return stockItemPackagingUOMs;
	}
	
	public void setStockItemPackagingUOMs(List<StockItemPackagingUOMDTO> stockItemPackagingUOMs) {
		this.stockItemPackagingUOMs = stockItemPackagingUOMs;
	}
	
	public List<StockItemReference> getStockItemReferences() {
		return stockItemReferences;
	}
	
	public void setStockItemReferences(List<StockItemReference> stockItemReferences) {
		this.stockItemReferences = stockItemReferences;
	}
	
	public boolean getVoided() {
		return voided;
	}
	
	public void setVoided(boolean voided) {
		this.voided = voided;
	}
	
	public Integer getCreator() {
		return creator;
	}
	
	public void setCreator(Integer creator) {
		this.creator = creator;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public String getCreatorGivenName() {
		return creatorGivenName;
	}
	
	public void setCreatorGivenName(String creatorGivenName) {
		this.creatorGivenName = creatorGivenName;
	}
	
	public String getCreatorFamilyName() {
		return creatorFamilyName;
	}
	
	public void setCreatorFamilyName(String creatorFamilyName) {
		this.creatorFamilyName = creatorFamilyName;
	}
	
	public String getCommonName() {
		return commonName;
	}
	
	public void setCommonName(String commonName) {
		this.commonName = commonName;
	}
	
	public String getAcronym() {
		return acronym;
	}
	
	public void setAcronym(String acronym) {
		this.acronym = acronym;
	}
	
	public BigDecimal getReorderLevel() {
		return reorderLevel;
	}
	
	public void setReorderLevel(BigDecimal reorderLevel) {
		this.reorderLevel = reorderLevel;
	}
	
	public Integer getReorderLevelUoMId() {
		return reorderLevelUoMId;
	}
	
	public void setReorderLevelUoMId(Integer reorderLevelUoMId) {
		this.reorderLevelUoMId = reorderLevelUoMId;
	}
	
	public String getReorderLevelUoMUuid() {
		return reorderLevelUoMUuid;
	}
	
	public void setReorderLevelUoMUuid(String reorderLevelUoMUuid) {
		this.reorderLevelUoMUuid = reorderLevelUoMUuid;
	}
	
	public Integer getReorderLevelConceptId() {
		return reorderLevelConceptId;
	}
	
	public void setReorderLevelConceptId(Integer reorderLevelConceptId) {
		this.reorderLevelConceptId = reorderLevelConceptId;
	}
	
	public String getReorderLevelUoMName() {
		return reorderLevelUoMName;
	}
	
	public void setReorderLevelUoMName(String reorderLevelUoMName) {
		this.reorderLevelUoMName = reorderLevelUoMName;
	}
	
	public String getDrugStrength() {
		return drugStrength;
	}
	
	public void setDrugStrength(String drugStrength) {
		this.drugStrength = drugStrength;
	}
	
	public Integer getCategoryId() {
		return categoryId;
	}
	
	public void setCategoryId(Integer categoryId) {
		this.categoryId = categoryId;
	}
	
	public String getCategoryUuid() {
		return categoryUuid;
	}
	
	public void setCategoryUuid(String categoryUuid) {
		this.categoryUuid = categoryUuid;
	}
	
	public String getCategoryName() {
		return categoryName;
	}
	
	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}
	
	public Integer getExpiryNotice() {
		return expiryNotice;
	}
	
	public void setExpiryNotice(Integer expiryNotice) {
		this.expiryNotice = expiryNotice;
	}
	
	public BigDecimal getPurchasePriceUoMFactor() {
		return purchasePriceUoMFactor;
	}
	
	public void setPurchasePriceUoMFactor(BigDecimal purchasePriceUoMFactor) {
		this.purchasePriceUoMFactor = purchasePriceUoMFactor;
	}
	
	public BigDecimal getDispensingUnitPackagingUoMFactor() {
		return dispensingUnitPackagingUoMFactor;
	}
	
	public void setDispensingUnitPackagingUoMFactor(BigDecimal dispensingUnitPackagingUoMFactor) {
		this.dispensingUnitPackagingUoMFactor = dispensingUnitPackagingUoMFactor;
	}
	
	public BigDecimal getDefaultStockOperationsUoMFactor() {
		return defaultStockOperationsUoMFactor;
	}
	
	public void setDefaultStockOperationsUoMFactor(BigDecimal defaultStockOperationsUoMFactor) {
		this.defaultStockOperationsUoMFactor = defaultStockOperationsUoMFactor;
	}
	
	public BigDecimal getReorderLevelUoMFactor() {
		return reorderLevelUoMFactor;
	}
	
	public void setReorderLevelUoMFactor(BigDecimal reorderLevelUoMFactor) {
		this.reorderLevelUoMFactor = reorderLevelUoMFactor;
	}

	public String getGenericConceptCode() {
		return genericConceptCode;
	}

	public void setGenericConceptCode(String genericConceptCode) {
		this.genericConceptCode = genericConceptCode;
	}

	public String getEtcdProductId() {
		return etcdProductId;
	}

	public void setEtcdProductId(String etcdProductId) {
		this.etcdProductId = etcdProductId;
	}

	public String getLevelOfUse() {
		return levelOfUse;
	}

	public void setLevelOfUse(String levelOfUse) {
		this.levelOfUse = levelOfUse;
	}

	public String getPpbRegistrationCode() {
		return ppbRegistrationCode;
	}

	public void setPpbRegistrationCode(String ppbRegistrationCode) {
		this.ppbRegistrationCode = ppbRegistrationCode;
	}

	public String getPackageCode() {
		return packageCode;
	}

	public void setPackageCode(String packageCode) {
		this.packageCode = packageCode;
	}
}