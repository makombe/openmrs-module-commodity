package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.*;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.stockmanagement.api.dto.DailyStockLineItemDTO;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.stockmanagement.api.dto.Result;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Resource(name = RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID
        + "/dailystockstatus", supportedClass = DailyStockLineItemDTO.class, supportedOpenmrsVersions = { "1.9.*",
                "1.10.*", "1.11.*", "1.12.*", "2.*" })
public class DailyStockStatusResource extends ResourceBase<DailyStockLineItemDTO> {

    @Override
    public DailyStockLineItemDTO getByUniqueId(String uniqueId) {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    protected void delete(DailyStockLineItemDTO delegate, String reason, RequestContext context)
            throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return doSearch(context);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
        Date reportDate = new Date();
        String reportDateParam = context.getParameter("reportDate");

        if (reportDateParam != null && !reportDateParam.isEmpty()) {
            try {
                reportDate = new SimpleDateFormat("yyyy-MM-dd").parse(reportDateParam);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd, got: " + reportDateParam);
            }
        }

        Result<DailyStockLineItemDTO> result = getStockManagementService().getDailyDispensedStockStatus(reportDate);
        result.getData().forEach(item -> {
            if (item.getNotes() == null || item.getNotes().isEmpty()) {
                item.setNotes("Daily consumption update");
            }
        });

        return toAlreadyPaged(result, context);
    }

    @Override
    public DailyStockLineItemDTO newDelegate() {
        return new DailyStockLineItemDTO();
    }

    @Override
    public DailyStockLineItemDTO save(DailyStockLineItemDTO delegate) {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public void purge(DailyStockLineItemDTO delegate, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();

        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            description.addProperty("productCode");
            description.addProperty("stockOnHand");
            description.addProperty("quantityReceived");
            description.addProperty("quantityDispensed");
            description.addProperty("notes");
        }

        if (rep instanceof DefaultRepresentation) {
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        }

        if (rep instanceof FullRepresentation) {
            description.addSelfLink();
        }

        if (rep instanceof RefRepresentation) {
            description.addProperty("productCode");
        }

        return description;
    }
    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addRequiredProperty("lineItems");
        return description;
    }

    @Override
    public Model getGETModel(Representation rep) {
        ModelImpl modelImpl = (ModelImpl) super.getGETModel(rep);
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            modelImpl
                    .property("productCode", new StringProperty())
                    .property("stockOnHand", new DecimalProperty())
                    .property("quantityReceived", new DecimalProperty())
                    .property("quantityDispensed", new DecimalProperty())
                    .property("notes", new StringProperty())
                    .property("reportDate", new DateProperty());
        }
        if (rep instanceof RefRepresentation) {
            modelImpl
                    .property("uuid", new StringProperty())
                    .property("productCode", new StringProperty());
        }
        return modelImpl;
    }

    @Override
    public Model getCREATEModel(Representation rep) {
        return new ModelImpl()
                .property("lineItems", new ArrayProperty(
                        new ObjectProperty()
                                .property("productCode", new StringProperty().required(true))
                                .property("stockOnHand", new DecimalProperty())
                                .property("quantityReceived", new DecimalProperty())
                                .property("quantityDispensed", new DecimalProperty())
                                .property("notes", new StringProperty())));
    }
}