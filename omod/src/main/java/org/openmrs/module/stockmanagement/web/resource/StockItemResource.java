package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.*;

import org.apache.commons.lang.StringUtils;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.stockmanagement.api.Privileges;
import org.openmrs.module.stockmanagement.api.StockManagementException;
import org.openmrs.module.stockmanagement.api.dto.*;
import org.openmrs.module.stockmanagement.api.model.*;
import org.openmrs.module.stockmanagement.api.model.StockItem.ItemType;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.util.LocaleUtility;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Resource(name = RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID + "/stockitem", supportedClass = StockItemDTO.class, supportedOpenmrsVersions = {
        "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*" })
public class StockItemResource extends ResourceBase<StockItemDTO> {
	
	@Override
	public StockItemDTO getByUniqueId(String uniqueId) {
		StockItemSearchFilter filter = new StockItemSearchFilter();
		filter.setIncludeVoided(true);
		filter.setUuid(uniqueId);
		Result<StockItemDTO> result = getStockManagementService().findStockItems(filter);
		return result.getData().isEmpty() ? null : result.getData().get(0);
	}
	
	@Override
	protected void delete(StockItemDTO delegate, String reason, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) {
		String searchToken = context.getParameter("q");
		if (StringUtils.isBlank(searchToken)) {
			return getStockItemsDirect(context);
		} else {
			return searchDrugsAndConcepts(context, searchToken);
		}
	}

