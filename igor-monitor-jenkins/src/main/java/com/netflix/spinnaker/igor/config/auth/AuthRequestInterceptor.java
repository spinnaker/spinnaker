package com.netflix.spinnaker.igor.config.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.netflix.spinnaker.igor.config.JenkinsProperties;
import com.squareup.okhttp.Credentials;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import retrofit.RequestInterceptor;

@Slf4j
public class AuthRequestInterceptor implements RequestInterceptor {

  private static final Joiner SUPPLIERS_JOINER = Joiner.on(", ");

  public AuthRequestInterceptor(JenkinsProperties.JenkinsHost host) {
    // Order may be significant here.
    if (!Strings.isNullOrEmpty(host.getUsername()) && !Strings.isNullOrEmpty(host.getPassword())) {
      suppliers.add(new BasicAuthHeaderSupplier(host.getUsername(), host.getPassword()));
    }

    if (!Strings.isNullOrEmpty(host.getJsonPath()) && !host.getOauthScopes().isEmpty()) {
      suppliers.add(new GoogleBearerTokenHeaderSupplier(host.getJsonPath(), host.getOauthScopes()));
    } else if (!Strings.isNullOrEmpty(host.getToken())) {
      BearerTokenHeaderSupplier supplier = new BearerTokenHeaderSupplier();
      supplier.token = host.getToken();
      suppliers.add(supplier);
    }
  }

  @Override
  public void intercept(RequestFacade request) {
    if (suppliers != null && !suppliers.isEmpty()) {
      request.addHeader("Authorization", SUPPLIERS_JOINER.join(suppliers));
    }
  }

  public List<AuthorizationHeaderSupplier> getSuppliers() {
    return suppliers;
  }

  public void setSuppliers(List<AuthorizationHeaderSupplier> suppliers) {
    this.suppliers = suppliers;
  }

  private List<AuthorizationHeaderSupplier> suppliers =
      new ArrayList<AuthorizationHeaderSupplier>();

  /** TODO(rz): Good candidate for plugins. */
  public interface AuthorizationHeaderSupplier {
    /**
     * Returns the value to be added as the value in the "Authorization" HTTP header.
     *
     * @return
     */
    String toString();
  }

  public static class BasicAuthHeaderSupplier implements AuthorizationHeaderSupplier {
    public BasicAuthHeaderSupplier(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String toString() {
      return Credentials.basic(username, password);
    }

    private final String username;
    private final String password;
  }

  @Slf4j
  public static class GoogleBearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {
    @SneakyThrows
    public GoogleBearerTokenHeaderSupplier(String jsonPath, List<String> scopes) {
      credentials =
          GoogleCredentials.fromStream(new FileInputStream(new File(jsonPath)))
              .createScoped(scopes);
    }

    @SneakyThrows
    public String toString() {
      log.debug("Including Google Bearer token in Authorization header");
      credentials.refreshIfExpired();
      return credentials.getAccessToken().getTokenValue();
    }

    private GoogleCredentials credentials;
  }

  @Slf4j
  public static class BearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {
    public String toString() {
      log.debug("Including raw bearer token in Authorization header");
      return "Bearer " + token;
    }

    private Object token;
  }
}
