package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Main.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class MainSpec {
  @MockBean private ClouddriverService mockClouddriverService;

  @MockBean private ServiceClientProvider serviceClientProvider;

  @MockBean private ApplicationService mockApplicationService;

  @MockBean private PermissionService mockPermissionService;

  @MockBean private FiatService mockFiatService;

  @MockBean private ExtendedFiatService mockExtendedFiatService;

  @MockBean private Front50Service mockFront50Service;

  @Test
  public void startupTest() {
    assertThat(serviceClientProvider != null);
  }
}
