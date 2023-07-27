package com.netflix.spinnaker.clouddriver.cloudrun.deploy.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StandardCloudrunAttributeValidatorTest {
  StandardCloudrunAttributeValidator standardCloudrunAttributeValidator;
  CredentialsRepository<CloudrunNamedAccountCredentials> credrepo;
  CloudrunNamedAccountCredentials mockcredentials;
  ValidationErrors errors;

  @BeforeEach
  public void init() {
    errors = mock(ValidationErrors.class);
    standardCloudrunAttributeValidator =
        new StandardCloudrunAttributeValidator("new context", errors);
    mockcredentials =
        new CloudrunNamedAccountCredentials.Builder()
            .setName("cloudrunaccount")
            .setAccountType("cloudrun")
            .setCloudProvider("cloudrun")
            .setApplicationName("my app")
            .setCredentials(mock(CloudrunCredentials.class))
            .setCloudRun(mock(CloudRun.class))
            .setEnvironment("environment")
            .setJsonKey("jsonkey")
            .setLiveLookupsEnabled(false)
            .setLocalRepositoryDirectory("/localdirectory")
            .setJsonPath("/jsonpath")
            .setProject(" my project")
            .build(mock(CloudrunJobExecutor.class));

    credrepo =
        new MapBackedCredentialsRepository(
            CloudrunNamedAccountCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());
    credrepo.save(mockcredentials);
  }

  @Test
  public void validateCredentialsTest() {
    assertTrue(standardCloudrunAttributeValidator.validateCredentials("cloudrunaccount", credrepo));
  }

  @Test
  public void validateCredentialsFailTest() {
    assertFalse(
        standardCloudrunAttributeValidator.validateCredentials(
            "Different cloudrun account", credrepo));
  }
}
