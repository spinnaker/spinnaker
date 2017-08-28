/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForAllInstancesNotUpTaskSpec extends Specification {
  @Subject task = new WaitForAllInstancesNotUpTask()

  @Unroll
  void "should succeed as #hasSucceeded based on instance providers #healthProviderNames for instances #instances"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "")

    expect:
    hasSucceeded == task.hasSucceeded(stage, [minSize: 0], instances, healthProviderNames)

    where:
    hasSucceeded || healthProviderNames   | instances
    true         || null                  | []
    true         || null                  | [ [ health: [ ] ] ]
    true         || ['a']                 | []
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || ['b']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    true         || ['b']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state: 'OutOfService' ] ] ] ]

    // Multiple health providers.
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['b']                 | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    true         || ['b']                 | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Unknown' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['b']                 | [ [ health: [ [ type: 'a', state : 'Unknown' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Unknown' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Unknown' ], [ type: 'b', state : 'OutOfService' ] ] ] ]

    // Multiple instances.
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Up' ] ] ] ]
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Up' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Up' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'b', state : 'Down' ] ] ] ]

    // Multiple instances with multiple health providers.
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || null                  | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['a']                 | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Up' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Down' ] ] ] ]
    false        || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Up' ], [ type: 'b', state : 'Unknown' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Down' ] ] ] ]
    true         || ['a', 'b']            | [ [ health: [ [ type: 'a', state : 'Down' ] ] ], [ health: [ [ type: 'a', state : 'Down' ], [ type: 'b', state : 'Unknown' ] ] ] ]

    // Ignoring health.
    true         || []                    | [ [ health: [ [ type: 'a', state : 'Up' ] ] ], [ health: [ [ type: 'a', state : 'Up'], [ type: 'b', state : 'Unknown' ] ] ] ]
  }
}