	/**
	 * Resolves the effective {@link ItemType} from the incoming request.
	 * <p>
	 * Resolution priority (first non-null wins):
	 * <ol>
	 *   <li>{@code itemType} request parameter – new canonical filter.
	 *       Accepted values: {@code PHARMACEUTICAL}, {@code NON_PHARMACEUTICAL},
	 *       {@code LAB_COMMODITY} (case-insensitive).</li>
	 *   <li>{@code isDrug} request parameter – legacy boolean filter kept for
	 *       backward compatibility. {@code true} → {@link ItemType#PHARMACEUTICAL},
	 *       {@code false} → {@link ItemType#NON_PHARMACEUTICAL}.</li>
	 *   <li>{@code null} – no type filter; all types are returned.</li>
	 * </ol>
	 * Callers that still pass {@code isDrug=true/false} continue to work without
	 * any changes on the client side.
	 */
	private ItemType resolveItemTypeFilter(RequestContext context) {
		// 1. Try the new itemType parameter first
		String itemTypeParam = context.getParameter("itemType");
		if (!StringUtils.isBlank(itemTypeParam)) {
			try {
				return ItemType.valueOf(itemTypeParam.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				// Fall through to legacy parameter rather than hard-failing,
				// so existing integrations are not broken by an unexpected value.
			}
		}

		// 2. Fall back to legacy isDrug boolean
		String isDrugParam = context.getParameter("isDrug");
		if (!StringUtils.isBlank(isDrugParam)) {
			return "true".equalsIgnoreCase(isDrugParam.trim())
			        ? ItemType.PHARMACEUTICAL
			        : ItemType.NON_PHARMACEUTICAL;
		}

		// 3. No filter – return all types
		return null;
	}

	/**
	 * @deprecated Use {@link #resolveItemTypeFilter(RequestContext)} instead.
	 *             Retained only to avoid breaking any subclasses or tests that
	 *             may call this method directly.
	 */
	@Deprecated
	private Boolean isDrugSearch(RequestContext context) {
		String isDrug = context.getParameter("isDrug");
		if (StringUtils.isBlank(isDrug))
			return null;
		return "true".equalsIgnoreCase(isDrug);
	}
	
	protected PageableResult getStockItemsDirect(RequestContext context) {
		StockItemSearchFilter filter = new StockItemSearchFilter();

		// Use the unified resolver so both isDrug and itemType params work
		ItemType itemTypeFilter = resolveItemTypeFilter(context);
		filter.setItemType(itemTypeFilter);

		filter.setIncludeVoided(context.getIncludeAll());

		String param = context.getParameter("drugUuid");
		if (!StringUtils.isBlank(param)) {
			Drug drug = Context.getConceptService().getDrugByUuid(param);
			if (drug == null) {
				return emptyResult(context);
			}
			filter.setDrugId(drug.getDrugId());
		}
		
		param = context.getParameter("conceptUuid");
		if (!StringUtils.isBlank(param)) {
			Concept concept = Context.getConceptService().getConcept(param);
			if (concept == null) {
				return emptyResult(context);
			}
			filter.setConceptId(concept.getConceptId());
		}
		
		param = context.getParameter("categoryUuid");
		if (!StringUtils.isBlank(param)) {
			Concept concept = Context.getConceptService().getConcept(param);
			if (concept == null) {
				return emptyResult(context);
			}
			filter.setCategoryId(concept.getConceptId());
		}

		param = context.getParameter("genericConceptCode");
		if (!StringUtils.isBlank(param)) {
			filter.setGenericConceptCode(param);
		}

		param = context.getParameter("etcdProductId");
		if (!StringUtils.isBlank(param)) {
			filter.setEtcdProductId(param);
		}
		
		filter.setStartIndex(context.getStartIndex());
		filter.setLimit(context.getLimit());
		Result<StockItemDTO> result = getStockManagementService().findStockItems(filter);
		return toAlreadyPaged(result, context);
	}
	
	protected PageableResult searchDrugsAndConcepts(RequestContext context, String searchToken) {
		ItemType itemTypeFilter = resolveItemTypeFilter(context);

		// Determine which entity types to search based on the resolved filter.
		// LAB_COMMODITY items are concept-based, so they follow the concept search path.
		boolean searchDrugs = true;
		boolean searchConcepts = true;
		if (itemTypeFilter != null) {
			if (itemTypeFilter == ItemType.PHARMACEUTICAL) {
				// Drugs are always pharmaceutical; concepts may also be pharmaceutical
				// but the primary driver for "isDrug=true" historically was the drug search.
				searchConcepts = false;
			} else {
				// NON_PHARMACEUTICAL and LAB_COMMODITY are concept-based only
				searchDrugs = false;
			}
		}

		StockItemSearchFilter filter = new StockItemSearchFilter();
		filter.setItemType(itemTypeFilter);
		filter.setIncludeVoided(context.getIncludeAll());
		filter.setStartIndex(context.getStartIndex());
		filter.setLimit(context.getLimit());
		filter.setGenericConceptCode(searchToken);
		filter.setEtcdProductId(searchToken);

		String param = context.getParameter("drugUuid");
		if (!StringUtils.isBlank(param)) {
			Drug drug = Context.getConceptService().getDrugByUuid(param);
			if (drug == null) {
				return emptyResult(context);
			}
			filter.setDrugId(drug.getDrugId());
		}

		param = context.getParameter("conceptUuid");
		if (!StringUtils.isBlank(param)) {
			Concept concept = Context.getConceptService().getConcept(param);
			if (concept == null) {
				return emptyResult(context);
			}
			filter.setConceptId(concept.getConceptId());
		}

		param = context.getParameter("categoryUuid");
		if (!StringUtils.isBlank(param)) {
			Concept concept = Context.getConceptService().getConcept(param);
			if (concept == null) {
				return emptyResult(context);
			}
			filter.setCategoryId(concept.getConceptId());
		}

		param = context.getParameter("genericConceptCode");
		if (!StringUtils.isBlank(param)) {
			filter.setGenericConceptCode(param);
		}

		param = context.getParameter("etcdProductId");
		if (!StringUtils.isBlank(param)) {
			filter.setEtcdProductId(param);
		}

		ConceptService service = Context.getConceptService();
		Integer maxIntermediateResult = GlobalProperties.getStockItemSearchMaxDrugConceptIntermediateResult();

		// Pass the resolved isDrug flag to the common-name search so it can still
		// apply a type filter at the DB level. For LAB_COMMODITY we pass null so
		// concept-based lab items are included (the itemType column will do the
		// final filtering in findStockItems).
		Boolean isDrugForCommonNameSearch = itemTypeFilter == null ? null
		        : (itemTypeFilter == ItemType.PHARMACEUTICAL ? Boolean.TRUE : Boolean.FALSE);

		List<Integer> itemsFound = getStockManagementService().searchStockItemCommonName(
		        searchToken, isDrugForCommonNameSearch, context.getIncludeAll(), maxIntermediateResult);
		if (!itemsFound.isEmpty()) {
			filter.setStockItemIds(itemsFound);
			maxIntermediateResult = Math.max(0, maxIntermediateResult - itemsFound.size());
		}

		if (searchConcepts) {
			List<Locale> locales = new ArrayList<>(LocaleUtility.getLocalesInOrder());
			List<Concept> searchResults = service.getConcepts(searchToken, locales, true, null, null, null, null, null, 0,
			        maxIntermediateResult + (searchDrugs ? 0 : maxIntermediateResult))
			        .stream()
			        .map(p -> p.getConcept())
			        .collect(Collectors.toList());
			if (searchResults.isEmpty())
				searchConcepts = false;
			else
				filter.setConcepts(searchResults);
		}

		if (searchDrugs) {
			List<Drug> drugs = service.getDrugs(searchToken, null, true, false, true, 0,
			        maxIntermediateResult + (searchConcepts
			                ? Math.max(0, maxIntermediateResult - (filter.getConcepts() != null ? filter.getConcepts().size() : 0))
			                : maxIntermediateResult));
			if (drugs.isEmpty())
				searchDrugs = false;
			else
				filter.setDrugs(drugs);
		}

		if (searchConcepts && searchDrugs) {
			filter.setSearchEitherDrugsOrConcepts(true);
		}

		Result<StockItemDTO> result = getStockManagementService().findStockItems(filter);
		return toAlreadyPaged(result, context);
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		return doSearch(context);
	}
	
	@Override
	public StockItemDTO newDelegate() {
		return new StockItemDTO();
	}
	
	@Override
	public StockItemDTO save(StockItemDTO delegate) {
		try {
			StockItem stockItem = getStockManagementService().saveStockItem(delegate);
			return getByUniqueId(stockItem.getUuid());
		}
		catch (StockManagementException exception) {
			throw new RestClientException(exception.getMessage());
		}
	}
	
	@Override
	public void purge(StockItemDTO delegate, RequestContext context) throws ResponseException {
		delete(delegate, null, context);
	}
	
	@PropertySetter("purchasePrice")
	public void setPurchasePrice(StockItemDTO instance, Double value) {
		if (value == null) {
			instance.setPurchasePrice(null);
		} else {
			instance.setPurchasePrice(BigDecimal.valueOf(value));
		}
	}
	
	@PropertySetter("reorderLevel")
	public void setReorderLevel(StockItemDTO instance, Double value) {
		if (value == null) {
			instance.setReorderLevel(null);
		} else {
			instance.setReorderLevel(BigDecimal.valueOf(value));
		}
	}

	/**
	 * Setter for the {@code itemType} property on create/update requests.
	 * Accepts both the enum name (e.g. {@code "PHARMACEUTICAL"}) and the
	 * integer value (e.g. {@code "1"}).  Also keeps the legacy {@code isDrug}
	 * field in sync inside the DTO so service-layer code that still reads
	 * {@code isDrug} behaves correctly.
	 */
	@PropertySetter("itemType")
	public void setItemType(StockItemDTO instance, Object value) {
		if (value == null) {
			instance.setItemType(null);
			return;
		}
		String raw = value.toString().trim();
		ItemType resolved;
		try {
			// Try numeric first (0, 1, 2)
			int numeric = Integer.parseInt(raw);
			resolved = ItemType.fromValue(numeric);
		} catch (NumberFormatException e) {
			// Try enum name (PHARMACEUTICAL, NON_PHARMACEUTICAL, LAB_COMMODITY)
			resolved = ItemType.valueOf(raw.toUpperCase());
		}
		instance.setItemType(resolved);
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("drugUuid");
		description.addProperty("conceptUuid");
		description.addProperty("hasExpiration");
		description.addProperty("preferredVendorUuid");
		description.addProperty("dispensingUnitUuid");
		description.addProperty("categoryUuid");
		description.addProperty("commonName");
		description.addProperty("acronym");
		description.addProperty("expiryNotice");
		description.addProperty("levelOfUse");
		description.addProperty("genericConceptCode");
		description.addProperty("etcdProductId");
		description.addProperty("ppbRegistrationCode");
		description.addProperty("packageCode");
		description.addProperty("itemType");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("hasExpiration");
		description.addProperty("preferredVendorUuid");
		description.addProperty("purchasePrice");
		description.addProperty("purchasePriceUoMUuid");
		description.addProperty("dispensingUnitUuid");
		description.addProperty("dispensingUnitPackagingUoMUuid");
		description.addProperty("defaultStockOperationsUoMUuid");
		description.addProperty("categoryUuid");
		description.addProperty("commonName");
		description.addProperty("acronym");
		description.addProperty("reorderLevel");
		description.addProperty("reorderLevelUoMUuid");
		description.addProperty("expiryNotice");
		description.addProperty("levelOfUse");
		description.addProperty("genericConceptCode");
		description.addProperty("etcdProductId");
		description.addProperty("ppbRegistrationCode");
		description.addProperty("packageCode");
		// New: allows reclassifying a concept-based item between
		// NON_PHARMACEUTICAL and LAB_COMMODITY. Service layer guards
		// against reclassifying drug-linked items.
		description.addProperty("itemType");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			description.addProperty("uuid");
			description.addProperty("drugUuid");
			description.addProperty("drugName");
			description.addProperty("conceptUuid");
			description.addProperty("conceptName");
			description.addProperty("hasExpiration");
			description.addProperty("preferredVendorUuid");
			description.addProperty("preferredVendorName");
			description.addProperty("purchasePrice");
			description.addProperty("purchasePriceUoMUuid");
			description.addProperty("purchasePriceUoMName");
			description.addProperty("purchasePriceUoMFactor");
			description.addProperty("dispensingUnitName");
			description.addProperty("dispensingUnitUuid");
			description.addProperty("dispensingUnitPackagingUoMUuid");
			description.addProperty("dispensingUnitPackagingUoMName");
			description.addProperty("dispensingUnitPackagingUoMFactor");
			description.addProperty("defaultStockOperationsUoMUuid");
			description.addProperty("defaultStockOperationsUoMName");
			description.addProperty("defaultStockOperationsUoMFactor");
			description.addProperty("categoryUuid");
			description.addProperty("categoryName");
			description.addProperty("commonName");
			description.addProperty("acronym");
			description.addProperty("reorderLevel");
			description.addProperty("reorderLevelUoMUuid");
			description.addProperty("reorderLevelUoMName");
			description.addProperty("reorderLevelUoMFactor");
			description.addProperty("dateCreated");
			description.addProperty("creatorGivenName");
			description.addProperty("creatorFamilyName");
			description.addProperty("voided");
			description.addProperty("expiryNotice");
			description.addProperty("levelOfUse");
			description.addProperty("genericConceptCode");
			description.addProperty("etcdProductId");
			description.addProperty("ppbRegistrationCode");
			description.addProperty("packageCode");
			// New: exposes the canonical item type in responses.
			// Consumers that previously relied on isDrug can check:
			//   itemType === "PHARMACEUTICAL"  ↔  isDrug === true
			//   itemType === "NON_PHARMACEUTICAL" ↔  isDrug === false
			//   itemType === "LAB_COMMODITY"   ↔  new
			description.addProperty("itemType");
		}
		
		if (rep instanceof DefaultRepresentation) {
			description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
		}
		
		if (rep instanceof FullRepresentation) {
			description.addProperty("permission");
			description.addProperty("packagingUnits");
			description.addProperty("references");
			description.addSelfLink();
		}
		
		if (rep instanceof RefRepresentation) {
			description.addProperty("uuid");
			description.addProperty("drugUuid");
			description.addProperty("drugName");
			description.addProperty("conceptUuid");
			description.addProperty("conceptName");
			// Include itemType in ref so list pages can filter client-side without a full fetch
			description.addProperty("itemType");
		}
		
		return description;
	}
	
