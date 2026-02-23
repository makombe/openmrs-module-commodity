package org.openmrs.module.stockmanagement.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.DailyStockLineItemDTO;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.nlmis.NlmisHttpClientService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.text.SimpleDateFormat;
import java.util.*;

public class DailyStockStatus extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(DailyStockStatus.class);

    @Override
    public void execute() {
        log.info("Pushing daily stock status TASK");

        if (!Context.isSessionOpen()) {
            Context.openSession();
        }

        // Add privileges BEFORE the try block so they're always removed in finally
        Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);

        try {
            doExecute();
        } catch (Exception e) {
            log.error("Unexpected error during daily stock status submission", e);
            throw new RuntimeException("Daily stock status task failed: " + e.getMessage(), e);
        } finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }

    private void doExecute() throws Exception {
        StockManagementService stockService = Context.getService(StockManagementService.class);
        Result<DailyStockLineItemDTO> result = stockService.getDailyDispensedStockStatus(new Date());

        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            log.warn("No daily stock data found. Skipping submission.");
            return;
        }

        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (DailyStockLineItemDTO item : result.getData()) {
            Map<String, Object> lineItem = new LinkedHashMap<>();
            lineItem.put("productCode", item.getProductCode());
            lineItem.put("stockOnHand", item.getStockOnHand() != null ? item.getStockOnHand().intValue() : 0);
            lineItem.put("quantityReceived",
                    item.getQuantityReceived() != null ? item.getQuantityReceived().intValue() : 0);
            lineItem.put("quantityDispensed",
                    item.getQuantityDispensed() != null ? item.getQuantityDispensed().intValue() : 0);
            lineItem.put("fromFacilityId", "");
            lineItem.put("notes", (item.getNotes() != null && !item.getNotes().isEmpty())
                    ? item.getNotes()
                    : "Daily consumption update");
            lineItems.add(lineItem);
        }

        String hfrCode = getGlobalProperty("kenyaemr.hie.facility.registry.code", "FID-UNKNOWN");
        String program = getGlobalProperty("nlmis.program.code", "PHAR017");
        String sourceApp = getGlobalProperty("nlmis.source.application", "");
        String submitEndpoint = getGlobalProperty("nlmis.daily.stock.status.submit.endpoint", "");

        if (submitEndpoint.isEmpty()) {
            log.error("Global property 'nlmis.daily.stock.status.submit.endpoint' is not configured.");
            return;
        }

        String lastUpdatedDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hfrCode", hfrCode);
        payload.put("program", program);
        payload.put("sourceApplication", sourceApp);
        payload.put("lastUpdatedDateTime", lastUpdatedDateTime);
        payload.put("lineItems", lineItems);

        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
        log.info("Submitting daily stock status payload: {}", jsonPayload);

        NlmisHttpClientService service = new NlmisHttpClientService();
        ResponseEntity<String> response = service.executePost(submitEndpoint, jsonPayload);

        if (response == null) {
            throw new RuntimeException("NLMIS submission returned null response.");
        }

        int statusCode = response.getStatusCodeValue();
        String responseBody = response.getBody();

        if (statusCode >= 200 && statusCode < 300) {
            log.info("Daily stock status submitted successfully. HTTP {}, Response: {}",
                    statusCode, responseBody);
        System.out.println("Daily stock status submitted successfully. =================== ");

        } else {
            log.error("Daily stock status submission FAILED. HTTP {}, Response: {}",
                    statusCode, responseBody);
            System.out.println("Daily stock status submission FAILED. ======== " + statusCode + " - " + responseBody);
            throw new RuntimeException(
                    "NLMIS submission failed with HTTP " + statusCode + ": " + responseBody);
        }
    }

    /**
     * Safely retrieves a global property, returning a fallback value if not set.
     */
    private String getGlobalProperty(String propertyName, String fallback) {
        String value = Context.getAdministrationService().getGlobalProperty(propertyName);
        return (value != null && !value.isEmpty()) ? value : fallback;
    }
}