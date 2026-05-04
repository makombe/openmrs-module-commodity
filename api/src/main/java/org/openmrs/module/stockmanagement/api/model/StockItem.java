package org.openmrs.module.stockmanagement.api.model;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.openmrs.Concept;
import org.openmrs.Drug;

import java.io.Serializable;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

/**
 * The persistent class for the stockmgmt_stock_item database table.
 */
@Entity(name = "stockmanagement.StockItem")
@Table(name = "stockmgmt_stock_item")
@Indexed
public class StockItem extends org.openmrs.BaseChangeableOpenmrsData implements Serializable {

	/**
	 * Represents the type of a stock item.
	 * <p>
	 * NON_PHARMACEUTICAL (0) and PHARMACEUTICAL (1) mirror the legacy {@code is_drug} boolean.
	 * LAB_COMMODITY (2) is the newly introduced type.
	 * <p>
	 * Whenever {@code itemType} is set, {@code isDrug} is kept in sync automatically so that
	 * existing code that still reads the {@code is_drug} column continues to work correctly.
	 */
	public enum ItemType {
		/** Formerly is_drug = false */
		NON_PHARMACEUTICAL(0),
		/** Formerly is_drug = true */
		PHARMACEUTICAL(1),
		/** New third category */
		LAB_COMMODITY(2),
		/** New fourth category */
		OTHER(3);

		private final int value;

		ItemType(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		/**
		 * Resolves an {@link ItemType} from its integer database value.
		 *
		 * @throws IllegalArgumentException if the value has no matching constant
		 */
		public static ItemType fromValue(int value) {
			for (ItemType type : values()) {
				if (type.value == value) {
					return type;
				}
			}
			throw new IllegalArgumentException("Unknown item type value: " + value);
		}

		/**
		 * Convenience factory – derives the type from the legacy {@code isDrug} flag.
		 * Used when migrating records that pre-date the {@code item_type} column.
		 */
		public static ItemType fromIsDrug(boolean isDrug) {
			return isDrug ? PHARMACEUTICAL : NON_PHARMACEUTICAL;
		}
	}

	@Converter
	public static class ItemTypeConverter implements AttributeConverter<ItemType, Integer> {

