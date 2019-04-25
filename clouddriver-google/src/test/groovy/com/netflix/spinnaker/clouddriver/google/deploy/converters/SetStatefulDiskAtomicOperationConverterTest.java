package com.netflix.spinnaker.clouddriver.google.deploy.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.instancegroups.GoogleServerGroupManagersFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.SetStatefulDiskAtomicOperation;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetStatefulDiskAtomicOperationConverterTest {

  private static final String ACCOUNT_NAME = "spinnaker-account";
  private static final String SERVER_GROUP_NAME = "spinnaker-test-v000";
  private static final String REGION = "us-central1";
  private static final String DEVICE_NAME = "spinnaker-test-v000-001";

  SetStatefulDiskAtomicOperationConverter converter;

  @Before
  public void setUp() {
    GoogleClusterProvider clusterProvider = mock(GoogleClusterProvider.class);
    GoogleServerGroupManagersFactory serverGroupManagersFactory =
        mock(GoogleServerGroupManagersFactory.class);
    converter =
        new SetStatefulDiskAtomicOperationConverter(clusterProvider, serverGroupManagersFactory);

    AccountCredentialsProvider accountCredentialsProvider = mock(AccountCredentialsProvider.class);
    GoogleNamedAccountCredentials accountCredentials =
        new GoogleNamedAccountCredentials.Builder()
            .name(ACCOUNT_NAME)
            .credentials(new FakeGoogleCredentials())
            .build();
    when(accountCredentialsProvider.getCredentials(any())).thenReturn(accountCredentials);
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  public void testConvertDescription() {
    Map<String, String> input = new HashMap<>();
    input.put("accountName", ACCOUNT_NAME);
    input.put("serverGroupName", SERVER_GROUP_NAME);
    input.put("region", REGION);
    input.put("deviceName", DEVICE_NAME);
    SetStatefulDiskDescription description = converter.convertDescription(input);

    assertThat(description.getAccount()).isEqualTo(ACCOUNT_NAME);
    assertThat(description.getServerGroupName()).isEqualTo(SERVER_GROUP_NAME);
    assertThat(description.getRegion()).isEqualTo(REGION);
    assertThat(description.getDeviceName()).isEqualTo(DEVICE_NAME);
  }

  @Test
  public void testConvertOperation() {
    Map<String, String> input = new HashMap<>();
    input.put("accountName", ACCOUNT_NAME);
    input.put("serverGroupName", SERVER_GROUP_NAME);
    input.put("region", REGION);
    input.put("deviceName", DEVICE_NAME);
    SetStatefulDiskAtomicOperation operation = converter.convertOperation(input);

    SetStatefulDiskDescription description = operation.getDescription();
    assertThat(description.getAccount()).isEqualTo(ACCOUNT_NAME);
    assertThat(description.getServerGroupName()).isEqualTo(SERVER_GROUP_NAME);
    assertThat(description.getRegion()).isEqualTo(REGION);
    assertThat(description.getDeviceName()).isEqualTo(DEVICE_NAME);
  }
}
