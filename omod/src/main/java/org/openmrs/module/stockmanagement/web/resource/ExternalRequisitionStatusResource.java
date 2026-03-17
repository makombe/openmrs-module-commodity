package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.StringProperty;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.stockmanagement.api.dto.ExternalRequisitionStatusDTO;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.model.ExternalRequisitionStatus;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.Date;
import java.util.UUID;

@Resource(name = RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID + "/externalrequisitionstatus",
        supportedClass = ExternalRequisitionStatus.class,
        supportedOpenmrsVersions = { "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*" })
public class ExternalRequisitionStatusResource extends ResourceBase<ExternalRequisitionStatus> {

    @Override
    public ExternalRequisitionStatus getByUniqueId(String uniqueId) {
        return getStockManagementService().getExternalRequisitionStatusByUuid(uniqueId);
    }

    @Override
    protected void delete(ExternalRequisitionStatus delegate, String reason, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public void purge(ExternalRequisitionStatus delegate, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public ExternalRequisitionStatus newDelegate() {
        return new ExternalRequisitionStatus();
    }

    /**
     * Handles POST /externalrequisitionstatus
     * Saves a new external requisition status record.
     */
    @Override
    public ExternalRequisitionStatus save(ExternalRequisitionStatus delegate) {
        if (StringUtils.isBlank(delegate.getUuid())) {
            delegate.setUuid(UUID.randomUUID().toString());
        }
        if (delegate.getDateCreated() == null) {
            delegate.setDateCreated(new Date());
        }
        if (delegate.getCreator() == null && Context.getAuthenticatedUser() != null) {
            delegate.setCreator(Context.getAuthenticatedUser().getId());
        }
        delegate.setDateUpdated(new Date());
        return getStockManagementService().saveExternalRequisitionStatus(delegate);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
        // Optional: support filtering by status or source
        String status = context.getParameter("status");
        String source = context.getParameter("source");
        Result<ExternalRequisitionStatusDTO> result =
                getStockManagementService().findExternalRequisitionStatuses(status, source, context.getIncludeAll());
        return toAlreadyPaged(result, context);
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return doSearch(context);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();

        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            description.addProperty("uuid");
            description.addProperty("message");
            description.addProperty("status");
            description.addProperty("source");
            description.addProperty("retired");
            description.addProperty("dateCreated");
            description.addProperty("dateUpdated");
            description.addProperty("operationNumber");
            description.addProperty("receiptNumber");
            description.addProperty("receiptMessage");
            description.addProperty("deliveryStatus");
            description.addProperty("podNotificationStatus");
        }

        if (rep instanceof DefaultRepresentation) {
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        }

        if (rep instanceof FullRepresentation) {
            description.addProperty("creator");
            description.addSelfLink();
        }

        if (rep instanceof RefRepresentation) {
            description.addProperty("uuid");
            description.addProperty("status");
        }

        return description;
    }

    @PropertyGetter("message")
    public String getMessage(ExternalRequisitionStatus instance) {
        return instance.getMessage();
    }

    @PropertySetter("message")
    public void setMessage(ExternalRequisitionStatus instance, String message) {
        instance.setMessage(message);
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("message");
        description.addProperty("status");
        description.addProperty("source");
        description.addProperty("retired");
        description.addProperty("operationNumber");
        description.addProperty("receiptNumber");
        description.addProperty("receiptMessage");
        description.addProperty("deliveryStatus");
        description.addProperty("podNotificationStatus");
        return description;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("message");
        description.addProperty("status");
        description.addProperty("source");
        description.addProperty("retired");
        description.addProperty("operationNumber");
        description.addProperty("receiptNumber");
        description.addProperty("receiptMessage");
        description.addProperty("deliveryStatus");
        description.addProperty("podNotificationStatus");
        return description;
    }

    @Override
    public Model getGETModel(Representation rep) {
        ModelImpl modelImpl = (ModelImpl) super.getGETModel(rep);

        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            modelImpl
                    .property("uuid", new StringProperty())
                    .property("message", new StringProperty())
                    .property("status", new StringProperty())
                    .property("source", new StringProperty())
                    .property("retired", new IntegerProperty())
                    .property("dateCreated", new DateTimeProperty())
                    .property("dateUpdated", new DateTimeProperty())
                    .property("operationNumber", new IntegerProperty())
                    .property("receiptNumber", new StringProperty())
                    .property("receiptMessage", new StringProperty())
                    .property("deliveryStatus", new StringProperty())
                    .property("podNotificationStatus", new StringProperty());
        }

        if (rep instanceof FullRepresentation) {
            modelImpl.property("creator", new IntegerProperty());
        }

        if (rep instanceof RefRepresentation) {
            modelImpl
                    .property("uuid", new StringProperty())
                    .property("status", new StringProperty());
        }

        return modelImpl;
    }

    @Override
    public Model getCREATEModel(Representation rep) {
        return new ModelImpl()
                .property("message", new StringProperty())
                .property("status", new StringProperty())
                .property("source", new StringProperty())
                .property("retired", new IntegerProperty())
                .property("operationNumber", new IntegerProperty())
                .property("receiptNumber", new StringProperty())
                .property("receiptMessage", new StringProperty())
                .property("deliveryStatus", new StringProperty())
                .property("podNotificationStatus", new StringProperty());
    }

    @Override
    public Model getUPDATEModel(Representation rep) {
        return getCREATEModel(rep);
    }
}