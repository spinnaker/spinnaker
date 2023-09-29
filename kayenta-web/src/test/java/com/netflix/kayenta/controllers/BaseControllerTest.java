package com.netflix.kayenta.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.config.WebConfiguration;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.service.MetricSetPairListService;
import com.netflix.kayenta.storage.MapBackedStorageServiceRepository;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootTest(
    classes = BaseControllerTest.TestControllersConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ExtendWith(SpringExtension.class)
public abstract class BaseControllerTest {

  protected static final String CONFIGS_ACCOUNT = "configs-account";
  protected static final String METRICS_STORE = "metrics-store";
  protected static final String OBJECT_STORE = "object-store";

  @MockBean StorageService storageService;
  @MockBean MetricSetPairListService metricSetPairListService;
  @MockBean ExecutionRepository executionRepository;
  @MockBean ExecutionLauncher executionLauncher;
  @Autowired ExecutionMapper executionMapper;

  @MockBean MetricsServiceRepository metricsServiceRepository;

  @MockBean(answer = Answers.RETURNS_MOCKS)
  Registry registry;

  @MockBean CanaryJudge canaryJudge;

  @Autowired private WebApplicationContext webApplicationContext;

  protected MockMvc mockMvc;

  @BeforeEach
  public void setUp() {
    this.mockMvc =
        MockMvcBuilders.webAppContextSetup(this.webApplicationContext).alwaysDo(print()).build();
    when(storageService.servicesAccount(CONFIGS_ACCOUNT)).thenReturn(true);
  }

  @EnableWebMvc
  @Import(WebConfiguration.class)
  @Configuration
  public static class TestControllersConfiguration {

    @Bean
    StorageServiceRepository storageServiceRepository(List<StorageService> storageServices) {
      return new MapBackedStorageServiceRepository(storageServices);
    }

    @Bean
    @Scope("prototype")
    ExecutionMapper executionMapper(
        ExecutionRepository executionRepository,
        ExecutionLauncher executionLauncher,
        Registry registry) {
      return new ExecutionMapper(
          new ObjectMapper(),
          registry,
          "",
          Optional.empty(),
          executionLauncher,
          executionRepository,
          false);
    }

    @Bean
    AccountCredentialsRepository accountCredentialsRepository() {
      MapBackedAccountCredentialsRepository repo = new MapBackedAccountCredentialsRepository();
      repo.save(
          CONFIGS_ACCOUNT,
          getCredentials(CONFIGS_ACCOUNT, AccountCredentials.Type.CONFIGURATION_STORE));
      repo.save(
          METRICS_STORE, getCredentials(METRICS_STORE, AccountCredentials.Type.METRICS_STORE));
      repo.save(OBJECT_STORE, getCredentials(OBJECT_STORE, AccountCredentials.Type.OBJECT_STORE));
      return repo;
    }

    private static AccountCredentials getCredentials(
        String accountName, AccountCredentials.Type type) {
      return getCredentials(accountName, Collections.singletonList(type));
    }

    private static AccountCredentials getCredentials(
        String accountName, List<AccountCredentials.Type> types) {
      AccountCredentials credentials = mock(AccountCredentials.class);
      when(credentials.getSupportedTypes()).thenReturn(types);
      when(credentials.getName()).thenReturn(accountName);
      return credentials;
    }
  }
}
