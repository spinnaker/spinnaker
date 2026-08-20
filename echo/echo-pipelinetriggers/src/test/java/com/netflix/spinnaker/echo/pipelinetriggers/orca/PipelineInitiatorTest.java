/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider;
import com.netflix.spinnaker.kork.web.context.RequestContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import retrofit2.mock.Calls;

class PipelineInitiatorTest {

  private NoopRegistry registry;
  private DynamicConfigService noopDynamicConfigService;
  private OrcaService orca;
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private FiatStatus fiatStatus;
  private ObjectMapper objectMapper;
  private QuietPeriodIndicator quietPeriodIndicator;
  private AuthenticatedRequestContextProvider contextProvider;
  private DiscoveryStatusListener activator;

  private Optional<String> capturedSpinnakerUser;
  private Optional<String> capturedSpinnakerAccounts;

  private Map<String, UserPermission.View> userPermissions;

  @BeforeEach
  void setUp() {
    registry = new NoopRegistry();
    noopDynamicConfigService = new DynamicConfigService.NoopDynamicConfig();
    orca = mock(OrcaService.class);
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    fiatStatus = mock(FiatStatus.class);
    objectMapper = mock(ObjectMapper.class);
    quietPeriodIndicator = mock(QuietPeriodIndicator.class);
    contextProvider = new AuthenticatedRequestContextProvider();
    activator = mock(DiscoveryStatusListener.class);

    capturedSpinnakerUser = Optional.empty();
    capturedSpinnakerAccounts = Optional.empty();

    userPermissions = new HashMap<>();
    userPermissions.put(
        "anonymous",
        userPermissionView(
            account("account1", List.of("READ")),
            account("account2", List.of("READ", "WRITE")),
            account("account3", List.of("READ", "WRITE"))));
    userPermissions.put(
        "not-anonymous",
        userPermissionView(
            account("account1", List.of("READ", "WRITE")),
            account("account2", List.of("READ", "WRITE")),
            account("account3", List.of("READ", "WRITE"))));
  }

  private static UserPermission.View userPermissionView(Account.View... accounts) {
    UserPermission.View view = new UserPermission.View();
    view.setAccounts(new LinkedHashSet<>(Arrays.asList(accounts)));
    return view;
  }

  private static Account.View account(String name, Collection<String> authorizations) {
    Account.View accountView = new Account.View();
    accountView.setName(name);
    accountView.setAuthorizations(
        authorizations.stream().map(Authorization::valueOf).collect(Collectors.toSet()));
    return accountView;
  }

  private void captureAuthorizationContext() {
    capturedSpinnakerUser = contextProvider.get().getUser();
    capturedSpinnakerAccounts = contextProvider.get().getAccounts();
  }

  private static Set<String> splitToSet(String s) {
    return s == null ? null : Set.of(s.split(","));
  }

  private static Stream<Arguments> callsOrcaTimesParams() {
    return Stream.of(
        // user, upInDiscovery, enabled, suppress, legacyFallbackEnabled, expectedTriggerCalls,
        // expectedSpinnakerUser, expectedSpinnakerAccounts
        Arguments.of("anonymous", false, true, false, false, 0, null, null), // down in discovery
        Arguments.of("anonymous", true, false, false, false, 0, null, null), // orca not enabled
        Arguments.of(
            null, true, true, true, false, 0, null, null), // cron triggers enabled but suppressed
        Arguments.of(
            "anonymous",
            true,
            true,
            false,
            false,
            1,
            "anonymous",
            null), // fallback disabled (no accounts)
        Arguments.of(
            "anonymous",
            true,
            true,
            false,
            true,
            1,
            "anonymous",
            "account2,account3"), // fallback enabled (all WRITE accounts)
        Arguments.of(
            "not-anonymous",
            true,
            true,
            false,
            true,
            1,
            "not-anonymous",
            "account1,account2,account3"), // fallback enabled (all WRITE accounts)
        Arguments.of(
            null,
            true,
            true,
            false,
            true,
            1,
            "anonymous",
            "account2,account3") // null trigger user should default to 'anonymous'
        );
  }

  @ParameterizedTest(name = "calls orca {5} times when enabled={2} and suppress={3}")
  @MethodSource("callsOrcaTimesParams")
  void callsOrcaExpectedTimesWhenEnabledAndSuppress(
      String user,
      boolean upInDiscovery,
      boolean enabled,
      boolean suppress,
      boolean legacyFallbackEnabled,
      int expectedTriggerCalls,
      String expectedSpinnakerUser,
      String expectedSpinnakerAccounts) {
    DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
    PipelineInitiator pipelineInitiator =
        new PipelineInitiator(
            registry,
            orca,
            Optional.of(fiatPermissionEvaluator),
            fiatStatus,
            MoreExecutors.newDirectExecutorService(),
            objectMapper,
            quietPeriodIndicator,
            dynamicConfigService,
            activator,
            5,
            5000);

    Pipeline pipeline =
        Pipeline.builder()
            .application("application")
            .name("name")
            .id("id")
            .type("pipeline")
            .trigger(Trigger.builder().type("cron").runAsUser(user).build())
            .build();

    String effectiveUser = (user == null) ? "anonymous" : user;

    Mockito.lenient().when(activator.isEnabled()).thenReturn(upInDiscovery);
    Mockito.lenient()
        .when(dynamicConfigService.isEnabled(eq("scheduler.triggers"), eq(true)))
        .thenReturn(!suppress);
    Mockito.lenient()
        .when(dynamicConfigService.isEnabled(eq("orca"), eq(true)))
        .thenReturn(enabled);
    Mockito.lenient().when(fiatStatus.isEnabled()).thenReturn(enabled);
    Mockito.lenient().when(fiatStatus.isLegacyFallbackEnabled()).thenReturn(legacyFallbackEnabled);
    Mockito.lenient()
        .when(fiatPermissionEvaluator.getPermission(effectiveUser))
        .thenReturn(userPermissions.get(effectiveUser));
    Mockito.lenient()
        .when(orca.trigger(pipeline))
        .thenAnswer(
            invocation -> {
              captureAuthorizationContext();
              return Calls.response(new OrcaService.TriggerResponse());
            });

    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER);

