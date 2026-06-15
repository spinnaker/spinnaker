package com.netflix.spinnaker.gate.security.oauth;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * WireMock ResponseDefinitionTransformer that simulates an OAuth2 authorization server by issuing a
 * 302 redirect back to the application's {@code redirect_uri} with a mock authorization code and
 * the original {@code state} parameter.
 *
 * <p>Both the {@code redirect_uri} and {@code state} are read directly from the incoming request's
 * query parameters (sent by Spring Security), so no hard-coded paths or port configuration is
 * needed.
 */
public class RedirectWithStateTransformer extends ResponseDefinitionTransformer {

  @Override
  public ResponseDefinition transform(
      Request request,
      ResponseDefinition responseDefinition,
      FileSource files,
      Parameters parameters) {

    // Read the redirect_uri that Spring Security sent to the authorize endpoint.
    String redirectUri = "";
    if (request.queryParameter("redirect_uri") != null
        && request.queryParameter("redirect_uri").isPresent()) {
      redirectUri = request.queryParameter("redirect_uri").firstValue();
    }

    // Grab the state value if present.
    String state = "";
    if (request.queryParameter("state") != null && request.queryParameter("state").isPresent()) {
      state = request.queryParameter("state").firstValue();
    }

    String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

    // Build the redirect as a real OAuth2 provider would: back to the redirect_uri with
    // an authorization code and the original state.
    String separator = redirectUri.contains("?") ? "&" : "?";
    String redirectLocation = redirectUri + separator + "code=mock-auth-code&state=" + encodedState;

    return ResponseDefinitionBuilder.responseDefinition()
        .withStatus(302)
        .withHeader("Location", redirectLocation)
        .build();
  }

  @Override
  public String getName() {
    return "redirect-with-state";
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }
}