	@PropertyGetter("packagingUnits")
	public Collection<StockItemPackagingUOMDTO> getStockOperationItems(StockItemDTO stockItemDTO) {
		if (stockItemDTO.getStockItemPackagingUOMs() != null)
			return stockItemDTO.getStockItemPackagingUOMs();
		StockItemPackagingUOMSearchFilter filter = new StockItemPackagingUOMSearchFilter();
		filter.setIncludeVoided(false);
		filter.setStockItemIds(Arrays.asList(stockItemDTO.getId()));
		List<StockItemPackagingUOMDTO> packagingUnits = getStockManagementService().findStockItemPackagingUOMs(filter)
		        .getData();
		return packagingUnits;
	}
	
	@PropertyGetter("references")
	public Collection<StockItemReferenceDTO> getStockItemReferences(StockItemDTO stockItemDTO) {
		List<StockItemReferenceDTO> stockItemReferenceDTOS = new ArrayList<>();
		for (StockItemReference stockItemReference : getStockManagementService().getStockItemReferenceByStockItem(stockItemDTO.getUuid())) {
			StockItemReferenceResource stockItemReferenceResource = new StockItemReferenceResource();
			stockItemReferenceDTOS.add(stockItemReferenceResource.convertToDTO(stockItemReference));
		}
		return stockItemReferenceDTOS;
	}
	
