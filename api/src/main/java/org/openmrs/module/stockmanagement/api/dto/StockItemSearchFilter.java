package org.openmrs.module.stockmanagement.api.dto;

import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.module.stockmanagement.api.model.StockItem.ItemType;

import java.util.List;

public class StockItemSearchFilter {

	private String uuid;

	private Integer startIndex;

	private Integer limit;

	/**
	 * New canonical type filter. When set, only items matching this
	 * {@link ItemType} are returned.
	 * Takes precedence over the legacy {@link #isDrug} flag when both are present.
	 */
	private ItemType itemType;

	/**
	 * @deprecated Use {@link #itemType} instead. Retained for backward
	 *             compatibility with callers
	 *             that have not yet migrated to the three-way type model.
	 *             If {@link #itemType} is set this field is ignored by the DAO
	 *             layer.
	 */
	@Deprecated
	private Boolean isDrug;

	private List<Drug> drugs;

	private List<Concept> concepts;

	private List<Integer> stockItemIds;

	private Integer drugId;

	private Integer conceptId;

	private boolean includeVoided;

	private boolean searchEitherDrugsOrConcepts = false;

	private List<Concept> categories;

	private Integer categoryId;

	private String genericConceptCode;

	private String etcdProductId;

	// itemType (primary filter going forward)

	public ItemType getItemType() {
		return itemType;
	}

	/**
	 * Sets the item-type filter and keeps the legacy {@code isDrug} flag in sync so
	 * that
	 * any DAO code that still checks {@code isDrug} behaves correctly for
	 * {@link ItemType#PHARMACEUTICAL} and {@link ItemType#NON_PHARMACEUTICAL}
	 * items.
	 * <p>
	 * Pass {@code null} to remove the type filter entirely (i.e. return all types).
	 */
	public void setItemType(ItemType itemType) {
		this.itemType = itemType;
		// Keep legacy field consistent for code that hasn't migrated yet.
		// LAB_COMMODITY has no direct isDrug equivalent, so we leave it null
		// to avoid inadvertently filtering out lab items in legacy paths.
		if (itemType == null) {
			this.isDrug = null;
		} else if (itemType == ItemType.PHARMACEUTICAL) {
			this.isDrug = true;
		} else if (itemType == ItemType.NON_PHARMACEUTICAL) {
			this.isDrug = false;
		} else {
			// LAB_COMMODITY – no legacy equivalent; nullify to avoid wrong filter
			this.isDrug = null;
		}
	}

	// Legacy isDrug (backward compatibility)
	/**
	 * @deprecated Use {@link #getItemType()} instead.
	 */
	@Deprecated
	public Boolean getIsDrug() {
		return isDrug;
	}

	/**
	 * Sets the legacy drug flag and derives {@link #itemType} from it.
	 * <p>
	 * New code should call {@link #setItemType(ItemType)} directly.
	 *
	 * @deprecated Use {@link #setItemType(ItemType)} instead.
	 */
	@Deprecated
	public void setIsDrug(Boolean isDrug) {
		this.isDrug = isDrug;
		// Only coerce itemType when it hasn't already been set to LAB_COMMODITY,
		// so that a stale isDrug=false call can't silently wipe a lab-item filter.
		if (isDrug == null) {
			if (this.itemType != ItemType.LAB_COMMODITY) {
				this.itemType = null;
			}
		} else if (this.itemType != ItemType.LAB_COMMODITY) {
			this.itemType = isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
		}
	}

