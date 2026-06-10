package org.openmrs.module.stockmanagement.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.DailyStockLineItemDTO;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.nlmis.NlmisHttpClientService;
import org.openmrs.module.stockmanagement.api.tnt.TntHttpClientService;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import java.text.SimpleDateFormat;
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

		// Track and trace events
		// Generate and push receipt event
		 	
		if (debugMode) System.out.println("Submitting tnt receipt status payload: {}" + epicsReceiptPayloadBuilder().toString());

		TntHttpClientService tntService = new TntHttpClientService();
		String tntJsonPayload = epicsReceiptPayloadBuilder().toString();
		String tntReceiptEndpoint = "";
		ResponseEntity<String> tntResponse = tntService.executePost(tntReceiptEndpoint, tntJsonPayload);

	if (tntResponse == null) {
			//throw new RuntimeException("TNT submission returned null response.");
		if (debugMode) System.out.println("TNT submission returned null response.");
	}

		int tntStatusCode = tntResponse.getStatusCodeValue();
		String tntResponseBody = tntResponse.getBody();

		if (tntStatusCode >= 200 && tntStatusCode < 300) {
			if (debugMode) System.out.println("TnT receipt event submitted successfully. HTTP {}, Response: {}" +
				statusCode + tntResponseBody);
			if (debugMode) System.out.println("TnT receipt event submitted successfully. =================== ");

		} else {
			if (debugMode) System.out.println("TnT receipt event submission FAILED. HTTP {}, Response: {}" +
				statusCode + tntResponseBody);
			if (debugMode)
				System.out.println("TnT receipt event submission FAILED. ======== " + tntStatusCode + " - " + tntResponseBody);
//			throw new RuntimeException(
//				"TnT receipt event submission failed with HTTP " + tntStatusCode + ": " + tntResponseBody);
		}

		// Generate and push dispense event

		if (debugMode) System.out.println("Submitting tnt receipt status payload: {}" + buildDispensePayload().toString());

	
		String tntDispenseJsonPayload = buildDispensePayload().toString();
		String tntDispenseEndpoint = "/dispense";
		ResponseEntity<String> tntDispenseResponse = tntService.executePost(tntDispenseEndpoint, tntDispenseJsonPayload);

		if (tntDispenseResponse == null) {
			//throw new RuntimeException("TNT submission returned null response.");
			if (debugMode) System.out.println("TNT Dispense submission returned null response.");
		}

		int tntDispenseStatusCode = tntDispenseResponse.getStatusCodeValue();
		String tntDispenseResponseBody = tntDispenseResponse.getBody();

		if (tntDispenseStatusCode >= 200 && tntDispenseStatusCode < 300) {
			if (debugMode) System.out.println("TnT dispense event submitted successfully. HTTP {}, Response: {}" +
				tntDispenseStatusCode + tntDispenseResponseBody);
			if (debugMode) System.out.println("TnT dispense event submitted successfully. =================== ");

		} else {
			if (debugMode) System.out.println("TnT receipt event submission FAILED. HTTP {}, Response: {}" +
				tntDispenseStatusCode + tntDispenseResponseBody);
			if (debugMode)
				System.out.println("TnT receipt event submission FAILED. ======== " + tntDispenseStatusCode + " - " + tntDispenseResponseBody);
//			throw new RuntimeException(
//				"TnT receipt event submission failed with HTTP " + tntStatusCode + ": " + tntResponseBody);
		}

	}

	/**
	 * Safely retrieves a global property, returning a fallback value if not set.
	 */
	private String getGlobalProperty(String propertyName, String fallback) {
		String value = Context.getAdministrationService().getGlobalProperty(propertyName);
		return (value != null && !value.isEmpty()) ? value : fallback;
	}

	private ObjectNode epicsReceiptPayloadBuilder() throws JsonProcessingException {

		String tntEventsPackagingSscc = getGlobalProperty("tnt.facility.events.sscc.identifier", "");
		String tntEventsDestinationSgln = getGlobalProperty("tnt.facility.events.destination.sgln.identifier", "");
		String tntEventsSourceSgln = getGlobalProperty("tnt.facility.events.source.sgln.identifier", "");
		String tntEventsProductGtin = getGlobalProperty("tnt.facility.events.sgtin.identifier", "");
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode payload = mapper.createObjectNode();

		// @context
		ArrayNode contextArray = mapper.createArrayNode();
		contextArray.add("https://ref.gs1.org/standards/epcis/epcis-context.jsonld");
		payload.set("@context", contextArray);

		payload.put("type", "EPCISDocument");
		payload.put("schemaVersion", "2.0");

		// timestamps (ISO-like)
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.HOUR_OF_DAY, -3);

		String now = sdf.format(cal.getTime());

		payload.put("creationDate", now);

		// epcisBody
		ObjectNode epcisBody = mapper.createObjectNode();

		ArrayNode eventList = mapper.createArrayNode();

		ObjectNode event = mapper.createObjectNode();

		event.put("type", "ObjectEvent");
		event.put("eventID", "urn:uuid:" + UUID.randomUUID());
		event.put("eventTime", now);
		event.put("eventTimeZoneOffset", "+03:00");

		// epcList
		ArrayNode epcList = mapper.createArrayNode();
		epcList.add("urn:epc:id:sscc:"+tntEventsPackagingSscc);
		event.set("epcList", epcList);

		event.put("action", "OBSERVE");
		event.put("bizStep", "receiving");
		event.put("disposition", "active");

		// readPoint
		ObjectNode readPoint = mapper.createObjectNode();
		readPoint.put("id", "urn:epc:id:sgln:"+tntEventsDestinationSgln);
		event.set("readPoint", readPoint);

		// bizLocation
		ObjectNode bizLocation = mapper.createObjectNode();
		bizLocation.put("id", "urn:epc:id:sgln:"+tntEventsDestinationSgln);
		event.set("bizLocation", bizLocation);

		// bizTransactionList
		ArrayNode bizTransactionList = mapper.createArrayNode();
		ObjectNode bizTransaction = mapper.createObjectNode();
		bizTransaction.put("type", "recadv");
		bizTransaction.put("bizTransaction", "urn:epcglobal:cbv:bt:0123456:GR789");
		bizTransactionList.add(bizTransaction);
		event.set("bizTransactionList", bizTransactionList);

		// sourceList
		ArrayNode sourceList = mapper.createArrayNode();
		ObjectNode source = mapper.createObjectNode();
		source.put("type", "owning_party");
		source.put("source", "urn:epc:id:sgln:"+tntEventsSourceSgln);
		sourceList.add(source);
		event.set("sourceList", sourceList);

		// destinationList
		ArrayNode destinationList = mapper.createArrayNode();
		ObjectNode destination = mapper.createObjectNode();
		destination.put("type", "owning_party");
		destination.put("destination", "urn:epc:id:sgln:"+tntEventsDestinationSgln);
		destinationList.add(destination);
		event.set("destinationList", destinationList);

		eventList.add(event);

		epcisBody.set("eventList", eventList);
		payload.set("epcisBody", epcisBody);

		// print JSON
		System.out.println("TNT Receive Event ==> "+mapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString(payload));
		
		return payload;
	}

	private ObjectNode buildDispensePayload() throws JsonProcessingException {

		String tntEventsPackagingSscc = getGlobalProperty("tnt.facility.events.sscc.identifier", "");
		String tntEventsDestinationSgln = getGlobalProperty("tnt.facility.events.destination.sgln.identifier", "");
		String tntEventsSourceSgln = getGlobalProperty("tnt.facility.events.source.sgln.identifier", "");
		String tntEventsProductSgtin = getGlobalProperty("tnt.facility.events.sgtin.identifier", "");

		ObjectMapper mapper = new ObjectMapper();

		// Current time minus 3 hours
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.add(Calendar.HOUR_OF_DAY, -3);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		String timestamp = sdf.format(cal.getTime());
		ObjectNode payload = mapper.createObjectNode();

		// @context
		ArrayNode contextArray = mapper.createArrayNode();
		contextArray.add("https://ref.gs1.org/standards/epcis/epcis-context.jsonld");
		payload.set("@context", contextArray);

		payload.put("type", "EPCISDocument");
		payload.put("schemaVersion", "2.0");
		payload.put("creationDate", timestamp);

		// EPCIS Body
		ObjectNode epcisBody = mapper.createObjectNode();
		ArrayNode eventList = mapper.createArrayNode();

		ObjectNode event = mapper.createObjectNode();

		event.put("type", "ObjectEvent");
		event.put("eventID", "urn:uuid:" + UUID.randomUUID());
		event.put("eventTime", timestamp);
		event.put("eventTimeZoneOffset", "+03:00");

		// epcList
		ArrayNode epcList = mapper.createArrayNode();
		epcList.add("urn:epc:id:sgtin:"+tntEventsProductSgtin+".000000010");		
		event.set("epcList", epcList);

		// quantityList
		ArrayNode quantityList = mapper.createArrayNode();

		ObjectNode quantity = mapper.createObjectNode();
		quantity.put("epcClass", "urn:epc:idpat:sgtin:"+tntEventsProductSgtin+".*");
		quantity.put("quantity", 1);
		quantity.put("uom", "EA");

		quantityList.add(quantity);
		event.set("quantityList", quantityList);

		event.put("action", "OBSERVE");
		event.put("bizStep", "dispensing");
		event.put("disposition", "dispensed");

		// readPoint
		ObjectNode readPoint = mapper.createObjectNode();
		readPoint.put("id", "urn:epc:id:sgln:"+tntEventsDestinationSgln);
		event.set("readPoint", readPoint);

		// bizLocation
		ObjectNode bizLocation = mapper.createObjectNode();
		bizLocation.put("id", "urn:epc:id:sgln:"+tntEventsDestinationSgln);
		event.set("bizLocation", bizLocation);

		eventList.add(event);
		epcisBody.set("eventList", eventList);
		payload.set("epcisBody", epcisBody);

		// print JSON
		System.out.println("TNT Dispense Event ==> "+mapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString(payload));
		return payload;
	}
}
