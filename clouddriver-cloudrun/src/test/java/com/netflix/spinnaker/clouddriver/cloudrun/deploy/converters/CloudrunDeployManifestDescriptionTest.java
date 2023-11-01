package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudrun.converter.manifest.CloudrunDeployManifestConverter;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.op.manifest.CloudrunDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CloudrunDeployManifestDescriptionTest {

  CloudrunDeployManifestConverter converter;
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
    converter = new CloudrunDeployManifestConverter();
    credentialsRepository = mock(CredentialsRepository.class);
    converter.setCredentialsRepository(credentialsRepository);
    converter.setObjectMapper(new ObjectMapper());
    mockCredentials = mock(CloudrunNamedAccountCredentials.class);
  }

  @Test
  public void convertOperationTest() {

    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    assertTrue(converter.convertOperation(input) instanceof CloudrunDeployManifestOperation);
  }

  @Test
  public void convertDescriptionTest() {

    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    assertTrue(converter.convertDescription(input) instanceof CloudrunDeployManifestDescription);
  }

  @Test
  public void checkApplicationNameTest() {

    Map<String, String> appMap = new HashMap<>();
    appMap.put("app", "foo");
    input.put("moniker", appMap);
    when(credentialsRepository.getOne(any())).thenReturn(mockCredentials);
    CloudrunDeployManifestDescription desc = converter.convertDescription(input);
    assertThat(desc.getApplication()).isEqualTo("foo");
  }
}
