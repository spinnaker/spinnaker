/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.canaryanalysis.orca.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequestScope;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SetupAndExecuteCanariesStageTest {

  private SetupAndExecuteCanariesStage stage;

  @Mock
  private Clock clock;

  private Instant now = Instant.now();

  @Before
  public void before() {
    initMocks(this);

    when(clock.instant()).thenReturn(now);

    stage = new SetupAndExecuteCanariesStage(clock, new ObjectMapper());
  }

  @Test
  public void test_that_calculateLifetime_uses_supplied_start_and_end_time_if_provided() {
    int lifetimeInMinutes = 5;
    Instant now = Instant.now();
    Instant later = now.plus(lifetimeInMinutes, ChronoUnit.MINUTES);
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder().build();

    Duration actual = stage.calculateLifetime(now, later, request);
    Duration expected = Duration.ofMinutes(lifetimeInMinutes);

    assertEquals("The duration should be 5 minutes", expected, actual);
  }

  @Test
  public void test_that_calculateLifetime_uses_supplied_lifetime_if_start_and_end_time_not_provided() {
    long lifetimeInMinutes = 5L;
    Instant now = Instant.now();
    Instant later = null;
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder().lifetimeDurationMins(5L).build();

    Duration actual = stage.calculateLifetime(now, later, request);
    Duration expected = Duration.ofMinutes(lifetimeInMinutes);

    assertEquals("The duration should be 5 minutes", expected, actual);
  }

  @Test(expected = IllegalArgumentException.class)
  public void test_that_calculateLifetime_throws_an_error_if_lifetime_and_start_and_endtime_not_provided() {
    Instant now = Instant.now();
    stage.calculateLifetime(now, null, CanaryAnalysisExecutionRequest.builder().build());
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_nothing_is_set_in_the_scopes() {
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder().build()))
        .build();
    Duration intervalDuration = Duration.ofMinutes(3);

    for (int i = 1; i < 6; i++) {
      SetupAndExecuteCanariesStage.ScopeTimeConfig conf = stage.calculateStartAndEndForJudgement(request, i, intervalDuration);
      assertEquals(now, conf.getStart());
      assertEquals(now.plus(i * 3, ChronoUnit.MINUTES), conf.getEnd());
    }
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_nothing_is_set_in_the_scopes_with_warmup() {
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder().build()))
        .beginAfterMins(4L)
        .build();
    Duration intervalDuration = Duration.ofMinutes(3);

    for (int i = 1; i < 6; i++) {
      SetupAndExecuteCanariesStage.ScopeTimeConfig conf = stage.calculateStartAndEndForJudgement(request, i, intervalDuration);
      assertEquals(now.plus(4, ChronoUnit.MINUTES), conf.getStart());
      assertEquals(now.plus(i * 3, ChronoUnit.MINUTES).plus(4, ChronoUnit.MINUTES), conf.getEnd());
    }
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_start_and_end_are_supplied_in_the_scopes() {
    Instant definedStart = now.minus(5, ChronoUnit.MINUTES);
    Instant definedEnd = now.plus(20, ChronoUnit.MINUTES);

    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder()
            .startTimeIso(definedStart.toString())
            .endTimeIso(definedEnd.toString())
            .build()))
        .build();
    Duration intervalDuration = Duration.ofMinutes(3);

    for (int i = 1; i < 6; i++) {
      SetupAndExecuteCanariesStage.ScopeTimeConfig conf = stage.calculateStartAndEndForJudgement(request, i, intervalDuration);
      assertEquals(definedStart, conf.getStart());
      assertEquals(definedStart.plus(i * 3, ChronoUnit.MINUTES), conf.getEnd());
    }
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_start_and_end_are_supplied_in_the_scopes_with_warmup() {
    Instant definedStart = now.minus(5, ChronoUnit.MINUTES);
    Instant definedEnd = now.plus(20, ChronoUnit.MINUTES);

    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .beginAfterMins(4L)
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder()
            .startTimeIso(definedStart.toString())
            .endTimeIso(definedEnd.toString())
            .build()))
        .build();
    Duration intervalDuration = Duration.ofMinutes(3);

    for (int i = 1; i < 6; i++) {
      SetupAndExecuteCanariesStage.ScopeTimeConfig conf = stage.calculateStartAndEndForJudgement(request, i, intervalDuration);
      assertEquals(definedStart, conf.getStart());
      assertEquals(definedStart.plus(i * 3, ChronoUnit.MINUTES), conf.getEnd());
    }
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_lookback_is_defined() {
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .lookbackMins(5L)
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder().build()))
        .build();
    Duration intervalDuration = Duration.ofMinutes(5);

    for (int i = 1; i < 6; i++) {
      SetupAndExecuteCanariesStage.ScopeTimeConfig conf = stage.calculateStartAndEndForJudgement(request, i, intervalDuration);
      assertEquals(now.plus((i - 1) * 5, ChronoUnit.MINUTES), conf.getStart());
      assertEquals(now.plus(i * 5, ChronoUnit.MINUTES), conf.getEnd());
    }
  }

  @Test
  public void test_that_calculateStartAndEndForJudgement_has_expected_start_and_end_when_start_iso_only_is_defined() {
    int interval = 1;
    String startIso = "2018-12-17T20:56:39.689Z";
    Duration lifetimeDuration = Duration.ofMinutes(3L);
    CanaryAnalysisExecutionRequest request = CanaryAnalysisExecutionRequest.builder()
        .scopes(ImmutableList.of(CanaryAnalysisExecutionRequestScope.builder().startTimeIso(startIso).build()))
        .build();

    SetupAndExecuteCanariesStage.ScopeTimeConfig actual = stage.calculateStartAndEndForJudgement(request, interval, lifetimeDuration);
    assertEquals(Instant.parse(startIso), actual.getStart());
    assertEquals(Instant.parse(startIso).plus(3L, ChronoUnit.MINUTES), actual.getEnd());
  }
}
