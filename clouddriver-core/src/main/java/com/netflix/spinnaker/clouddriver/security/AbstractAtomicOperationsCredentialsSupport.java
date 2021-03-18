package com.netflix.spinnaker.clouddriver.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAtomicOperationsCredentialsSupport
    implements AtomicOperationConverter {

  @Autowired private AccountCredentialsProvider accountCredentialsProvider;

  private ObjectMapper objectMapper;

  @Autowired
  public void setObjectMapper(ObjectMapper objectMapper) {
    // TODO(rz): This is a bad pattern, we should be using the object mapper customizer bean, rather
    //  than modifying a singleton, global object mapper after injecting it somewhere.
    this.objectMapper =
        objectMapper
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T extends AccountCredentials> T getCredentialsObject(final String name) {
    if (name == null) {
      throw new InvalidRequestException("credentials are required");
    }

    T credential;
    try {
      AccountCredentials repoCredential = accountCredentialsProvider.getCredentials(name);
      if (repoCredential == null) {
        throw new NullPointerException();
      }

      credential = (T) repoCredential;
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format(
              "credentials not found (name: %s, names: %s)",
              name,
              getAccountCredentialsProvider().getAll().stream()
                  .map(AccountCredentials::getName)
                  .collect(Collectors.joining(","))),
          e);
    }

    return credential;
  }

  public AccountCredentialsProvider getAccountCredentialsProvider() {
    return accountCredentialsProvider;
  }

  public void setAccountCredentialsProvider(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
