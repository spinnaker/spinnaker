package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.ClientConfiguration;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import org.junit.jupiter.api.Test;

public class AwsLambdaClientConfigurationTest {

  @Test
  void tcpKeepAliveEnabled_setsClientFlag() {
    LambdaServiceConfig props = new LambdaServiceConfig();
    props.setTcpKeepAlive(true); // Enable

    ClientConfiguration config = new ClientConfiguration();
    config.setUseTcpKeepAlive(props.isTcpKeepAlive());

    assertTrue(config.useTcpKeepAlive(), "TCP KeepAlive should be enabled");
  }

  @Test
  void tcpKeepAliveDisabled_defaultIsFalse() {
    LambdaServiceConfig props = new LambdaServiceConfig();
    props.setTcpKeepAlive(false); // Explicit false

    ClientConfiguration config = new ClientConfiguration();
    config.setUseTcpKeepAlive(props.isTcpKeepAlive());

    assertFalse(config.useTcpKeepAlive(), "TCP KeepAlive should be disabled by default");
  }
}
