/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "server-group")
public class ServerGroupProperties {

  private Resize resize = new Resize();

  public static class Resize {
    private boolean matchInstancesSize;

    public void setMatchInstancesSize(boolean matchInstancesSize) {
      this.matchInstancesSize = matchInstancesSize;
    }

    public boolean isMatchInstancesSize() {
      return matchInstancesSize;
    }
  }

  public void setResize(Resize resize) {
    this.resize = resize;
  }

  public Resize getResize() {
    return resize;
  }
}