    verify(activator, times(1)).isEnabled();
    verify(orca, times(expectedTriggerCalls)).trigger(pipeline);
    if (legacyFallbackEnabled) {
      verify(fiatPermissionEvaluator, times(1)).getPermission(effectiveUser);
    } else {
      verify(fiatPermissionEvaluator, never()).getPermission(any());
    }

    assertThat(capturedSpinnakerUser.orElse(null)).isEqualTo(expectedSpinnakerUser);
    assertThat(splitToSet(capturedSpinnakerAccounts.orElse(null)))
        .isEqualTo(splitToSet(expectedSpinnakerAccounts));
  }

  @Test
  void propagatesAuthHeadersToOrcaCallsWithoutRunAs() throws InterruptedException {
    RequestContext context = contextProvider.get();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    PipelineInitiator pipelineInitiator =
        new PipelineInitiator(
            registry,
            orca,
            Optional.of(fiatPermissionEvaluator),
            fiatStatus,
            executor,
            objectMapper,
            quietPeriodIndicator,
            noopDynamicConfigService,
            activator,
            5,
            5000);

    Trigger trigger = Trigger.builder().type("cron").build().atPropagateAuth(true);

    Pipeline pipeline =
        Pipeline.builder()
            .application("application")
            .name("name")
            .id("id")
            .type("pipeline")
            .trigger(trigger)
            .build();

    String user = "super-duper-user";
    String account = "super-duper-account";

    when(activator.isEnabled()).thenReturn(true);
    when(fiatStatus.isEnabled()).thenReturn(true);
    when(fiatStatus.isLegacyFallbackEnabled()).thenReturn(false);
    when(orca.trigger(pipeline))
        .thenAnswer(
            invocation -> {
              captureAuthorizationContext();
              return Calls.response(new OrcaService.TriggerResponse());
            });

    context.setUser(user);
    context.setAccounts(account);
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER);
    context.clear();

    // Wait for the trigger to actually be invoked (happens on separate thread)
    executor.shutdown();
    executor.awaitTermination(2, TimeUnit.SECONDS);

    verify(orca, times(1)).trigger(pipeline);
    assertThat(capturedSpinnakerUser).contains(user);
    assertThat(capturedSpinnakerAccounts).contains(account);
  }

  private static Stream<Arguments> planPipelineIfTemplatedParams() {
    return Stream.of(
        Arguments.of("pipeline", 0, "anonymous", null),
        Arguments.of("templatedPipeline", 1, "anonymous", null),
        Arguments.of(null, 0, "anonymous", null));
  }

  @ParameterizedTest(name = "calls orca {1} to plan pipeline if templated ({0})")
  @MethodSource("planPipelineIfTemplatedParams")
  void callsOrcaToPlanPipelineIfTemplated(
      String type,
      int expectedPlanCalls,
      String expectedSpinnakerUser,
      String expectedSpinnakerAccounts) {
    PipelineInitiator pipelineInitiator =
        new PipelineInitiator(
            registry,
            orca,
            Optional.empty(),
            fiatStatus,
            MoreExecutors.newDirectExecutorService(),
            objectMapper,
            quietPeriodIndicator,
            noopDynamicConfigService,
            activator,
            5,
            5000);

    Pipeline pipeline =
        Pipeline.builder().application("application").name("name").id("id").type(type).build();

    Map<String, Object> pipelineMap = objectMapperConvertToMap(pipeline);

    when(fiatStatus.isEnabled()).thenReturn(true);
    when(activator.isEnabled()).thenReturn(true);
    when(orca.plan(any(), eq(true))).thenReturn(Calls.response(pipelineMap));
    Mockito.lenient()
        .when(objectMapper.convertValue(pipelineMap, Pipeline.class))
        .thenReturn(pipeline);
    when(orca.trigger(any()))
        .thenAnswer(
            invocation -> {
              captureAuthorizationContext();
              return Calls.response(new OrcaService.TriggerResponse());
            });

    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER);

    verify(orca, times(expectedPlanCalls)).plan(any(), eq(true));
    verify(orca, times(1)).trigger(any());

    assertThat(capturedSpinnakerUser.orElse(null)).isEqualTo(expectedSpinnakerUser);
    assertThat(capturedSpinnakerAccounts.orElse(null)).isEqualTo(expectedSpinnakerAccounts);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMapperConvertToMap(Pipeline pipeline) {
    return EchoObjectMapper.getInstance().convertValue(pipeline, Map.class);
  }
}
