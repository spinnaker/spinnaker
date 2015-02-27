/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.notifications

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import net.greghaines.jesque.worker.WorkerPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP

@Component
@Slf4j
@CompileStatic
class JesqueActivator implements ApplicationListener<EurekaStatusChangedEvent> {

  private final WorkerPool jesqueWorkerPool

  @Autowired
  JesqueActivator(WorkerPool jesqueWorkerPool) {
    this.jesqueWorkerPool = jesqueWorkerPool
  }

  @Override
  void onApplicationEvent(EurekaStatusChangedEvent event) {
    event.statusChangeEvent.with {
      if (it.status == UP) {
        log.info("Instance is alive... starting Jesque worker pool")
        jesqueWorkerPool.togglePause(false)
      } else if (it.previousStatus == UP) {
        log.warn("Instance is going $it.status... stopping Jesque worker pool")
        jesqueWorkerPool.togglePause(true)
      }
    }
  }
}
