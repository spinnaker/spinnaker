package com.netflix.kayenta.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.List;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootTest(
    classes = BaseControllerTest.TestControllersConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@RunWith(SpringRunner.class)
public abstract class BaseControllerTest {

  protected static final String CONFIGS_ACCOUNT = "configs-account";

  @Autowired StorageService storageService;
  @MockBean MetricSetPairListService metricSetPairListService;
  @MockBean ExecutionRepository executionRepository;
  @MockBean ExecutionMapper executionMapper;

  @MockBean MetricsServiceRepository metricsServiceRepository;

  @MockBean(answer = Answers.RETURNS_MOCKS)
  Registry registry;

  @MockBean CanaryJudge canaryJudge;

  @Autowired private WebApplicationContext webApplicationContext;

  protected MockMvc mockMvc;

  @Before
  public void setUp() {
    this.mockMvc =
        MockMvcBuilders.webAppContextSetup(this.webApplicationContext).alwaysDo(print()).build();
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
    StorageService storageService() {
      StorageService mock = mock(StorageService.class);
      when(mock.servicesAccount(CONFIGS_ACCOUNT)).thenReturn(true);
      return mock;
    }

    @Bean
    AccountCredentialsRepository accountCredentialsRepository() {
      MapBackedAccountCredentialsRepository repo = new MapBackedAccountCredentialsRepository();
      repo.save(CONFIGS_ACCOUNT, getCredentials(CONFIGS_ACCOUNT));
      return repo;
    }

    private static AccountCredentials getCredentials(String accountName) {
      AccountCredentials credentials = mock(AccountCredentials.class);
      when(credentials.getName()).thenReturn(accountName);
      return credentials;
    }
  }
}
