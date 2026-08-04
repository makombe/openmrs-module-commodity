package org.openmrs.module.stockmanagement.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/rest/" + RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID)
public class StockManagementResourceController extends MainResourceController {

    private static final String DESTINATION_SGLN_IDENTIFIER_PROPERTY = "tnt.facility.events.destination.sgln.identifier";

    /**
     * @see org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController#getNamespace()
     */
    @Override
    public String getNamespace() {
        return "v1/stockmanagement";
    }

    /**
     * Returns the configured GS1 SGLN identifier for the destination (receiving facility),
     * read from the tnt.facility.events.destination.sgln.identifier global property.
     * Consumed by the frontend Track and Trace / receipt forms.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/trackandtraceconfig/destinationsgln")
    @ResponseBody
    public Object getDestinationSglnIdentifier() {
        String value = Context.getAdministrationService().getGlobalProperty(DESTINATION_SGLN_IDENTIFIER_PROPERTY);
        SimpleObject response = new SimpleObject();
        response.put("property", DESTINATION_SGLN_IDENTIFIER_PROPERTY);
        response.put("value", value);
        return response;
    }
}