	@PropertyGetter("permission")
	public SimpleObject getPermission(StockItemDTO stockItemDTO) {
		SimpleObject simpleObject = new SimpleObject();
		simpleObject.add("canView", true);
		boolean canEdit = Context.getAuthenticatedUser().hasPrivilege(Privileges.TASK_STOCKMANAGEMENT_STOCKITEMS_MUTATE);
		simpleObject.add("canEdit", canEdit);
		return simpleObject;
	}
	
	@Override
	public Model getGETModel(Representation rep) {
		ModelImpl modelImpl = (ModelImpl) super.getGETModel(rep);
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			modelImpl.property("uuid", new StringProperty())
			        .property("drugUuid", new StringProperty())
			        .property("drugName", new StringProperty())
			        .property("conceptUuid", new StringProperty())
			        .property("conceptName", new StringProperty())
			        .property("hasExpiration", new BooleanProperty())
			        .property("preferredVendorUuid", new StringProperty())
			        .property("preferredVendorName", new StringProperty())
			        .property("purchasePrice", new DecimalProperty())
			        .property("purchasePriceUoMUuid", new StringProperty())
			        .property("purchasePriceUoMName", new StringProperty())
			        .property("purchasePriceUoMFactor", new DecimalProperty())
			        .property("dispensingUnitName", new StringProperty())
			        .property("dispensingUnitUuid", new StringProperty())
			        .property("dispensingUnitPackagingUoMUuid", new StringProperty())
			        .property("dispensingUnitPackagingUoMName", new StringProperty())
			        .property("dispensingUnitPackagingUoMFactor", new DecimalProperty())
			        .property("defaultStockOperationsUoMUuid", new StringProperty())
			        .property("defaultStockOperationsUoMName", new StringProperty())
			        .property("defaultStockOperationsUoMFactor", new DecimalProperty())
			        .property("categoryUuid", new StringProperty())
			        .property("categoryName", new StringProperty())
			        .property("dateCreated", new DateTimeProperty())
			        .property("creatorGivenName", new StringProperty())
			        .property("creatorFamilyName", new StringProperty())
			        .property("voided", new BooleanProperty())
			        .property("commonName", new StringProperty())
			        .property("acronym", new StringProperty())
			        .property("reorderLevel", new DecimalProperty())
			        .property("reorderLevelUoMUuid", new StringProperty())
			        .property("reorderLevelUoMName", new StringProperty())
			        .property("reorderLevelUoMFactor", new DecimalProperty())
			        .property("expiryNotice", new IntegerProperty())
			        .property("levelOfUse", new StringProperty())
			        .property("genericConceptCode", new StringProperty())
			        .property("etcdProductId", new StringProperty())
			        .property("ppbRegistrationCode", new StringProperty())
			        .property("packageCode", new StringProperty())
			        // New: enum string – one of PHARMACEUTICAL, NON_PHARMACEUTICAL, LAB_COMMODITY
			        .property("itemType", new StringProperty()
			                ._enum(Arrays.asList("PHARMACEUTICAL", "NON_PHARMACEUTICAL", "LAB_COMMODITY"))
			                .description("Canonical item type. Replaces the legacy isDrug boolean. "
			                        + "PHARMACEUTICAL ≡ isDrug=true, NON_PHARMACEUTICAL ≡ isDrug=false, LAB_COMMODITY is new."));
		}

		if (rep instanceof RefRepresentation) {
			modelImpl.property("uuid", new StringProperty())
			        .property("drugUuid", new StringProperty())
			        .property("drugName", new StringProperty())
			        .property("conceptUuid", new StringProperty())
			        .property("conceptName", new StringProperty())
			        .property("itemType", new StringProperty()
			                ._enum(Arrays.asList("PHARMACEUTICAL", "NON_PHARMACEUTICAL", "LAB_COMMODITY")));
		}
		
		return modelImpl;
	}
}