		@Override
		public Integer convertToDatabaseColumn(ItemType attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public ItemType convertToEntityAttribute(Integer dbData) {
			return dbData == null ? null : ItemType.fromValue(dbData);
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "stock_item_id")
	@DocumentId
	private Integer id;

	@JoinColumn(name = "concept_id")
	@ManyToOne(fetch = FetchType.LAZY)
	private Concept concept;

	@JoinColumn(name = "drug_id")
	@OneToOne(fetch = FetchType.LAZY)
	private Drug drug;

	@Column(name = "has_expiration")
	private boolean hasExpiration;

	@OneToMany(mappedBy = "stockItem")
	private Set<StockBatch> stockBatches;

	@JoinColumn(name = "preferred_vendor_id")
	@ManyToOne(fetch = FetchType.LAZY)
	private StockSource preferredVendor;

	@Column(name = "purchase_price", nullable = true)
	private BigDecimal purchasePrice;

	@JoinColumn(name = "purchase_price_uom_id", nullable = true)
	@ManyToOne(fetch = FetchType.LAZY)
	private StockItemPackagingUOM purchasePriceUoM;

	@JoinColumn(name = "dispensing_unit_id", nullable = true)
	@ManyToOne(fetch = FetchType.LAZY)
	private Concept dispensingUnit;

	@JoinColumn(name = "dispensing_unit_packaging_uom_id", nullable = true)
	@ManyToOne(fetch = FetchType.LAZY)
	private StockItemPackagingUOM dispensingUnitPackagingUoM;

	@JoinColumn(name = "default_stock_operations_uom_id", nullable = true)
	@ManyToOne(fetch = FetchType.LAZY)
	private StockItemPackagingUOM defaultStockOperationsUoM;

	@OneToMany(mappedBy = "stockItem")
	private Set<StockItemPackagingUOM> stockItemPackagingUOMs;

	@OneToMany(mappedBy = "stockItem", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<StockItemReference> references;

	/**
	 * Legacy boolean kept for backward compatibility with existing queries and integrations.
	 * <strong>Do not set this field directly</strong> – use {@link #setItemType(ItemType)} or
	 * {@link #setIsDrug(Boolean)} instead; both methods keep the two fields in sync.
	 */
	@GenericField
	@Column(name = "is_drug", nullable = false)
	private Boolean isDrug;

	/**
	 * New canonical type discriminator.
	 * Stored as a plain integer in the {@code item_type} column via {@link ItemTypeConverter}.
	 * <p>
	 * Values:
	 * <ul>
	 *   <li>0 – Non-Pharmaceutical</li>
	 *   <li>1 – Pharmaceutical (Drug)</li>
	 *   <li>2 – Lab Commodity</li>
	 *   <li>3 – Other</li>
	 * </ul>
	 */
	@GenericField
	@Column(name = "item_type", nullable = false)
	@Convert(converter = ItemTypeConverter.class)
	private ItemType itemType;

	@FullTextField
	@Column(name = "common_name", length = 255, nullable = true)
	private String commonName;

	@FullTextField
	@Column(name = "acronym", length = 255, nullable = true)
	private String acronym;

	@Column(name = "reorder_level", nullable = true)
	private BigDecimal reorderLevel;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reorder_level_uom_id")
	private StockItemPackagingUOM reorderLevelUOM;

	@JoinColumn(name = "category_id")
	@ManyToOne(fetch = FetchType.LAZY)
	private Concept category;

	@Column(name = "expiry_notice", nullable = true)
	private Integer expiryNotice;

	@Column(name = "level_of_use", length = 255, nullable = true)
	private String levelOfUse;

	@Column(name = "generic_concept_code", length = 255, nullable = true)
	private String genericConceptCode;

	@Column(name = "etcd_product_id", length = 255, nullable = true)
	private String etcdProductId;

	@Column(name = "ppb_registration_code", length = 255, nullable = true)
	private String ppbRegistrationCode;

	@Column(name = "package_code", length = 255, nullable = true)
	private String packageCode;

	public StockItem() {
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Drug getDrug() {
		return this.drug;
	}

	/**
	 * Sets the associated {@link Drug} and updates both {@code isDrug} and {@code itemType}
	 * when a drug is attached or detached, preserving the legacy behaviour.
	 */
	public void setDrug(Drug drug) {
		this.drug = drug;
		if (!((drug != null && isDrug != null && isDrug) || (drug == null && isDrug != null && !isDrug))) {
			boolean drugPresent = drug != null;
			this.isDrug = drugPresent;
			if (this.itemType == null
					|| this.itemType == ItemType.NON_PHARMACEUTICAL
					|| this.itemType == ItemType.PHARMACEUTICAL) {
				this.itemType = drugPresent ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
			}
		}
	}

	/**
	 * Returns the stock item's canonical type.
	 * <p>
	 * If {@code itemType} was never explicitly set (e.g. a legacy record loaded before the
	 * column existed), it is derived on-the-fly from {@code isDrug} so callers always receive
	 * a non-null value for records that have {@code isDrug} populated.
	 */
	public ItemType getItemType() {
		if (itemType == null && isDrug != null) {
			return ItemType.fromIsDrug(isDrug);
		}
		return itemType;
	}

	/**
	 * Sets the item type and keeps {@code isDrug} in sync.
	 *
	 * @param itemType the new type; must not be {@code null}
	 */
	public void setItemType(ItemType itemType) {
		this.itemType = itemType;
		// Keep legacy field consistent
		this.isDrug = (itemType == ItemType.PHARMACEUTICAL);
	}

	/**
	 * @deprecated Prefer {@link #getItemType()} which supports all three item categories.
	 *             This method is kept for backward compatibility only.
	 */
	@Deprecated
	public Boolean getIsDrug() {
		return isDrug;
	}

	/**
	 * Sets the legacy {@code isDrug} flag and updates {@code itemType} accordingly.
	 * <p>
	 * This setter exists solely for backward compatibility. New code should call
	 * {@link #setItemType(ItemType)} directly.
	 *
	 * @deprecated use {@link #setItemType(ItemType)} instead
	 */
	@Deprecated
	public void setIsDrug(Boolean isDrug) {
		this.isDrug = isDrug;
		if (isDrug != null) {
			if (this.itemType == null
					|| (this.itemType != ItemType.LAB_COMMODITY
							&& this.itemType != ItemType.OTHER)) {
				this.itemType = isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
			}
		}
	}

	/** Returns {@code true} if this item is a pharmaceutical drug. */
	public boolean isPharmaceutical() {
		return getItemType() == ItemType.PHARMACEUTICAL;
	}

	/** Returns {@code true} if this item is a non-pharmaceutical commodity. */
	public boolean isNonPharmaceutical() {
		return getItemType() == ItemType.NON_PHARMACEUTICAL;
	}

	/** Returns {@code true} if this item is a lab commodity. */
	public boolean isLabCommodity() {
		return getItemType() == ItemType.LAB_COMMODITY;
	}

	/**
	 * Returns {@code true} if this item does not fall into pharmaceutical,
	 * non-pharmaceutical, or lab commodity categories.
	 */
	public boolean isOther() {
		return getItemType() == ItemType.OTHER;
	}

	public boolean getHasExpiration() {
		return this.hasExpiration;
	}

	public void setHasExpiration(boolean hasExpiration) {
		this.hasExpiration = hasExpiration;
	}

	public Concept getConcept() {
		return concept;
	}

	public void setConcept(Concept concept) {
		this.concept = concept;
	}

	public StockItemPackagingUOM getDispensingUnitPackagingUoM() {
		return dispensingUnitPackagingUoM;
	}

	public void setDispensingUnitPackagingUoM(StockItemPackagingUOM dispensingUnitPackagingUoM) {
		this.dispensingUnitPackagingUoM = dispensingUnitPackagingUoM;
	}

	public Set<StockBatch> getStockBatches() {
		return this.stockBatches;
	}

	public void setStockBatches(Set<StockBatch> stockBatches) {
		this.stockBatches = stockBatches;
	}

	public StockBatch addStockBatch(StockBatch stockBatch) {
		getStockBatches().add(stockBatch);
		stockBatch.setStockItem(this);
		return stockBatch;
	}

	public StockBatch removeStockBatch(StockBatch stockBatch) {
		getStockBatches().remove(stockBatch);
		stockBatch.setStockItem(null);
		return stockBatch;
	}

	public Set<StockItemPackagingUOM> getStockItemPackagingUOMs() {
		return this.stockItemPackagingUOMs;
	}

	public void setStockItemPackagingUOMs(Set<StockItemPackagingUOM> stockItemPackagingUOMs) {
		this.stockItemPackagingUOMs = stockItemPackagingUOMs;
	}

	public StockItemPackagingUOM addStockItemPackagingUom(StockItemPackagingUOM stockItemPackagingUom) {
		getStockItemPackagingUOMs().add(stockItemPackagingUom);
		stockItemPackagingUom.setStockItem(this);
		return stockItemPackagingUom;
	}

	public StockItemPackagingUOM removeStockItemPackagingUom(StockItemPackagingUOM stockItemPackagingUom) {
		getStockItemPackagingUOMs().remove(stockItemPackagingUom);
		stockItemPackagingUom.setStockItem(null);
		return stockItemPackagingUom;
	}

	public Set<StockItemReference> getReferences() {
		return references;
	}

	public void setStockItemReferences(Set<StockItemReference> references) {
		this.references = references;
	}

	public StockItemReference addStockItemReference(StockItemReference reference) {
		getReferences().add(reference);
		reference.setStockItem(this);
		return reference;
	}

	public StockItemReference removeStockItemReferences(StockItemReference reference) {
		getReferences().remove(reference);
		reference.setStockItem(null);
		return reference;
	}

	public boolean isHasExpiration() {
		return hasExpiration;
	}

	public StockSource getPreferredVendor() {
		return preferredVendor;
	}

	public void setPreferredVendor(StockSource preferredVendor) {
		this.preferredVendor = preferredVendor;
	}

	public BigDecimal getPurchasePrice() {
		return purchasePrice;
	}

	public void setPurchasePrice(BigDecimal purchasePrice) {
		this.purchasePrice = purchasePrice;
	}

	public StockItemPackagingUOM getPurchasePriceUoM() {
		return purchasePriceUoM;
	}

	public void setPurchasePriceUoM(StockItemPackagingUOM purchasePriceUoM) {
		this.purchasePriceUoM = purchasePriceUoM;
	}

	public Concept getDispensingUnit() {
		return dispensingUnit;
	}

	public void setDispensingUnit(Concept dispensingUnit) {
		this.dispensingUnit = dispensingUnit;
	}

	public StockItemPackagingUOM getDefaultStockOperationsUoM() {
		return defaultStockOperationsUoM;
	}

	public void setDefaultStockOperationsUoM(StockItemPackagingUOM defaultStockOperationsUoM) {
		this.defaultStockOperationsUoM = defaultStockOperationsUoM;
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

	public StockItemPackagingUOM getReorderLevelUOM() {
		return reorderLevelUOM;
	}

	public void setReorderLevelUOM(StockItemPackagingUOM reorderLevelUOM) {
		this.reorderLevelUOM = reorderLevelUOM;
	}

	public Concept getCategory() {
		return category;
	}

	public void setCategory(Concept category) {
		this.category = category;
	}

	public Integer getExpiryNotice() {
		return expiryNotice;
	}

	public void setExpiryNotice(Integer expiryNotice) {
		this.expiryNotice = expiryNotice;
	}

	public String getLevelOfUse() {
		return levelOfUse;
	}

	public void setLevelOfUse(String levelOfUse) {
		this.levelOfUse = levelOfUse;
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