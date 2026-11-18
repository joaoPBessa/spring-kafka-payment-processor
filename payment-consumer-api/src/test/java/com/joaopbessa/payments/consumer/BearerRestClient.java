package com.joaopbessa.payments.consumer;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.joaopbessa.payments.common.security.TestJwtIssuer;

/**
 * Calls the application over HTTP as a caller that holds a token with the given roles. A test that needs a call
 * without credentials uses the {@link TestRestTemplate} directly.
 */
public class BearerRestClient {

	private final TestRestTemplate restTemplate;
	private final TestJwtIssuer issuer;

	public BearerRestClient(TestRestTemplate restTemplate, TestJwtIssuer issuer) {
		this.restTemplate = restTemplate;
		this.issuer = issuer;
	}

	public <T> ResponseEntity<T> get(String path, Class<T> responseType, String... roles) {
		return restTemplate.exchange(path, HttpMethod.GET, withRoles(null, roles), responseType);
	}

	public <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType, String... roles) {
		return restTemplate.exchange(path, HttpMethod.POST, withRoles(body, roles), responseType);
	}

	private HttpEntity<Object> withRoles(Object body, String... roles) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, issuer.bearer(roles));
		return new HttpEntity<>(body, headers);
	}

}
