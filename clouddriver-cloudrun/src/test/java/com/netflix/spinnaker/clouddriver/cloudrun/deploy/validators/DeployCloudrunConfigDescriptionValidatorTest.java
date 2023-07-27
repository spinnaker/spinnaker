package com.netflix.spinnaker.clouddriver.cloudrun.deploy.validators;

import static org.mockito.Mockito.*;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunConfigDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeployCloudrunConfigDescriptionValidatorTest {
  DeployCloudrunConfigDescriptionValidator deployCloudrunConfigDescriptionValidator;
  CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository;
  CloudrunNamedAccountCredentials mockCredentials;
  DeployCloudrunConfigDescription description;
  ValidationErrors errors;

  @BeforeEach
  public void init() {
    deployCloudrunConfigDescriptionValidator = new DeployCloudrunConfigDescriptionValidator();
    mockCredentials = mock(CloudrunNamedAccountCredentials.class);
    credentialsRepository = mock(CredentialsRepository.class);
    errors = mock(ValidationErrors.class);
    description = new DeployCloudrunConfigDescription();
    description.setAccountName("cloudrunaccount");
  }

  @Test
  public void ValidateTest() throws NoSuchFieldException {
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
    try {
      deployCloudrunConfigDescriptionValidator = new DeployCloudrunConfigDescriptionValidator();
      Field f =
          deployCloudrunConfigDescriptionValidator
              .getClass()
              .getDeclaredField("credentialsRepository");
      f.setAccessible(true);
      f.set(deployCloudrunConfigDescriptionValidator, credentialsRepository);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Failed to set credentialsRepository of DeployCloudrunConfigDescriptionValidator object",
          e);
    }
    deployCloudrunConfigDescriptionValidator.validate(List.of(description), description, errors);
    verify(errors, never()).rejectValue("${context}.account", "${context}.account.notFound");
  }
}
