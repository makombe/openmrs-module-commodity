package org.openmrs.module.stockmanagement.api.tnt;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class TntHttpClientService {

	private static final Logger log = LoggerFactory.getLogger(TntHttpClientService.class);
	private static final int TIMEOUT = 50000;

	// Global property keys — single source of truth
	private static final String GP_BASE_URL = "tnt.facility.events.submission.base.url";
	private static final String GP_API_TOKEN = "tnt.facility.events.submission.api.token";

	// Header names the TNT API accepts
	private static final String HEADER_X_API_KEY = "X-API-Key";
	private static final String HEADER_AUTHORIZATION = "Authorization";

	private boolean debugMode = false;

	private String getBaseUrl() {
		Context.addProxyPrivilege("Get Global Properties");
		return Context.getAdministrationService().getGlobalProperty(GP_BASE_URL);
	}

	private String apiKey() {
		Context.addProxyPrivilege("Get Global Properties");
		return Context.getAdministrationService().getGlobalProperty(GP_API_TOKEN);
	}

	private String buildUrl(String endpoint) {
		String baseUrl = getBaseUrl();
		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			throw new IllegalStateException(
					"Missing TnT configuration: global property '" + GP_BASE_URL + "' is not set");
		}
		return endpoint != null ? baseUrl + endpoint : baseUrl;
	}

	private CloseableHttpClient createHttpClient() {
		RequestConfig config = RequestConfig.custom()
				.setConnectTimeout(TIMEOUT)
				.setConnectionRequestTimeout(TIMEOUT)
				.setSocketTimeout(TIMEOUT)
				.build();
		return HttpClients.custom()
				.setDefaultRequestConfig(config)
				.build();
	}

	public ResponseEntity<String> executePost(String endpoint, String payload) {
		return executePost(endpoint, payload, apiKey());
	}

	public ResponseEntity<String> executePost(String endpoint, String payload, String resolvedApiKey) {
		debugMode = GlobalProperties.isLoggingEnabled();

		String url = buildUrl(endpoint);

		if (debugMode) {
			log.debug("TntHttpClientService: POST url={} payload={}", url, payload);
		} else {
			log.info("TntHttpClientService: POST url={}", url);
		}

		if (payload == null || payload.trim().isEmpty()) {
			log.error("TntHttpClientService: empty payload — aborting POST to {}", url);
			return ResponseEntity.badRequest()
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"status\":\"ERROR\",\"message\":\"Empty payload\"}");
		}

		HttpPost post = new HttpPost(url);

		// ── Auth headers — send both so the request satisfies either scheme ──
		if (resolvedApiKey != null && !resolvedApiKey.trim().isEmpty()) {
			post.setHeader(HEADER_AUTHORIZATION, "Bearer " + resolvedApiKey);
			post.setHeader(HEADER_X_API_KEY, resolvedApiKey);
		} else {
			log.warn("TntHttpClientService: no API key configured — request may be rejected with 401");
		}

		post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		post.setHeader(HttpHeaders.ACCEPT, "application/json");

		try {
			post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

			try (CloseableHttpClient httpClient = createHttpClient();
					CloseableHttpResponse response = httpClient.execute(post)) {

				ResponseEntity<String> result = handleResponse(response);

				if (debugMode) {
					log.debug("TntHttpClientService: response status={} body={}",
							result.getStatusCodeValue(), result.getBody());
				}

				return result;
			}
		} catch (Exception e) {
			log.error("TntHttpClientService: POST to {} failed", url, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
		}
	}

	private ResponseEntity<String> handleResponse(CloseableHttpResponse response) {
		try {
			int statusCode = response.getStatusLine().getStatusCode();
			HttpEntity entity = response.getEntity();
			String responseBody = entity != null ? EntityUtils.toString(entity) : "{}";

			if (debugMode) {
				log.debug("TntHttpClientService: HTTP {} body={}", statusCode, responseBody);
			}

			return ResponseEntity.status(statusCode)
					.contentType(MediaType.APPLICATION_JSON)
					.body(responseBody);
		} catch (Exception e) {
			log.error("TntHttpClientService: failed to parse response", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"status\":\"ERROR\"}");
		}
	}
}