package com.netflix.spinnaker.clouddriver.cloudrun.deploy.validators;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeployCloudrunDescriptionValidatorTest {
  DeployCloudrunDescriptionValidator deployCloudrunDescriptionValidator;
  CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository;
  CloudrunNamedAccountCredentials mockCredentials;
  DeployCloudrunDescription description;
  ValidationErrors validationErrors;

  @BeforeEach
  public void init() {
    deployCloudrunDescriptionValidator = new DeployCloudrunDescriptionValidator();
    validationErrors = mock(ValidationErrors.class);
    description = new DeployCloudrunDescription();
    description.setAccountName("cloudrunaccount");
    description.setAccount("cloudrun");
    description.setApplication("my app");
    description.setPromote(false);
    description.setRegion("region");
    description.setApplicationDirectoryRoot("/directoryroot");
    description.setConfigFiles(List.of("/path"));
    description.setCredentials(mockCredentials);
    description.setStopPreviousVersion(false);
    description.setSuppressVersionString(false);
  }

  @Test
  public void validateTest() {
    mockCredentials =
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

    credentialsRepository =
        new MapBackedCredentialsRepository(
            CloudrunNamedAccountCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());
    credentialsRepository.save(mockCredentials);
    deployCloudrunDescriptionValidator.credentialsRepository = credentialsRepository;
    deployCloudrunDescriptionValidator.validate(
        List.of(description), description, validationErrors);
    verify(validationErrors, never())
        .rejectValue("${context}.account", "${context}.account.notFound");
  }

  @Test
  public void validateFailsOnGivingNullCredentials() {
    mockCredentials = null;
    credentialsRepository =
        new MapBackedCredentialsRepository(
            CloudrunNamedAccountCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());

    deployCloudrunDescriptionValidator.credentialsRepository = credentialsRepository;
    deployCloudrunDescriptionValidator.validate(
        List.of(description), description, validationErrors);

    verify(validationErrors, times(1))
        .rejectValue("${context}.account", "${context}.account.notFound");
  }
}