	/**
	 * Resolves the effective {@link ItemType} to use in a DAO query.
	 * <ul>
	 * <li>If {@code itemType} is set, it is returned as-is.</li>
	 * <li>Otherwise the legacy {@code isDrug} flag is promoted to its equivalent
	 * {@link ItemType} so that a single code path handles both fields.</li>
	 * <li>Returns {@code null} when neither field is set (no type filter).</li>
	 * </ul>
	 * DAO / service implementations should call this method rather than reading
	 * {@code itemType} and {@code isDrug} separately.
	 */
	public ItemType resolveEffectiveItemType() {
		if (itemType != null) {
			return itemType;
		}
		if (isDrug != null) {
			return isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL;
		}
		return null; // no filter – return all types
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public Integer getStartIndex() {
		return startIndex;
	}

	public void setStartIndex(Integer startIndex) {
		this.startIndex = startIndex;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public List<Drug> getDrugs() {
		return drugs;
	}

	public void setDrugs(List<Drug> drugs) {
		this.drugs = drugs;
	}

	public List<Concept> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<Concept> concepts) {
		this.concepts = concepts;
	}

	public boolean getIncludeVoided() {
		return includeVoided;
	}

	public void setIncludeVoided(boolean includeVoided) {
		this.includeVoided = includeVoided;
	}

	public boolean getSearchEitherDrugsOrConcepts() {
		return searchEitherDrugsOrConcepts;
	}

	public void setSearchEitherDrugsOrConcepts(boolean searchEitherDrugsOrConcepts) {
		this.searchEitherDrugsOrConcepts = searchEitherDrugsOrConcepts;
	}

	public List<Integer> getStockItemIds() {
		return stockItemIds;
	}

	public void setStockItemIds(List<Integer> stockItemIds) {
		this.stockItemIds = stockItemIds;
	}

	public Integer getDrugId() {
		return drugId;
	}

	public void setDrugId(Integer drugId) {
		this.drugId = drugId;
	}

	public Integer getConceptId() {
		return conceptId;
	}

	public void setConceptId(Integer conceptId) {
		this.conceptId = conceptId;
	}

	public List<Concept> getCategories() {
		return categories;
	}

	public void setCategories(List<Concept> categories) {
		this.categories = categories;
	}

	public Integer getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(Integer categoryId) {
		this.categoryId = categoryId;
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
	public static class ItemGroupFilter {

		private Integer drugId;

		private Integer conceptId;

		/**
		 * New canonical type field for group-level filtering.
		 */
		private ItemType itemType;

		/**
		 * @deprecated Use {@link #itemType} instead.
		 */
		@Deprecated
		private Boolean isDrug;

		public Integer getDrugId() {
			return drugId;
		}

		public void setDrugId(Integer drugId) {
			this.drugId = drugId;
		}

		public Integer getConceptId() {
			return conceptId;
		}

		public void setConceptId(Integer conceptId) {
			this.conceptId = conceptId;
		}

		public ItemType getItemType() {
			return itemType;
		}

		public void setItemType(ItemType itemType) {
			this.itemType = itemType;
			if (itemType == null) {
				this.isDrug = null;
			} else if (itemType == ItemType.PHARMACEUTICAL) {
				this.isDrug = true;
			} else if (itemType == ItemType.NON_PHARMACEUTICAL) {
				this.isDrug = false;
			} else {
				this.isDrug = null; // LAB_COMMODITY has no legacy equivalent
			}
		}

		/**
		 * @deprecated Use {@link #getItemType()} instead.
		 */
		@Deprecated
		public Boolean getIsDrug() {
			return isDrug;
		}

		/**
		 * @deprecated Use {@link #setItemType(ItemType)} instead.
		 */
		@Deprecated
		public void setIsDrug(Boolean isDrug) {
			this.isDrug = isDrug;
			if (this.itemType != ItemType.LAB_COMMODITY) {
				this.itemType = isDrug == null ? null
						: (isDrug ? ItemType.PHARMACEUTICAL : ItemType.NON_PHARMACEUTICAL);
			}
		}

		/**
		 * Resolves the effective {@link ItemType} for DAO queries, falling back to
		 * the legacy {@code isDrug} flag when {@code itemType} is not set.
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

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			ItemGroupFilter that = (ItemGroupFilter) o;

			if (drugId != null ? !drugId.equals(that.drugId) : that.drugId != null)
				return false;
			if (conceptId != null ? !conceptId.equals(that.conceptId) : that.conceptId != null)
				return false;
			// Compare on itemType (canonical); fall back to isDrug for legacy instances
			// where itemType may not have been set.
			ItemType thisType = this.resolveEffectiveItemType();
			ItemType thatType = that.resolveEffectiveItemType();
			return thisType == thatType;
		}

		@Override
		public int hashCode() {
			int result = drugId != null ? drugId.hashCode() : 0;
			result = 31 * result + (conceptId != null ? conceptId.hashCode() : 0);
			// Hash on the resolved type so legacy and new instances hash consistently
			ItemType resolved = resolveEffectiveItemType();
			result = 31 * result + (resolved != null ? resolved.hashCode() : 0);
			return result;
		}
	}
}