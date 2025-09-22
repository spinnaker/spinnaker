package com.netflix.spinnaker.gate.security.oauth;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * WireMock ResponseDefinitionTransformer that builds a 302 redirect to the application callback URL
 * while preserving and URL-encoding the incoming "state" query parameter.
 *
 * <p>The transformer does not require the app port at construction time. Instead it calls a {@link
 * Supplier<Integer>} (configured by the test) to obtain the application's HTTP port at transform
 * time. Tests should set the supplier (see example test code).
 *
 * <p>Usage:
 *
 * <pre>
 *   // in test setup:
 *   RedirectWithStateTransformer.setAppPortSupplier(() -> localServerPort);
 * </pre>
 */
public class RedirectWithStateTransformer extends ResponseDefinitionTransformer {

  /**
   * A supplier that returns the application port. Tests MUST set this before the transformer is
   * invoked, e.g. in {@code @BeforeEach}.
   */
  private static volatile Supplier<Integer> appPortSupplier = () -> -1;

  /**
   * Set the supplier that will provide the application port at transform time. Use a lambda that
   * returns the value of @LocalServerPort.
   *
   * @param supplier supplier returning the app port
   */
  public static void setAppPortSupplier(Supplier<Integer> supplier) {
    appPortSupplier = Objects.requireNonNull(supplier, "appPort supplier must not be null");
  }

  /** Reset supplier to default (optional cleanup). */
  public static void resetAppPortSupplier() {
    appPortSupplier = () -> -1;
  }

  private int getAppPortOrThrow() {
    int port = appPortSupplier.get();
    if (port <= 0) {
      throw new IllegalStateException("Application port not set in RedirectWithStateTransformer");
    }
    return port;
  }

  @Override
  public ResponseDefinition transform(
      Request request,
      ResponseDefinition responseDefinition,
      FileSource files,
      Parameters parameters) {

    // Grab the first state value if present
    String state = "";
    if (request.queryParameter("state") != null
        && request.queryParameter("state").firstValue() != null) {
      state = request.queryParameter("state").firstValue();
    }

    // URL-encode state to preserve characters like '=' and '+'
    String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

    int appPort = getAppPortOrThrow();

    String redirectLocation =
        "http://localhost:"
            + appPort
            + "/login/oauth2/code/github?code=vcbcncnm&state="
            + encodedState;

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
