package org.openmrs.module.stockmanagement.api.jobs;

import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderHeaderAwareBuilder;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.Privileges;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dao.StockManagementDao;
import org.openmrs.module.stockmanagement.api.dto.*;
import org.openmrs.module.stockmanagement.api.model.*;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Performant Opening Stock bulk import job.
 *
 */
public class OpeningStockImportJob {

    private static final String OPENING_STOCK_TYPE_UUID = "99999999-9999-9999-9999-999999999999";
    private static final String OPENING_STOCK_TYPE = "initial";

    private static final int COL_OPERATION_DATE = 0;
    private static final int COL_LOCATION = 1;
    private static final int COL_COMMON_NAME = 2;
    private static final int COL_BATCH_NUMBER = 3;
    private static final int COL_EXPIRATION_DATE = 4;
    private static final int COL_QUANTITY = 5;
    private static final int COL_QTY_UOM = 6;
    private static final int COL_MANUFACTURER_NAME = 7;
    private static final int COL_PURCHASE_PRICE = 8;

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory
            .getLog(OpeningStockImportJob.class);

    private final Path file;
    private final boolean hasHeader;
    private final StockManagementDao dao;
    private final OpeningStockImportResult result = new OpeningStockImportResult();

    public OpeningStockImportJob(Path file, boolean hasHeader, StockManagementDao dao) {
        this.file = file;
        this.hasHeader = hasHeader;
        this.dao = dao;
        result.setErrors(new ArrayList<>());
    }

