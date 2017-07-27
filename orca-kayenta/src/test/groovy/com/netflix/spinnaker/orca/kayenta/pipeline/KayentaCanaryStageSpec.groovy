/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class KayentaCanaryStageSpec extends Specification {

  @Shared
  WaitStage waitStage = new WaitStage()

  @Unroll
  def "should not include any interval wait stages if start/end times are explicitly specified"() {
    given:
      def pipelineBuilder = new Pipeline().builder()
      def pipeline = pipelineBuilder.build()
      def canaryStageContext = [canaryConfig: [canaryConfigId: "MySampleStackdriverCanaryConfig",
                                               controlScope: "myapp-v010-",
                                               experimentScope: "myapp-v021-",
                                               startTimeIso: "2017-01-01T01:02:34.567Z",
                                               endTimeIso: "2017-01-01T05:02:34.567Z",
                                               beginCanaryAnalysisAfterMins: beginCanaryAnalysisAfterMins]]
      def kayentaCanaryStage = new Stage<>(pipeline, "kayentaCanary", "Run Kayenta Canary", canaryStageContext)
      def builder = new KayentaCanaryStage()
      builder.clock = Clock.systemUTC()
      builder.waitStage = waitStage

    when:
      def aroundStages = builder.aroundStages(kayentaCanaryStage)

    then:
      aroundStages*.type == expectedStageTypes

    where:
      beginCanaryAnalysisAfterMins || expectedStageTypes
      null                         || ["runCanary"]
      ""                           || ["runCanary"]
      "0"                          || ["runCanary"]
      "30"                         || ["wait", "runCanary"]
  }

  @Unroll
  def "should still handle canary intervals properly even if start/end times are explicitly specified"() {
    given:
      def pipelineBuilder = new Pipeline().builder()
      def pipeline = pipelineBuilder.build()
      def canaryStageContext = [canaryConfig: [canaryConfigId: "MySampleStackdriverCanaryConfig",
                                               controlScope: "myapp-v010-",
                                               experimentScope: "myapp-v021-",
                                               startTimeIso: "2017-01-01T01:02:34.567Z",
                                               endTimeIso: "2017-01-01T05:02:34.567Z",
                                               beginCanaryAnalysisAfterMins: beginCanaryAnalysisAfterMins,
                                               canaryAnalysisIntervalMins: canaryAnalysisIntervalMins,
                                               lookbackMins: lookbackMins]]
      def kayentaCanaryStage = new Stage<>(pipeline, "kayentaCanary", "Run Kayenta Canary", canaryStageContext)
      def builder = new KayentaCanaryStage()
      def startTimeInstant = Instant.parse("2017-01-01T01:02:34.567Z")
      builder.clock = Clock.fixed(startTimeInstant, ZoneId.systemDefault())
      builder.waitStage = waitStage

    when:
      def aroundStages = builder.aroundStages(kayentaCanaryStage)
      def summary = collectSummary(aroundStages, startTimeInstant)

    then:
      summary == stageSummary

    where:
      beginCanaryAnalysisAfterMins | canaryAnalysisIntervalMins | lookbackMins || stageSummary
      null                         | null                       | null         || [[minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | ""                         | ""           || [[minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | "0"                        | "0"          || [[minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | "60"                       | null         || [[minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 60, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 120, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 180, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | null                       | ""           || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | ""                         | "0"          || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | "0"                        | null         || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | "60"                       | ""           || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 60, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 120, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 180, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | null                       | "120"        || [[minutesFromInitialStartToCanaryStart: 120, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | ""                         | "60"         || [[minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | "0"                        | "60"         || [[minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      null                         | "60"                       | "60"         || [[minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 60, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 60, minutesFromInitialStartToCanaryEnd: 120, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 120, minutesFromInitialStartToCanaryEnd: 180, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | null                       | "120"        || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 120, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | ""                         | "60"         || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | "0"                        | "60"         || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
      "15"                         | "60"                       | "60"         || [[wait: 900],
                                                                                   [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: 60, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 60, minutesFromInitialStartToCanaryEnd: 120, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 120, minutesFromInitialStartToCanaryEnd: 180, step: "60"],
                                                                                   [minutesFromInitialStartToCanaryStart: 180, minutesFromInitialStartToCanaryEnd: 240, step: "60"]]
  }

  @Unroll
  def "should start now and include warmupWait stage if necessary"() {
    given:
      def pipelineBuilder = new Pipeline().builder()
      def pipeline = pipelineBuilder.build()
      def canaryStageContext = [canaryConfig: [canaryConfigId: "MySampleStackdriverCanaryConfig",
                                               controlScope: "myapp-v010-",
                                               experimentScope: "myapp-v021-",
                                               lifetimeHours: "1",
                                               beginCanaryAnalysisAfterMins: beginCanaryAnalysisAfterMins]]
      def kayentaCanaryStage = new Stage<>(pipeline, "kayentaCanary", "Run Kayenta Canary", canaryStageContext)
      def builder = new KayentaCanaryStage()
      def startTimeInstant = Instant.parse("2017-01-01T01:02:34.567Z")
      builder.clock = Clock.fixed(startTimeInstant, ZoneId.systemDefault())
      builder.waitStage = waitStage

    when:
      def aroundStages = builder.aroundStages(kayentaCanaryStage)

    then:
      aroundStages*.type == expectedStageTypes
      !warmupWaitPeriodMinutes || aroundStages[0].context.waitTime == Duration.ofMinutes(warmupWaitPeriodMinutes).getSeconds()
      aroundStages.find { it.type == "runCanary" }.context.startTimeIso == startTimeInstant.plus(warmupWaitPeriodMinutes, ChronoUnit.MINUTES).toString()

    where:
      beginCanaryAnalysisAfterMins || expectedStageTypes            | warmupWaitPeriodMinutes
      null                         || ["wait", "runCanary"]         | 0
      ""                           || ["wait", "runCanary"]         | 0
      "0"                          || ["wait", "runCanary"]         | 0
      "30"                         || ["wait", "wait", "runCanary"] | 30
  }

  @Unroll
  def "should start now and properly schedule canary pipelines respecting intervals"() {
    given:
      def pipelineBuilder = new Pipeline().builder()
      def pipeline = pipelineBuilder.build()
      def canaryStageContext = [canaryConfig: [canaryConfigId: "MySampleStackdriverCanaryConfig",
                                               controlScope: "myapp-v010-",
                                               experimentScope: "myapp-v021-",
                                               beginCanaryAnalysisAfterMins: beginCanaryAnalysisAfterMins,
                                               canaryAnalysisIntervalMins: canaryAnalysisIntervalMins,
                                               lookbackMins: lookbackMins,
                                               lifetimeHours: "48"]]
      def kayentaCanaryStage = new Stage<>(pipeline, "kayentaCanary", "Run Kayenta Canary", canaryStageContext)
      def builder = new KayentaCanaryStage()
      def startTimeInstant = Instant.parse("2017-01-01T01:02:34.567Z")
      builder.clock = Clock.fixed(startTimeInstant, ZoneId.systemDefault())
      builder.waitStage = waitStage

    when:
      def aroundStages = builder.aroundStages(kayentaCanaryStage)
      def summary = collectSummary(aroundStages, startTimeInstant)

    then:
      summary == stageSummary

    where:
      beginCanaryAnalysisAfterMins | canaryAnalysisIntervalMins           | lookbackMins || stageSummary
      null                         | null                                 | null         || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | ""                                   | ""           || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | "0"                                  | "0"          || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | Duration.ofHours(8).toMinutes() + "" | null         || [[wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(8).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(16).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(24).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(32).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(40).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | null                                 | ""           || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | ""                                   | "0"          || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | "0"                                  | null         || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | Duration.ofHours(8).toMinutes() + "" | ""           || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(8).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(16).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(24).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(32).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(40).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45, minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | null                                 | "60"         || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | ""                                   | "60"         || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | "0"                                  | "60"         || [[wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      null                         | Duration.ofHours(8).toMinutes() + "" | "60"         || [[wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(7).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(8).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(15).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(16).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(23).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(24).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(31).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(32).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(39).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(40).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | null                                 | "60"         || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | ""                                   | "60"         || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | "0"                                  | "60"         || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(48).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
      "45"                         | Duration.ofHours(8).toMinutes() + "" | "60"         || [[wait: Duration.ofMinutes(45).getSeconds()],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(7).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(8).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(15).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(16).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(23).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(24).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(31).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(32).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(39).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(40).toMinutes(), step: "60"],
                                                                                             [wait: Duration.ofHours(8).getSeconds()],
                                                                                             [minutesFromInitialStartToCanaryStart: 45 + Duration.ofHours(47).toMinutes(), minutesFromInitialStartToCanaryEnd: 45 + Duration.ofHours(48).toMinutes(), step: "60"]]
  }

  def "should propagate additional attributes"() {
    given:
      def pipelineBuilder = new Pipeline().builder()
      def pipeline = pipelineBuilder.build()
      def canaryStageContext = [canaryConfig: [metricsAccountName: "atlas-acct-1",
                                               canaryConfigId: "MySampleAtlasCanaryConfig",
                                               controlScope: "some.host.node",
                                               experimentScope: "some.other.host.node",
                                               canaryAnalysisIntervalMins: Duration.ofHours(6).toMinutes(),
                                               lifetimeHours: "12",
                                               step: "PT60S",
                                               extendedScopeParams: [type: "node"]]]
      def kayentaCanaryStage = new Stage<>(pipeline, "kayentaCanary", "Run Kayenta Canary", canaryStageContext)
      def builder = new KayentaCanaryStage()
      def startTimeInstant = Instant.parse("2017-01-01T01:02:34.567Z")
      builder.clock = Clock.fixed(startTimeInstant, ZoneId.systemDefault())
      builder.waitStage = waitStage

    when:
      def aroundStages = builder.aroundStages(kayentaCanaryStage)
      def summary = collectSummary(aroundStages, startTimeInstant)

    then:
      summary == [[wait: Duration.ofHours(6).getSeconds()],
                  [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(6).toMinutes(), step: "PT60S", metricsAccountName: "atlas-acct-1", extendedScopeParams: [type: "node"]],
                  [wait: Duration.ofHours(6).getSeconds()],
                  [minutesFromInitialStartToCanaryStart: 0, minutesFromInitialStartToCanaryEnd: Duration.ofHours(12).toMinutes(), step: "PT60S", metricsAccountName: "atlas-acct-1", extendedScopeParams: [type: "node"]]]
  }

  def collectSummary(List<Stage> aroundStages, startTimeInstant) {
    return aroundStages.collect {
      if (it.type == waitStage.type) {
        return [wait: it.context.waitTime]
      } else if (it.type == RunCanaryPipelineStage.STAGE_TYPE) {
        Instant runCanaryPipelineStartInstant = Instant.parse(it.context.startTimeIso)
        Instant runCanaryPipelineEndInstant = Instant.parse(it.context.endTimeIso)
        Map ret = [
          minutesFromInitialStartToCanaryStart: startTimeInstant.until(runCanaryPipelineStartInstant, ChronoUnit.MINUTES),
          minutesFromInitialStartToCanaryEnd  : startTimeInstant.until(runCanaryPipelineEndInstant, ChronoUnit.MINUTES)
        ]

        if (it.context.metricsAccountName) {
          ret.metricsAccountName = it.context.metricsAccountName
        }

        if (it.context.step) {
          ret.step = it.context.step
        }

        if (it.context.extendedScopeParams) {
          ret.extendedScopeParams = it.context.extendedScopeParams
        }

        return ret
      } else {
        throw new IllegalArgumentException("Encountered unexpected stage of type '$it.type'.")
      }
    }
  }
}
