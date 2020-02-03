package com.netflix.kayenta.controllers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class CanaryConfigControllerTest extends BaseControllerTest {

  private static final String CONFIGS_ACCOUNT = "configs-account";
  private static final String CONFIG_ID = "canary_config_12345";
  private static final AccountCredentials CREDENTIALS = getCredentials(CONFIGS_ACCOUNT);
  private StorageService storageService = mock(StorageService.class);

  @Override
  @Before
  public void setUp() {
    super.setUp();
    when(accountCredentialsRepository.getRequiredOne(CONFIGS_ACCOUNT)).thenReturn(CREDENTIALS);
    when(storageServiceRepository.getOne(CONFIGS_ACCOUNT)).thenReturn(Optional.of(storageService));
  }

  @Test
  public void getCanaryConfig_returnsOkResponseForExistingConfiguration() throws Exception {
    CanaryConfig response = CanaryConfig.builder().application("test-app").build();
    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenReturn(response);

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.applications.length()").value(is(1)))
        .andExpect(jsonPath("$.applications[0]").value(is("test-app")));
  }

  @Test
  public void getCanaryConfig_returnsNotFoundResponseForNotExistingConfiguration()
      throws Exception {
    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenThrow(new NotFoundException("dummy message"));

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message").value(is("dummy message")));
  }

  @Test
  public void getCanaryConfig_returnsBadRequestResponseForNotResolvedAccount() throws Exception {
    when(accountCredentialsRepository.getRequiredOne(CONFIGS_ACCOUNT))
        .thenThrow(new IllegalArgumentException("test message"));

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message", equalTo("test message")));
  }

  private static AccountCredentials getCredentials(String accountName) {
    AccountCredentials credentials = mock(AccountCredentials.class);
    when(credentials.getName()).thenReturn(accountName);
    return credentials;
  }
}
