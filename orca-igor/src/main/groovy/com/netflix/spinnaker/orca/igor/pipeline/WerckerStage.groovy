/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.MonitorWerckerJobStartedTask
import org.springframework.stereotype.Component

@Component
class WerckerStage extends JenkinsStage {

  Class waitForJobStartTaskClass() {
    return MonitorWerckerJobStartedTask.class
  }

}
