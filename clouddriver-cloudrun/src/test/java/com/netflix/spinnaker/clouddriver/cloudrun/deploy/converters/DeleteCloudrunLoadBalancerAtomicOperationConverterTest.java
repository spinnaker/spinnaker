package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.DeleteCloudrunLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DeleteCloudrunLoadBalancerAtomicOperationConverterTest {

  DeleteCloudrunLoadBalancerAtomicOperationConverter
      deleteCloudrunLoadBalancerAtomicOperationConverter;
  CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository;
  CloudrunNamedAccountCredentials mockCredentials;
  Map<String, Object> input =
      new HashMap<>() {
        {
          put("accountName", "cloudrunaccount");
        }
      };

  @Before
  public void init() {
    deleteCloudrunLoadBalancerAtomicOperationConverter =
        new DeleteCloudrunLoadBalancerAtomicOperationConverter();
    credentialsRepository = mock(CredentialsRepository.class);
    deleteCloudrunLoadBalancerAtomicOperationConverter.setCredentialsRepository(
        credentialsRepository);
    mockCredentials = mock(CloudrunNamedAccountCredentials.class);
  }

  @Test
  public void ConvertOperationTest() {
    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    assertTrue(
        deleteCloudrunLoadBalancerAtomicOperationConverter.convertOperation(input)
            instanceof DeleteCloudrunLoadBalancerAtomicOperation);
  }
}
