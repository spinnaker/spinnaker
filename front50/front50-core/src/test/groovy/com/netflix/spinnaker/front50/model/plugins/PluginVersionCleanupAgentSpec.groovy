/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.front50.model.plugins

import com.netflix.spinnaker.front50.config.PluginVersionCleanupProperties
import com.netflix.spinnaker.moniker.Namer
import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer
import org.springframework.scheduling.TaskScheduler
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PluginVersionCleanupAgentSpec extends Specification {

  PluginVersionPinningRepository repository = Mock()
  PluginVersionCleanupProperties properties = new PluginVersionCleanupProperties()
  Namer namer = new FriggaReflectiveNamer()
  TaskScheduler scheduler = Mock()

  def "schedules on creation"() {
    given:
    PluginVersionCleanupAgent subject = new PluginVersionCleanupAgent(repository, properties, namer, scheduler)

    when:
    subject.schedule()

    then:
    1 * scheduler.scheduleWithFixedDelay(subject, properties.interval)
  }

  def "cleans up old versions"() {
    given:
    PluginVersionCleanupAgent subject = new PluginVersionCleanupAgent(repository, properties, namer, scheduler)

    when:
    subject.run()

    then:
    1 * repository.all() >> [
      serverGroupPluginVersions("orca-main", "us-west-2", 11),
      serverGroupPluginVersions("clouddriver-main", "us-west-2", 3),
      serverGroupPluginVersions("orca-main", "us-east-1", 5)
    ].flatten()
    1 * repository.bulkDelete(["orca-main-v000-us-west-2"])
    0 * _
  }

  private List<ServerGroupPluginVersions> serverGroupPluginVersions(String cluster, String location, int count) {
    Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

    (0..count - 1).collect {
      String sequence = "$it".padLeft(3, "0")
      String serverGroupName = "$cluster-v$sequence"
      def versions = new ServerGroupPluginVersions("$serverGroupName-$location", serverGroupName, location, [:])
      versions.createTs = clock.instant().plusMillis(it).toEpochMilli()
      versions
    }
  }
}
