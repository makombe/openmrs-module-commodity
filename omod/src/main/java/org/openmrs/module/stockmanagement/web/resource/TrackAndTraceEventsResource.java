
package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.StringProperty;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.TrackAndTraceEventsDTO;
import org.openmrs.module.stockmanagement.api.model.TrackAndTraceEvents;
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

import java.util.Date;
import java.util.UUID;

@Resource(name = RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID
        + "/trackandtraceevent", supportedClass = TrackAndTraceEvents.class, supportedOpenmrsVersions = {
                "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*" })
public class TrackAndTraceEventsResource extends ResourceBase<TrackAndTraceEvents> {

    @Override
    public TrackAndTraceEvents getByUniqueId(String uniqueId) {
        return getStockManagementService().getTrackAndTraceEventByUuid(uniqueId);
    }

    @Override
    protected void delete(TrackAndTraceEvents delegate, String reason, RequestContext context)
            throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public void purge(TrackAndTraceEvents delegate, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public TrackAndTraceEvents newDelegate() {
        return new TrackAndTraceEvents();
    }

    @Override
    public TrackAndTraceEvents save(TrackAndTraceEvents delegate) {
        if (StringUtils.isBlank(delegate.getUuid())) {
            delegate.setUuid(UUID.randomUUID().toString());
        }
        if (delegate.getDateCreated() == null) {
            delegate.setDateCreated(new Date());
        }
        if (delegate.getRetired() == null) {
            delegate.setRetired(0);
        }
        if (delegate.getCreator() == null && Context.getAuthenticatedUser() != null) {
            delegate.setCreator(Context.getAuthenticatedUser().getId());
        }
        delegate.setDateUpdated(new Date());
        return getStockManagementService().saveTrackAndTraceEvent(delegate);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
        String eventId = context.getParameter("eventId");
        String eventType = context.getParameter("eventType");
        String bizType = context.getParameter("bizType");
        String status = context.getParameter("status");
        String reference = context.getParameter("reference");
        String dateFrom = context.getParameter("dateFrom");
        String dateTo = context.getParameter("dateTo");

        Result<TrackAndTraceEventsDTO> result = getStockManagementService()
                .findTrackAndTraceEvents(eventId, eventType, bizType, status,
                        reference, dateFrom, dateTo, context.getIncludeAll());
        return toAlreadyPaged(result, context);
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return doSearch(context);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();

        description.addProperty("uuid");
        description.addProperty("eventId");
        description.addProperty("eventType");
        description.addProperty("bizType");
        description.addProperty("status");
        description.addProperty("reference");
        description.addProperty("eventTime");
        description.addProperty("message");
        description.addProperty("errorMessage");
        description.addProperty("retired");
        description.addProperty("dateCreated");
        description.addProperty("dateUpdated");

        if (rep instanceof DefaultRepresentation) {
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        }

        if (rep instanceof FullRepresentation) {
            description.addProperty("creator");
            description.addSelfLink();
        }

        return description;
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties()
            throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addRequiredProperty("eventId");
        description.addRequiredProperty("eventType");
        description.addProperty("bizType");
        description.addProperty("status");
        description.addProperty("reference");
        description.addProperty("eventTime");
        description.addProperty("message");
        description.addProperty("errorMessage");
        description.addProperty("retired");
        return description;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties()
            throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("eventType");
        description.addProperty("bizType");
        description.addProperty("status");
        description.addProperty("reference");
        description.addProperty("eventTime");
        description.addProperty("message");
        description.addProperty("errorMessage");
        description.addProperty("retired");
        return description;
    }

    @Override
    public Model getGETModel(Representation rep) {
        ModelImpl modelImpl = (ModelImpl) super.getGETModel(rep);

        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            modelImpl
                    .property("uuid", new StringProperty())
                    .property("eventId", new StringProperty())
                    .property("eventType", new StringProperty())
                    .property("bizType", new StringProperty())
                    .property("status", new StringProperty())
                    .property("reference", new StringProperty())
                    .property("eventTime", new DateTimeProperty())
                    .property("message", new StringProperty())
                    .property("errorMessage", new StringProperty())
                    .property("retired", new IntegerProperty())
                    .property("dateCreated", new DateTimeProperty())
                    .property("dateUpdated", new DateTimeProperty());
        }

        if (rep instanceof FullRepresentation) {
            modelImpl.property("creator", new IntegerProperty());
        }

        if (rep instanceof RefRepresentation) {
            modelImpl
                    .property("uuid", new StringProperty())
                    .property("eventId", new StringProperty())
                    .property("eventType", new StringProperty())
                    .property("status", new StringProperty());
        }

        return modelImpl;
    }

    @Override
    public Model getCREATEModel(Representation rep) {
        return new ModelImpl()
                .property("eventId", new StringProperty().required(true))
                .property("eventType", new StringProperty().required(true))
                .property("bizType", new StringProperty())
                .property("status", new StringProperty())
                .property("reference", new StringProperty())
                .property("eventTime", new DateTimeProperty())
                .property("message", new StringProperty())
                .property("errorMessage", new StringProperty())
                .property("retired", new IntegerProperty());
    }

    @Override
    public Model getUPDATEModel(Representation rep) {
        return new ModelImpl()
                .property("eventType", new StringProperty())
                .property("bizType", new StringProperty())
                .property("status", new StringProperty())
                .property("reference", new StringProperty())
                .property("eventTime", new DateTimeProperty())
                .property("message", new StringProperty())
                .property("errorMessage", new StringProperty())
                .property("retired", new IntegerProperty());
    }
}