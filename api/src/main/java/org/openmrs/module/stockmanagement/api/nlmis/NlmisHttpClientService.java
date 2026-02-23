package org.openmrs.module.stockmanagement.api.nlmis;


import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;

public class NlmisHttpClientService {

	private String getBaseUrl() {
		Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		return Context.getAdministrationService()
				.getGlobalProperty("nlmis.base.url");
	}

	private String getToken() {
		Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		return Context.getAdministrationService()
				.getGlobalProperty("nlmis.oauth.token");
	}

	private String buildUrl(String endpoint) {
		String baseUrl = getBaseUrl();
		if (baseUrl == null || endpoint == null) {
			throw new IllegalStateException("Missing NLMIS configuration");
		}
		return baseUrl + endpoint;
	}

	/**
	 * Generic GET Executor
	 */
	public ResponseEntity<String> executeGet(String endpoint) {

		HttpsURLConnection con = null;

		try {
			String completeUrl = buildUrl(endpoint);
			URL url = new URL(completeUrl);

			con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("Authorization", "Bearer " + getToken());
			con.setRequestProperty("Accept", "application/json");
			con.setConnectTimeout(50000);
			con.setReadTimeout(50000);

			return handleResponse(con);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":\"Error\"}");
	}

	/**
	 * GET with query params
	 */
	public ResponseEntity<String> executeGet(String endpoint, String queryString) {

		HttpsURLConnection con = null;

		try {
			String completeUrl = buildUrl(endpoint);

			if (queryString != null && !queryString.trim().isEmpty()) {
				completeUrl += "?" + queryString;
			}

			URL url = new URL(completeUrl);

			con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("Authorization", "Bearer " + getToken());
			con.setRequestProperty("Accept", "application/json");
			con.setConnectTimeout(50000);
			con.setReadTimeout(50000);

			return handleResponse(con);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":\"Error\"}");
	}

	/**
	 * Generic POST Executor
	 */
	public ResponseEntity<String> executePost(String endpoint, String payload) {

		HttpsURLConnection con = null;

		try {
			String completeUrl = buildUrl(endpoint);
			URL url = new URL(completeUrl);

			con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setRequestProperty("Authorization", "Bearer " + getToken());
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.setRequestProperty("Accept", "application/json");
			con.setConnectTimeout(50000);
			con.setReadTimeout(50000);

			// Send payload
			PrintStream os = new PrintStream(con.getOutputStream());
			os.print(payload);
			os.flush();
			os.close();

			return handleResponse(con);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":\"Error\"}");
	}

	/**
	 * Unified Response Handler
	 */
	private ResponseEntity<String> handleResponse(HttpsURLConnection con) throws IOException {

		int responseCode = con.getResponseCode();

		InputStream stream = (responseCode >= 200 && responseCode < 300)
				? con.getInputStream()
				: con.getErrorStream();

		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		return ResponseEntity.status(responseCode)
				.headers(headers)
				.body(response.toString());
	}
}
