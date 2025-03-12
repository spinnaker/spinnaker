package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.DestroyCloudrunAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DestroyCloudrunAtomicOperationConverterTest {
  DestroyCloudrunAtomicOperationConverter destroyCloudrunAtomicOperationConverter;
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
    destroyCloudrunAtomicOperationConverter = new DestroyCloudrunAtomicOperationConverter();
    credentialsRepository = mock(CredentialsRepository.class);
    destroyCloudrunAtomicOperationConverter.setCredentialsRepository(credentialsRepository);
    mockCredentials = mock(CloudrunNamedAccountCredentials.class);
  }

  @Test
  public void ConvertOperationTest() {

    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    assertTrue(
        destroyCloudrunAtomicOperationConverter.convertOperation(input)
            instanceof DestroyCloudrunAtomicOperation);
  }
}