    public void execute() {
        StockManagementService svc = Context.getService(StockManagementService.class);
        User currentUser = Context.getAuthenticatedUser();

        log.info("OpeningStockImportJob v2 starting for user: "
                + (currentUser != null ? currentUser.getUsername() : "unknown"));

        // STEP 1. Resolve the "initial" operation type
        StockOperationType openingStockType = svc.getStockOperationTypeByUuid(OPENING_STOCK_TYPE_UUID);
        if (openingStockType == null
                || !OPENING_STOCK_TYPE.equals(openingStockType.getOperationType())) {
            result.getErrors().add(
                    "Opening Stock operation type (UUID " + OPENING_STOCK_TYPE_UUID
                            + ") not found. Ensure stockmanagement migrations have run.");
            return;
        }

        HashSet<PrivilegeScope> allScopes = svc.getPrivilegeScopes(
                currentUser, null, openingStockType,
                Privileges.TASK_STOCKMANAGEMENT_STOCKOPERATIONS_MUTATE);
        if (allScopes == null || allScopes.isEmpty()) {
            result.getErrors().add(
                    "Access denied: you do not have '"
                            + Privileges.TASK_STOCKMANAGEMENT_STOCKOPERATIONS_MUTATE
                            + "' scoped to any Opening Stock location. "
                            + "Contact your administrator to assign the correct role.");
            return;
        }

        // Build the set of location UUIDs the user is explicitly scoped to.
        // This is used in step 5 instead of userCanProcess().
        final Set<String> scopedLocationUuids = new HashSet<>();
        for (PrivilegeScope scope : allScopes) {
            if (scope.getLocationUuid() != null) {
                scopedLocationUuids.add(scope.getLocationUuid().toLowerCase());
            }
        }

        log.info("OpeningStockImportJob: user scopedLocationUuids ("
                + scopedLocationUuids.size() + "): " + scopedLocationUuids);

        List<PartyDTO> stockHoldingParties = svc.getAllStockHoldingPartyList();

        // Build name → Location map using only parties returned by the tag-
        // filtered query. Also keep uuid → Location for the privilege step.
        Map<String, Location> locationByUuid = new LinkedHashMap<>();
        Map<String, Location> locationsByName = new LinkedHashMap<>();

        for (PartyDTO p : stockHoldingParties) {
            if (p.getLocationUuid() == null)
                continue;
            Location loc = Context.getLocationService()
                    .getLocationByUuid(p.getLocationUuid());
            if (loc == null || loc.getRetired())
                continue;
            locationByUuid.put(loc.getUuid().toLowerCase(), loc);
            locationsByName.put(loc.getName().toLowerCase(), loc);
        }

        log.info("OpeningStockImportJob: eligible stock locations ("
                + locationsByName.size() + "): " + locationsByName.keySet());

        // STEP 4. Parse file — non-stock locations rejected here
        List<ParsedRow> parsedRows = parseFile(locationsByName);
        if (parsedRows == null)
            return;
        if (parsedRows.isEmpty()) {
            result.getErrors().add("No data rows found in the file.");
            return;
        }

        // STEP 5. Per-location privilege check — FAIL FAST, before item work
        // Uses scopedLocationUuids built in step 2 — zero additional DB calls.
        // Placed before the stock item / UOM queries so permission errors surface
        // immediately without loading unnecessary data.

        Set<String> distinctLocationUuids = parsedRows.stream()
                .filter(r -> r.rowErrors.isEmpty() && r.location != null)
                .map(r -> r.location.getUuid())
                .collect(Collectors.toSet());

        // Check each location against scopedLocationUuids — zero additional DB calls.
        // Two independent conditions must BOTH be true for a location to be accepted:
        //
        // Condition A — Stock tag: location is in locationsByName
        // (enforced already in parseRow; rows failing here never
        // reach this check)
        //
        // Condition B — Role scope: location UUID is in scopedLocationUuids
        // (the flat set of UUIDs the user is explicitly scoped to,
        // including descendants expanded by the DAO query in step 2)

        for (ParsedRow row : parsedRows) {
            if (!row.rowErrors.isEmpty() || row.location == null)
                continue;
            boolean permitted = scopedLocationUuids.contains(
                    row.location.getUuid().toLowerCase());
            if (!permitted) {
                row.rowErrors.add(
                        "Permission denied: your role scope does not include '"
                                + row.locationName + "' for Opening Stock operations. "
                                + "Ask your administrator to add this location to your "
                                + "role scope under Stock Management → User Role Scopes.");
            }
        }

        // All-or-nothing gate: reject entire upload if ANY row has errors
        if (parsedRows.stream().anyMatch(r -> !r.rowErrors.isEmpty())) {
            result.getErrors().add(
                    "Validation failed — no operations were created. "
                            + "Fix all errors below and re-upload.");
            recordRowErrors(parsedRows);
            return;
        }

        // STEP 6. Targeted stock item load
        Set<String> requestedNames = parsedRows.stream()
                .filter(r -> !isBlank(r.commonName))
                .map(r -> normalizeName(r.commonName))
                .collect(Collectors.toSet());

        Map<String, StockItem> itemsByName = buildItemMapForNames(requestedNames, svc);

        for (ParsedRow row : parsedRows) {
            if (!row.rowErrors.isEmpty() || isBlank(row.commonName))
                continue;
            StockItem item = itemsByName.get(normalizeName(row.commonName));
            if (item == null) {
                row.rowErrors.add(
                        "Stock item not found: '" + row.commonName + "'. "
                                + "Name must match common_name in OpenMRS exactly.");
            } else {
                row.stockItem = item;
            }
        }

        // STEP 7. Resolve packaging UOMs
        resolvePackagingUOMs(parsedRows, svc);

        // Second all-or-nothing gate after item/UOM resolution
        if (parsedRows.stream().anyMatch(r -> !r.rowErrors.isEmpty())) {
            result.getErrors().add(
                    "Validation failed — no operations were created. "
                            + "Fix all errors below and re-upload.");
            recordRowErrors(parsedRows);
            return;
        }

        // 8a. UOM entities — one batch query keyed by UUID
        List<String> uomUuids = parsedRows.stream()
                .filter(r -> r.packagingUOMDto != null)
                .map(r -> r.packagingUOMDto.getUuid())
                .distinct()
                .collect(Collectors.toList());

        Map<String, StockItemPackagingUOM> uomByUuid = new HashMap<>();
        if (!uomUuids.isEmpty()) {
            for (StockItemPackagingUOM u : dao.getStockItemPackagingUOMsByUuids(uomUuids)) {
                uomByUuid.put(u.getUuid(), u);
            }
        }
        for (ParsedRow row : parsedRows) {
            if (row.packagingUOMDto == null)
                continue;
            StockItemPackagingUOM entity = uomByUuid.get(row.packagingUOMDto.getUuid());
            if (entity == null) {
                row.rowErrors.add("Could not load UOM entity for '" + row.qtyUomName + "'.");
            } else {
                row.packagingUOM = entity;
            }
        }

        // 8b. Party entities — one lookup per distinct location
        Set<Location> usedLocations = parsedRows.stream()
                .filter(r -> r.location != null)
                .map(r -> r.location)
                .collect(Collectors.toSet());

        Map<String, Party> partyByLocationUuid = new HashMap<>();
        for (Location loc : usedLocations) {
            Party p = svc.getPartyByLocation(loc);
            if (p != null)
                partyByLocationUuid.put(loc.getUuid(), p);
        }

        Map<String, StockBatch> existingBatchCache = new HashMap<>();

        for (ParsedRow row : parsedRows) {
            if (!row.rowErrors.isEmpty() || row.stockItem == null
                    || row.location == null || isBlank(row.batchNumber))
                continue;

            String batchKey = row.stockItem.getId() + "|" + row.batchNumber + "|"
                    + (row.expirationDate != null ? row.expirationDate.getTime() : "");

            // Use cached result if already looked up for another row with same batch
            StockBatch existingBatch;
            if (existingBatchCache.containsKey(batchKey)) {
                existingBatch = existingBatchCache.get(batchKey);
            } else {
                existingBatch = dao.findStockBatch(
                        row.stockItem, row.batchNumber, row.expirationDate);
                existingBatchCache.put(batchKey, existingBatch); // null is a valid cached value
            }

            if (existingBatch == null) {
                // Batch is new — safe to import
                continue;
            }

            // Batch already exists — check if it has already been received at
            // this specific location via an Opening Stock (or any) transaction.
            Party party = partyByLocationUuid.get(row.location.getUuid());
            if (party == null)
                continue; // party missing — will fail later

            // Query: does a StockItemTransaction exist for (batch, party)?
            // A non-zero count means this batch was already credited here.
            StockItemInventorySearchFilter invFilter = new StockItemInventorySearchFilter();
            List<StockItemInventorySearchFilter.ItemGroupFilter> itemGroupFilters = new ArrayList<>();
            StockItemInventorySearchFilter.ItemGroupFilter groupFilter = new StockItemInventorySearchFilter.ItemGroupFilter(
                    Collections.singletonList(party.getId()),
                    existingBatch.getStockItem().getId(),
                    existingBatch.getId());
            itemGroupFilters.add(groupFilter);
            invFilter.setItemGroupFilters(itemGroupFilters);

            List<StockItemInventory> inventory = dao.getStockItemInventory(invFilter, null).getData();

            boolean alreadyImported = !inventory.isEmpty()
                    && inventory.stream().anyMatch(
                            inv -> inv.getQuantity() != null
                                    && inv.getQuantity().compareTo(java.math.BigDecimal.ZERO) != 0);

            if (alreadyImported) {
                row.rowErrors.add(
                        "Batch '" + row.batchNumber + "' for '"
                                + row.commonName + "' already exists at '"
                                + row.locationName + "' with stock on hand. "
                                + "Re-importing an existing batch would create a duplicate "
                                + "Opening Stock entry. Remove this row or use a different "
                                + "batch number.");
            }
        }

        // Final gate after entity pre-load and duplicate-batch check
        if (parsedRows.stream().anyMatch(r -> !r.rowErrors.isEmpty())) {
            result.getErrors().add(
                    "Validation failed — no operations were created.");
            recordRowErrors(parsedRows);
            return;
        }

        // STEP 9. Group rows and write one operation per group
        Map<String, List<ParsedRow>> groups = parsedRows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.operationDate + "|" + r.locationName,
                        LinkedHashMap::new, Collectors.toList()));

        int opsCreated = 0;
        for (Map.Entry<String, List<ParsedRow>> entry : groups.entrySet()) {
            try {
                createOperationDirectly(
                        entry.getValue(), openingStockType,
                        partyByLocationUuid, currentUser);
                opsCreated++;
            } catch (Exception ex) {
                String key = entry.getKey().replace("|", " / ");
                result.getErrors().add(
                        "Error creating operation for [" + key + "]: " + ex.getMessage());
            }
        }

        result.setOperationsCreated(opsCreated);
        if (result.getErrors().isEmpty()) {
            result.setSuccess(true);
        }
    }

    public OpeningStockImportResult getResult() {
        return result;
    }

    private List<ParsedRow> parseFile(Map<String, Location> locationsByName) {
        try {
            byte[] magic = new byte[4];
            try (java.io.InputStream peek = Files.newInputStream(file)) {
                peek.read(magic);
            }
            boolean isXlsx = (magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04);
            return isXlsx ? parseXlsx(locationsByName) : parseCsv(locationsByName);
        } catch (Exception ex) {
            result.getErrors().add("File read error: " + ex.getMessage());
            return null;
        }
    }

    private List<ParsedRow> parseXlsx(Map<String, Location> locationsByName) throws Exception {
        final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        byte[] fileBytes = Files.readAllBytes(file);

        List<String> sharedStrings = new ArrayList<>();
        String dataSheetPath = null;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(fileBytes))) {
            java.util.zip.ZipEntry entry;
            Map<String, byte[]> zipEntries = new LinkedHashMap<>();
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.put(entry.getName(), readZipEntry(zis));
            }

            if (zipEntries.containsKey("xl/sharedStrings.xml")) {
                DocumentBuilder db = nsAwareBuilder();
                org.w3c.dom.Document doc = db.parse(
                        new java.io.ByteArrayInputStream(zipEntries.get("xl/sharedStrings.xml")));
                org.w3c.dom.NodeList siList = doc.getElementsByTagNameNS(NS, "si");
                for (int i = 0; i < siList.getLength(); i++) {
                    org.w3c.dom.NodeList tNodes = ((org.w3c.dom.Element) siList.item(i)).getElementsByTagNameNS(NS,
                            "t");
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < tNodes.getLength(); j++) {
                        String txt = tNodes.item(j).getTextContent();
                        if (txt != null)
                            sb.append(txt);
                    }
                    sharedStrings.add(sb.toString());
                }
            }

            Map<String, String> ridToSheet = new LinkedHashMap<>();
            if (zipEntries.containsKey("xl/_rels/workbook.xml.rels")) {
                DocumentBuilder db = nsAwareBuilder();
                org.w3c.dom.Document doc = db.parse(
                        new java.io.ByteArrayInputStream(zipEntries.get("xl/_rels/workbook.xml.rels")));
                org.w3c.dom.NodeList rels = doc.getElementsByTagName("Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    org.w3c.dom.Element rel = (org.w3c.dom.Element) rels.item(i);
                    ridToSheet.put(rel.getAttribute("Id"), rel.getAttribute("Target"));
                }
            }

            if (zipEntries.containsKey("xl/workbook.xml")) {
                DocumentBuilder db = nsAwareBuilder();
                org.w3c.dom.Document doc = db.parse(
                        new java.io.ByteArrayInputStream(zipEntries.get("xl/workbook.xml")));
                org.w3c.dom.NodeList sheets = doc.getElementsByTagNameNS(NS, "sheet");
                for (int i = 0; i < sheets.getLength(); i++) {
                    org.w3c.dom.Element sh = (org.w3c.dom.Element) sheets.item(i);
                    if ("Opening_Stock_Data".equalsIgnoreCase(sh.getAttribute("name"))) {
                        String rid = sh.getAttributeNS(REL_NS, "id");
                        String target = ridToSheet.get(rid);
                        if (target != null)
                            dataSheetPath = "xl/" + target;
                        break;
                    }
                }
            }

            if (dataSheetPath == null) {
                result.getErrors().add(
                        "Xlsx file does not contain a sheet named 'Opening_Stock_Data'. "
                                + "Please use the provided template.");
                return null;
            }
            if (!zipEntries.containsKey(dataSheetPath)) {
                result.getErrors().add(
                        "Sheet 'Opening_Stock_Data' not found in xlsx at path: " + dataSheetPath);
                return null;
            }

            DocumentBuilder db = nsAwareBuilder();
            org.w3c.dom.Document sheetDoc = db.parse(
                    new java.io.ByteArrayInputStream(zipEntries.get(dataSheetPath)));

            final int FIRST_DATA_ROW = 4; // rows 1-3 are title/headers/example
            List<ParsedRow> rows = new ArrayList<>();
            org.w3c.dom.NodeList rowList = sheetDoc.getElementsByTagNameNS(NS, "row");
            int dataRowNumber = 0;

            for (int ri = 0; ri < rowList.getLength(); ri++) {
                org.w3c.dom.Element rowEl = (org.w3c.dom.Element) rowList.item(ri);
                int rowNum = Integer.parseInt(rowEl.getAttribute("r"));
                if (rowNum < FIRST_DATA_ROW)
                    continue;

                String[] cols = new String[9];
                org.w3c.dom.NodeList cells = rowEl.getElementsByTagNameNS(NS, "c");
                for (int ci = 0; ci < cells.getLength(); ci++) {
                    org.w3c.dom.Element cell = (org.w3c.dom.Element) cells.item(ci);
                    String cellType = cell.getAttribute("t");
                    org.w3c.dom.NodeList vNodes = cell.getElementsByTagNameNS(NS, "v");
                    String rawVal = (vNodes.getLength() > 0)
                            ? vNodes.item(0).getTextContent()
                            : null;
                    int colIdx = cellRefToColIndex(cell.getAttribute("r"));
                    if (colIdx < 0 || colIdx >= cols.length || rawVal == null)
                        continue;

                    if ("s".equals(cellType)) {
                        int ssIdx = Integer.parseInt(rawVal);
                        cols[colIdx] = ssIdx < sharedStrings.size()
                                ? sharedStrings.get(ssIdx)
                                : "";
                    } else if ((colIdx == COL_OPERATION_DATE || colIdx == COL_EXPIRATION_DATE)
                            && rawVal.matches("[0-9]+(\\.[0-9]+)?")) {
                        cols[colIdx] = excelSerialToDateStr(rawVal);
                    } else {
                        cols[colIdx] = rawVal.endsWith(".0")
                                ? rawVal.substring(0, rawVal.length() - 2)
                                : rawVal;
                    }
                }
                if (isBlankRow(cols))
                    continue;
                dataRowNumber++;
                rows.add(parseRow(dataRowNumber, cols, locationsByName));
            }
            return rows;
        }
    }

    private List<ParsedRow> parseCsv(Map<String, Location> locationsByName) {
        List<ParsedRow> rows = new ArrayList<>();
        int lineNumber = 0;
        try (Reader reader = Files.newBufferedReader(file)) {
            RFC4180Parser parser = new RFC4180ParserBuilder()
                    .withSeparator(',').withQuoteChar('"').build();
            CSVReader csv = hasHeader
                    ? new CSVReaderHeaderAwareBuilder(reader).withCSVParser(parser).build()
                    : new CSVReaderBuilder(reader).withCSVParser(parser).build();
            String[] line;
            while ((line = csv.readNext()) != null) {
                lineNumber++;
                if (isBlankRow(line))
                    continue;
                rows.add(parseRow(lineNumber, line, locationsByName));
            }
        } catch (Exception ex) {
            result.getErrors().add("File read error at line " + lineNumber + ": " + ex.getMessage());
            return null;
        }
        return rows;
    }

    private ParsedRow parseRow(int lineNo, String[] cols, Map<String, Location> locationsByName) {
        ParsedRow row = new ParsedRow();
        row.lineNumber = lineNo;

        String rawDate = get(cols, COL_OPERATION_DATE);
        if (isBlank(rawDate)) {
            row.rowErrors.add("OPERATION DATE is required");
        } else {
            Date d = parseDate(rawDate);
            if (d == null)
                row.rowErrors.add("OPERATION DATE must be YYYY-MM-DD, got: " + rawDate);
            else if (d.after(new Date()))
                row.rowErrors.add("OPERATION DATE cannot be in the future");
            else {
                row.operationDate = rawDate.trim();
                row.operationDateObj = d;
            }
        }

        String rawLoc = get(cols, COL_LOCATION);
        if (isBlank(rawLoc)) {
            row.rowErrors.add("LOCATION is required");
        } else {
            row.locationName = rawLoc.trim();
            Location loc = locationsByName.get(row.locationName.toLowerCase());
            if (loc == null) {
                row.rowErrors.add(
                        "'" + row.locationName + "' is not a valid stock-holding location. "
                                + "Only locations tagged as Main Store, Main Pharmacy, "
                                + "Dispensary, or Sub Store are accepted.");
            } else {
                row.location = loc;
            }
        }

        String rawName = get(cols, COL_COMMON_NAME);
        if (isBlank(rawName))
            row.rowErrors.add("COMMON NAME is required");
        else
            row.commonName = rawName.trim();

        String rawBatch = get(cols, COL_BATCH_NUMBER);
        if (isBlank(rawBatch))
            row.rowErrors.add("BATCH NUMBER is required (use UNKNOWN if unavailable)");
        else
            row.batchNumber = rawBatch.trim();

        String rawExpiry = get(cols, COL_EXPIRATION_DATE);
        if (!isBlank(rawExpiry)) {
            Date d = parseDate(rawExpiry.trim());
            if (d == null)
                row.rowErrors.add("EXPIRATION DATE must be YYYY-MM-DD, got: " + rawExpiry);
            else
                row.expirationDate = d;
        }

        String rawQty = get(cols, COL_QUANTITY);
        if (isBlank(rawQty)) {
            row.rowErrors.add("QUANTITY is required");
        } else {
            try {
                BigDecimal qty = new BigDecimal(rawQty.trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0)
                    row.rowErrors.add("QUANTITY must be > 0, got: " + rawQty);
                else
                    row.quantity = qty;
            } catch (NumberFormatException e) {
                row.rowErrors.add("QUANTITY must be a number, got: " + rawQty);
            }
        }

        String rawUom = get(cols, COL_QTY_UOM);
        if (isBlank(rawUom))
            row.rowErrors.add("QTY UOM is required");
        else
            row.qtyUomName = rawUom.trim();

        String rawMfr = get(cols, COL_MANUFACTURER_NAME);
        if (!isBlank(rawMfr))
            row.manufacturerName = rawMfr.trim();

        String rawPrice = get(cols, COL_PURCHASE_PRICE);
        if (!isBlank(rawPrice)) {
            try {
                BigDecimal p = new BigDecimal(rawPrice.trim());
                if (p.compareTo(BigDecimal.ZERO) < 0)
                    row.rowErrors.add("PURCHASE PRICE cannot be negative");
                else
                    row.purchasePrice = p;
            } catch (NumberFormatException e) {
                row.rowErrors.add("PURCHASE PRICE must be a number, got: " + rawPrice);
            }
        }
        return row;
    }

    private Map<String, StockItem> buildItemMapForNames(
            Set<String> normalizedNames, StockManagementService svc) {
        if (normalizedNames.isEmpty())
            return Collections.emptyMap();

        StockItemSearchFilter filter = new StockItemSearchFilter();
        filter.setIncludeVoided(false);
        filter.setStartIndex(0);
        filter.setLimit(Integer.MAX_VALUE);

        List<Integer> matchedIds = svc.findStockItems(filter).getData().stream()
                .filter(dto -> !isBlank(dto.getCommonName())
                        && normalizedNames.contains(normalizeName(dto.getCommonName())))
                .map(StockItemDTO::getId)
                .collect(Collectors.toList());

        if (matchedIds.isEmpty())
            return Collections.emptyMap();

        return dao.getStockItems(matchedIds).stream()
                .filter(item -> !isBlank(item.getCommonName()))
                .collect(Collectors.toMap(
                        item -> normalizeName(item.getCommonName()),
                        item -> item,
                        (a, b) -> a));
    }

    private void resolvePackagingUOMs(List<ParsedRow> rows, StockManagementService svc) {
        List<Integer> stockItemIds = rows.stream()
                .filter(r -> r.rowErrors.isEmpty() && r.stockItem != null)
                .map(r -> r.stockItem.getId()).distinct()
                .collect(Collectors.toList());
        if (stockItemIds.isEmpty())
            return;

        StockItemPackagingUOMSearchFilter f = new StockItemPackagingUOMSearchFilter();
        f.setStockItemIds(stockItemIds);
        Map<Integer, List<StockItemPackagingUOMDTO>> uomsByItem = svc.findStockItemPackagingUOMs(f).getData().stream()
                .collect(Collectors.groupingBy(StockItemPackagingUOMDTO::getStockItemId));

        for (ParsedRow row : rows) {
            if (!row.rowErrors.isEmpty() || row.stockItem == null)
                continue;
            if (Boolean.TRUE.equals(row.stockItem.getHasExpiration()) && row.expirationDate == null) {
                row.rowErrors.add("EXPIRATION DATE is required for '"
                        + row.commonName + "' (item is expirable).");
            }
            if (isBlank(row.qtyUomName))
                continue;
            List<StockItemPackagingUOMDTO> uoms = uomsByItem.getOrDefault(row.stockItem.getId(),
                    Collections.emptyList());
            Optional<StockItemPackagingUOMDTO> matched = uoms.stream()
                    .filter(u -> u.getPackagingUomName() != null
                            && u.getPackagingUomName().equalsIgnoreCase(row.qtyUomName))
                    .findFirst();
            if (!matched.isPresent()) {
                String available = uoms.stream().map(StockItemPackagingUOMDTO::getPackagingUomName)
                        .filter(Objects::nonNull).collect(Collectors.joining(", "));
                row.rowErrors.add("QTY UOM '" + row.qtyUomName + "' not found for '"
                        + row.commonName + "'. "
                        + (available.isEmpty() ? "No UOMs configured."
                                : "Available: [" + available + "]"));
            } else {
                row.packagingUOMDto = matched.get();
            }
        }
    }

    private void createOperationDirectly(
            List<ParsedRow> groupRows,
            StockOperationType openingStockType,
            Map<String, Party> partyByLocationUuid,
            User currentUser) {

        ParsedRow first = groupRows.get(0);
        Party party = partyByLocationUuid.get(first.location.getUuid());
        if (party == null) {
            throw new IllegalStateException(
                    "No party found for location '" + first.locationName + "'.");
        }

        Date now = new Date();

        StockOperation operation = new StockOperation();
        operation.setStockOperationType(openingStockType);
        operation.setStatus(StockOperationStatus.COMPLETED);
        operation.setOperationDate(first.operationDateObj);
        operation.setAtLocation(first.location);
        operation.setSource(party);
        operation.setLocked(true);
        operation.setOperationOrder(1);
        operation.setApprovalRequired(false);
        operation.setCreator(currentUser);
        operation.setDateCreated(now);
        operation.setCompletedBy(currentUser);
        operation.setCompletedDate(now);
        dao.saveStockOperation(operation);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        operation.setOperationNumber(String.format("IST-%s-%s",
                StringUtils.leftPad(Integer.toString(operation.getId()), 4, '0'), timestamp));
        dao.saveStockOperation(operation);

        // Cache batches within this group: stockItemId|batchNo|expiryMillis
        Map<String, StockBatch> batchCache = new HashMap<>();

        for (ParsedRow row : groupRows) {
            String batchKey = row.stockItem.getId() + "|" + row.batchNumber + "|"
                    + (row.expirationDate != null ? row.expirationDate.getTime() : "");

            StockBatch batch = batchCache.get(batchKey);
            if (batch == null) {
                batch = dao.findStockBatch(row.stockItem, row.batchNumber, row.expirationDate);
                if (batch == null) {
                    batch = new StockBatch();
                    batch.setStockItem(row.stockItem);
                    batch.setBatchNo(row.batchNumber);
                    if (Boolean.TRUE.equals(row.stockItem.getHasExpiration()))
                        batch.setExpiration(row.expirationDate);
                    if (!isBlank(row.manufacturerName))
                        batch.setManufacturerName(row.manufacturerName);
                    batch.setCreator(currentUser);
                    batch.setDateCreated(now);
                    dao.saveStockBatch(batch);
                }
                batchCache.put(batchKey, batch);
            }

            StockItemPackagingUOM packagingUOM = row.packagingUOM;

            StockOperationItem opItem = new StockOperationItem();
            opItem.setStockOperation(operation);
            opItem.setStockItem(row.stockItem);
            opItem.setStockBatch(batch);
            opItem.setStockItemPackagingUOM(packagingUOM);
            opItem.setQuantity(row.quantity);
            if (row.purchasePrice != null)
                opItem.setPurchasePrice(row.purchasePrice);
            opItem.setCreator(currentUser);
            opItem.setDateCreated(now);
            dao.saveStockOperationItem(opItem);

            StockItemTransaction txn = new StockItemTransaction();
            txn.setStockItem(row.stockItem);
            txn.setStockBatch(batch);
            txn.setStockItemPackagingUOM(packagingUOM);
            txn.setQuantity(row.quantity.multiply(packagingUOM.getFactor()));
            txn.setParty(party);
            txn.setStockOperation(operation);
            txn.setStockOperationItem(opItem);
            txn.setCreator(currentUser);
            txn.setDateCreated(now);
            dao.saveStockItemTransaction(txn);
        }

        result.setItemsImported(result.getItemsImported() + groupRows.size());
    }

    private static DocumentBuilder nsAwareBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder();
    }

    private static int cellRefToColIndex(String ref) {
        if (ref == null || ref.isEmpty())
            return -1;
        int idx = 0;
        for (char c : ref.toCharArray()) {
            if (!Character.isLetter(c))
                break;
            idx = idx * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return idx - 1;
    }

    private static String excelSerialToDateStr(String rawVal) {
        try {
            double serial = Double.parseDouble(rawVal);
            long days = (long) serial;
            if (days > 59)
                days--;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(1899, java.util.Calendar.DECEMBER, 31, 0, 0, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            cal.add(java.util.Calendar.DATE, (int) days);
            return String.format("%04d-%02d-%02d",
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH));
        } catch (NumberFormatException e) {
            return rawVal;
        }
    }

    private static byte[] readZipEntry(java.util.zip.ZipInputStream zis) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = zis.read(tmp)) != -1)
            buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    private void recordRowErrors(List<ParsedRow> rows) {
        for (ParsedRow row : rows) {
            if (!row.rowErrors.isEmpty())
                result.getErrors().add("Row " + row.lineNumber + ": "
                        + String.join("; ", row.rowErrors));
        }
    }

    private static String normalizeName(String s) {
        if (s == null)
            return "";
        String r = s.replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u2012', '-').replace('\u2010', '-')
                .replace('\u2011', '-').replace('\u00AD', '-')
                .replace('\u00A0', ' ');
        return r.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }

    private static boolean isBlankRow(String[] cols) {
        for (String c : cols)
            if (!isBlank(c))
                return false;
        return true;
    }

    private static String get(String[] cols, int idx) {
        return idx < cols.length ? cols[idx] : null;
    }

    private static Date parseDate(String raw) {
        if (isBlank(raw))
            return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            sdf.setLenient(false);
            return sdf.parse(raw.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    static class ParsedRow {
        int lineNumber;
        List<String> rowErrors = new ArrayList<>();
        String operationDate;
        Date operationDateObj;
        String locationName;
        Location location;
        String commonName;
        StockItem stockItem;
        String batchNumber;
        Date expirationDate;
        BigDecimal quantity;
        String qtyUomName;
        StockItemPackagingUOMDTO packagingUOMDto;
        StockItemPackagingUOM packagingUOM;
        String manufacturerName;
        BigDecimal purchasePrice;
    }
}