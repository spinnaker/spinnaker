package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.DeployCloudrunAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeployCloudrunAtomicOperationConverterTest {
  DeployCloudrunAtomicOperationConverter deployCloudrunAtomicOperationConverter;
  CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository;
  CloudrunNamedAccountCredentials mockCredentials;
  Map<String, Object> input =
      new HashMap<>() {
        {
          put("accountName", "cloudrunaccount");
        }
      };

  @BeforeEach
  public void init() {
    deployCloudrunAtomicOperationConverter = new DeployCloudrunAtomicOperationConverter();
    credentialsRepository = mock(CredentialsRepository.class);
    deployCloudrunAtomicOperationConverter.setCredentialsRepository(credentialsRepository);
    deployCloudrunAtomicOperationConverter.setObjectMapper(new ObjectMapper());
    mockCredentials = mock(CloudrunNamedAccountCredentials.class);
  }

  @Test
  public void ConvertOperationTest() {

    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    assertTrue(
        deployCloudrunAtomicOperationConverter.convertOperation(input)
            instanceof DeployCloudrunAtomicOperation);
  }
}
