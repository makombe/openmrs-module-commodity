package org.openmrs.module.stockmanagement.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.DailyStockLineItemDTO;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.nlmis.NlmisHttpClientService;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.*;

public class DailyStockStatus extends AbstractTask {

	private static final Logger log = LoggerFactory.getLogger(DailyStockStatus.class);
	private static Boolean debugMode = false;

	@Override
	public void execute() {
		log.info("Pushing daily stock status TASK and TNT Events");

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
		debugMode = GlobalProperties.isLoggingEnabled();
		StockManagementService stockService = Context.getService(StockManagementService.class);
		Result<DailyStockLineItemDTO> result = stockService.getDailyDispensedStockStatus(new Date());

		if (result == null || result.getData() == null || result.getData().isEmpty()) {
			if (debugMode) System.out.println("No daily stock data found. Skipping submission.");
			return;
		}

		List<Map<String, Object>> lineItems = new ArrayList<>();
		for (DailyStockLineItemDTO item : result.getData()) {
			int soh = item.getStockOnHand() != null ? item.getStockOnHand().intValue() : 0;
			int recv = item.getQuantityReceived() != null ? item.getQuantityReceived().intValue() : 0;
			int disp = item.getQuantityDispensed() != null ? item.getQuantityDispensed().intValue() : 0;

			if (soh == 0 && recv == 0 && disp == 0) {
				continue;
			}

			// guard against invalid product codes
			if (item.getProductCode() == null || item.getProductCode().trim().isEmpty()) {
				log.warn("Skipping item with missing/invalid productCode");
				continue;
			}

			Map<String, Object> lineItem = new LinkedHashMap<>();
			lineItem.put("productCode", item.getProductCode());
			lineItem.put("stockOnHand", soh);
			lineItem.put("quantityReceived", recv);
			lineItem.put("quantityDispensed", disp);
			lineItem.put("fromFacilityId", "");
			lineItem.put("notes", item.getNotes() != null && !item.getNotes().isEmpty()
				? item.getNotes()
				: "Daily consumption update");

			lineItems.add(lineItem);
		}
		String hfrCode = getGlobalProperty("kenyaemr.hie.facility.registry.code", "FID-UNKNOWN");
		String program = getGlobalProperty("nlmis.program.code", "PHAR017");
		String sourceApp = getGlobalProperty("nlmis.source.application", "");
		String submitEndpoint = getGlobalProperty("nlmis.daily.stock.status.submit.endpoint", "");

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
			if (debugMode) System.out.println("NLMIS submission returned null response.");
		}

		int statusCode = response.getStatusCodeValue();
		String responseBody = response.getBody();

		if (statusCode >= 200 && statusCode < 300) {
			log.info("Daily stock status submitted successfully. HTTP {}, Response: {}",
				statusCode, responseBody);
			System.out.println("Daily stock status submitted successfully. =================== ");

		} 
		else {
			log.error("Daily stock status submission FAILED. HTTP {}, Response: {}",
				statusCode, responseBody);
			System.out.println("Daily stock status submission FAILED. ======== " + statusCode + " - " + responseBody);
//			throw new RuntimeException(
			//	"NLMIS submission failed with HTTP " + statusCode + ": " + responseBody);
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
