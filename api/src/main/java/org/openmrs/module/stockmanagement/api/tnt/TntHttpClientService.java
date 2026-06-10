package org.openmrs.module.stockmanagement.api.tnt;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.utils.GlobalProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class TntHttpClientService {

	private static final int TIMEOUT = 50000;
	private static Boolean debugMode = false;	
	
	private String getBaseUrl() {
		Context.addProxyPrivilege("Get Global Properties");
		return Context.getAdministrationService()
			.getGlobalProperty("tnt.facility.events.submission.base.url");
	}

	private String apiKey() {
		Context.addProxyPrivilege("Get Global Properties");
		return Context.getAdministrationService()
			.getGlobalProperty("tnt.facility.events.submission.api.token");
	}

	private String buildUrl(String endpoint) {
		String baseUrl = getBaseUrl();
		if (baseUrl == null || endpoint == null) {
			throw new IllegalStateException("Missing TnT configuration");
		}
		return baseUrl + endpoint;
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

	/**
	 * Generic POST Executor
	 */
	public ResponseEntity<String> executePost(String endpoint, String payload) {
		debugMode = GlobalProperties.isLoggingEnabled();
		String url = buildUrl(endpoint);
		if (debugMode) System.out.println("Stock management: Starting TNT event submission url ==> " + url);
		if (debugMode) System.out.println("Stock management: Starting TNT event submission payload " + payload);
		
		HttpPost post = new HttpPost(url);

		post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey());
		post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		post.setHeader(HttpHeaders.ACCEPT, "application/json");

		try {
			post.setEntity(
				new StringEntity(payload, ContentType.APPLICATION_JSON)
			);
			try (CloseableHttpClient httpClient = createHttpClient();
			     CloseableHttpResponse response = httpClient.execute(post)) {
				if (debugMode) System.out.println("Stock management: TNT event submission response" + response.toString());
				if (debugMode) System.out.println("Stock management: TNT event submission statusline" + response.getStatusLine().toString());
			
				return handleResponse(response);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
		}
	}

	/**
	 * Handles HTTP responses
	 */
	private ResponseEntity<String> handleResponse(CloseableHttpResponse response) {

		try {

			int statusCode = response.getStatusLine().getStatusCode();
			if (debugMode) System.out.println("Stock management: TNT event submission statuscode" + statusCode);
			HttpEntity entity = response.getEntity();
			String responseBody = entity != null
				? EntityUtils.toString(entity)
				: "{}";
			return ResponseEntity.status(statusCode)
				.contentType(MediaType.APPLICATION_JSON)
				.body(responseBody);
		}
		catch (Exception e) {
			e.printStackTrace();

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":\"ERROR\"}");
		}
	}
}
