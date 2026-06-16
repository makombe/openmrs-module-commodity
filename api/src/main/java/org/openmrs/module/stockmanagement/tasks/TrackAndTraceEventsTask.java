package org.openmrs.module.stockmanagement.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.TrackAndTraceEventsDTO;
import org.openmrs.module.stockmanagement.api.model.TrackAndTraceEvents;
import org.openmrs.module.stockmanagement.api.tnt.TntHttpClientService;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

public class TrackAndTraceEventsTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(TrackAndTraceEventsTask.class);

    private static final String STATUS_QUEUED = "queued";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String BIZ_TYPE_RECEIPT = "receipt";
    private static final String BIZ_TYPE_DISPENSE = "dispense";

    private boolean debugMode = false;

    @Override
    public void execute() {
        log.info("TrackAndTraceEventsTask: starting execution");

        if (!Context.isSessionOpen()) {
            Context.openSession();
        }

        Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);

        try {
            doExecute();
        } catch (Exception e) {
            log.error("Unexpected error during TNT events submission", e);
        } finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }

    private void doExecute() {
        debugMode = GlobalProperties.isLoggingEnabled();

        StockManagementService stockService = Context.getService(StockManagementService.class);
        String apiKey = Context.getAdministrationService()
                .getGlobalProperty("tnt.facility.events.submission.api.token", "");

        TntHttpClientService tntService = new TntHttpClientService();

        Result<TrackAndTraceEventsDTO> queuedResult = stockService.findTrackAndTraceEvents(
                null,
                null,
                null,
                STATUS_QUEUED,
                null,
                null,
                null,
                false);

        if (queuedResult.getData().isEmpty()) {
            log.info("TrackAndTraceEventsTask: no queued events found — nothing to process");
            return;
        }

        log.info("TrackAndTraceEventsTask: found {} queued event(s) to process", queuedResult.getData().size());

        for (TrackAndTraceEventsDTO dto : queuedResult.getData()) {
            try {
                processEvent(dto, stockService, tntService, apiKey);
            } catch (Exception e) {
                log.error("TrackAndTraceEventsTask: failed to process event uuid={}", dto.getUuid(), e);
                markFailed(dto, stockService, "Unexpected error: " + e.getMessage());
            }
        }
    }

    private void processEvent(
            TrackAndTraceEventsDTO dto,
            StockManagementService stockService,
            TntHttpClientService tntService,
            String apiKey) {

        String uuid = dto.getUuid();
        String bizType = dto.getBizType();

        TrackAndTraceEvents entity = stockService.getTrackAndTraceEventByUuid(uuid);
        if (entity == null) {
            log.warn("TrackAndTraceEventsTask: entity not found uuid={} — skipping", uuid);
            return;
        }

        String rawMessage = entity.getMessage();

        String payload = extractCleanJson(rawMessage);
        if (payload == null) {
            log.error("TrackAndTraceEventsTask: cannot extract valid JSON from message for uuid={}", uuid);
            markFailedEntity(entity, stockService, "Unparseable payload — no valid JSON object found");
            return;
        }

        if (debugMode) {
            log.debug("TrackAndTraceEventsTask: Processing event uuid={} payload={}", uuid, payload);
        } else {
            log.info("TrackAndTraceEventsTask: Processing event uuid={} bizType={}", uuid, bizType);
        }

        String endpoint;
        if (BIZ_TYPE_RECEIPT.equalsIgnoreCase(bizType)) {
            endpoint = "";
        } else if (BIZ_TYPE_DISPENSE.equalsIgnoreCase(bizType)) {
            endpoint = "/dispense";
        } else {
            log.warn("TrackAndTraceEventsTask: unknown biz_type='{}' uuid={}", bizType, uuid);
            markFailedEntity(entity, stockService, "Unknown biz_type: " + bizType);
            return;
        }

        ResponseEntity<String> response = tntService.executePost(endpoint, payload, apiKey);

        if (response == null) {
            log.error("TrackAndTraceEventsTask: null response for uuid={}", uuid);
            markFailedEntity(entity, stockService, "Null response from TNT");
            return;
        }

        int statusCode = response.getStatusCodeValue();
        String responseBody = response.getBody();

        if (statusCode >= 200 && statusCode < 300) {
            log.info("TrackAndTraceEventsTask: {} event uuid={} submitted successfully HTTP {}",
                    bizType, uuid, statusCode);
            // Restore clean payload before marking success so the record is tidy
            entity.setMessage(payload);
            markSuccessEntity(entity, stockService);
        } else {
            String reason = "HTTP " + statusCode + ": "
                    + (responseBody != null ? responseBody : "no body");
            log.error("TrackAndTraceEventsTask: {} event uuid={} submission FAILED. {}",
                    bizType, uuid, reason);
            entity.setMessage(payload);
            markFailedEntity(entity, stockService, reason);
        }
    }

    private void markFailed(TrackAndTraceEventsDTO dto, StockManagementService stockService, String reason) {
        updateStatus(dto, stockService, STATUS_FAILED, reason);
    }

    private void updateStatus(
            TrackAndTraceEventsDTO dto,
            StockManagementService stockService,
            String newStatus,
            String failureMessage) {
        try {
            TrackAndTraceEvents entity = stockService.getTrackAndTraceEventByUuid(dto.getUuid());
            if (entity == null) {
                log.warn("TrackAndTraceEventsTask: cannot update status — entity not found uuid={}", dto.getUuid());
                return;
            }
            entity.setStatus(newStatus);
            // Append failure reason to the existing message so the original
            // EPCIS payload is preserved for debugging
            if (failureMessage != null) {
                String existing = entity.getMessage() != null ? entity.getMessage() : "";
                entity.setMessage(existing + "\n[FAILURE] " + failureMessage);
            }
            stockService.saveTrackAndTraceEvent(entity);

            log.info("TrackAndTraceEventsTask: event uuid={} status updated to '{}'", dto.getUuid(), newStatus);
        } catch (Exception e) {
            log.error("TrackAndTraceEventsTask: failed to update status for event uuid={}", dto.getUuid(), e);
        }
    }

    private void markFailedEntity(TrackAndTraceEvents entity,
            StockManagementService stockService,
            String reason) {
        try {
            entity.setStatus(STATUS_FAILED);
            stockService.saveTrackAndTraceEvent(entity);
            log.info("TrackAndTraceEventsTask: uuid={} marked failed: {}", entity.getUuid(), reason);
        } catch (Exception e) {
            log.error("TrackAndTraceEventsTask: failed to persist failure status uuid={}",
                    entity.getUuid(), e);
        }
    }

    private void markSuccessEntity(TrackAndTraceEvents entity,
            StockManagementService stockService) {
        try {
            entity.setStatus(STATUS_SUCCESS);
            stockService.saveTrackAndTraceEvent(entity);
            log.info("TrackAndTraceEventsTask: uuid={} marked success", entity.getUuid());
        } catch (Exception e) {
            log.error("TrackAndTraceEventsTask: failed to persist success status uuid={}",
                    entity.getUuid(), e);
        }
    }

    private String extractCleanJson(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return null;

        String trimmed = raw.trim();

        if (!trimmed.startsWith("{")) {
            log.error("TrackAndTraceEventsTask: payload does not start with '{' — cannot extract JSON");
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int closeIndex = -1;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\' && inString) {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString)
                continue;

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    closeIndex = i;
                    break;
                }
            }
        }

        if (closeIndex == -1) {
            log.error("TrackAndTraceEventsTask: could not find closing brace in payload");
            return null;
        }

        return trimmed.substring(0, closeIndex + 1);
    }
}