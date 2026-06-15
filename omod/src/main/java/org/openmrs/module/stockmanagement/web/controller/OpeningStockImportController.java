package org.openmrs.module.stockmanagement.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.stockmanagement.api.ModuleConstants;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.OpeningStockImportResult;
import org.openmrs.module.stockmanagement.api.utils.FileUtil;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles bulk Opening Stock imports uploaded as a CSV file.
 *
 */
@Controller("${rootrootArtifactId}.OpeningStockImportController")
@RequestMapping("/rest/" + RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID + "/openingstockimport")
public class OpeningStockImportController {

        private static final Log log = LogFactory.getLog(OpeningStockImportController.class);

        // Status constants written into the result map
        public static final String STATUS_PENDING = "PENDING";
        public static final String STATUS_RUNNING = "RUNNING";
        public static final String STATUS_COMPLETED = "COMPLETED";
        public static final String STATUS_FAILED = "FAILED";

        /**
         * In-memory result store. Key = sessionId (userId_uuid).
         * Value = the live {@link OpeningStockImportResult} for that import.
         * Written by the background thread; read by the GET polling endpoint.
         */
        private static final Map<String, OpeningStockImportResult> SESSION_RESULTS = new ConcurrentHashMap<>();

        /**
         * Single-thread executor — opening stock migration is a one-at-a-time
         * administrative operation; there is no reason to run many concurrently.
         * Using a single thread also avoids contention on the location/item caches
         * built inside the import job.
         */
        private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "opening-stock-import");
                t.setDaemon(true);
                return t;
        });

        @RequestMapping(method = RequestMethod.POST)
        @ResponseBody
        public OpeningStockImportResult upload(
                        @RequestParam(value = "file") MultipartFile file,
                        HttpServletRequest request) {

                if (!Context.isAuthenticated()) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.stockoperation.authrequired"));
                }

                if (file == null || file.isEmpty()) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.nofileuploaded"));
                }

                if (file.getSize() > GlobalProperties.getStockItemsMaxUploadSize() * 1024L * 1024L) {
                        return errorResult(String.format(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.maxfilesizeexceeded"),
                                        GlobalProperties.getStockItemsMaxUploadSize()));
                }

                // Accept both CSV and xlsx. Browsers/OS may send xlsx as
                // "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                // "application/octet-stream", or even "application/zip".
                // The import job detects the actual format from the file magic bytes,
                // so we only reject content types that are clearly wrong (e.g. PDF, image).
                String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
                boolean isCsv = contentType.contains("csv") || contentType.contains("text/plain");
                boolean isXlsx = contentType.contains("spreadsheet") || contentType.contains("excel")
                                || contentType.contains("zip") || contentType.contains("octet-stream")
                                || contentType.isEmpty();
                if (!isCsv && !isXlsx) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.contenttypenotsupported"));
                }

                File workingDir = FileUtil.getWorkingDirectory();
                String sessionId = Context.getAuthenticatedUser().getUserId() + "_" + UUID.randomUUID();
                File filePath = new File(workingDir, sessionId);
                try {
                        file.transferTo(filePath);
                } catch (Exception ex) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.transferworkingdirfailed"));
                }

                String hasHeaderParam = request.getParameter("hasHeader");
                boolean hasHeader = hasHeaderParam != null
                                && (hasHeaderParam.equalsIgnoreCase("true") || hasHeaderParam.equals("1"));

                OpeningStockImportResult pending = new OpeningStockImportResult();
                pending.setStatus(STATUS_PENDING);
                pending.setUploadSessionId(sessionId);
                pending.setErrors(new ArrayList<>());
                SESSION_RESULTS.put(sessionId, pending);

                final UserContext userContext = Context.getUserContext();
                final Path importPath = filePath.toPath();
                final boolean finalHeader = hasHeader;
                final String finalSession = sessionId;

                EXECUTOR.submit(() -> {
                        pending.setStatus(STATUS_RUNNING);
                        try {
                                // Bind the uploading user's session to this background thread
                                // so Context.getAuthenticatedUser() works inside the import job.
                                Context.openSession();
                                Context.setUserContext(userContext);

                                StockManagementService svc = Context.getService(StockManagementService.class);
                                OpeningStockImportResult jobResult = svc.importOpeningStock(importPath, finalHeader);

                                // Copy fields into the already-registered result object so
                                // the polling endpoint sees the final state.
                                pending.setSuccess(jobResult.isSuccess());
                                pending.setOperationsCreated(jobResult.getOperationsCreated());
                                pending.setItemsImported(jobResult.getItemsImported());
                                pending.setErrors(jobResult.getErrors());
                                pending.setHasErrorFile(jobResult.isHasErrorFile());
                                pending.setStatus(STATUS_COMPLETED);

                        } catch (Exception ex) {
                                log.error("Opening stock import failed for session " + finalSession, ex);
                                pending.setSuccess(false);
                                pending.getErrors().add(
                                                "Import failed unexpectedly: " + ex.getMessage());
                                pending.setStatus(STATUS_FAILED);
                        } finally {
                                try {
                                        Context.closeSession();
                                } catch (Exception ignore) {
                                }
                                try {
                                        importPath.toFile().delete();
                                } catch (Exception ignore) {
                                }
                        }
                });

                return pending;
        }

        @RequestMapping(value = "/{sessionId}", method = RequestMethod.GET)
        @ResponseBody
        public OpeningStockImportResult getStatus(
                        @PathVariable("sessionId") String sessionId,
                        HttpServletRequest request) {

                if (!Context.isAuthenticated()) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.stockoperation.authrequired"));
                }

                // Security: sessionId starts with the userId of the uploader.
                // Reject if the authenticated user does not own this session.
                Integer currentUserId = Context.getAuthenticatedUser().getUserId();
                if (!sessionId.startsWith(currentUserId + "_")) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.nofileuploaded"));
                }

                OpeningStockImportResult result = SESSION_RESULTS.get(sessionId);
                if (result == null) {
                        return errorResult(
                                        Context.getMessageSourceService()
                                                        .getMessage("stockmanagement.importoperation.nofileuploaded"));
                }

                return result;
        }

        private static OpeningStockImportResult errorResult(String message) {
                OpeningStockImportResult r = new OpeningStockImportResult();
                r.setSuccess(false);
                r.setStatus(STATUS_FAILED);
                r.setErrors(new ArrayList<>());
                r.getErrors().add(message);
                return r;
        }
}