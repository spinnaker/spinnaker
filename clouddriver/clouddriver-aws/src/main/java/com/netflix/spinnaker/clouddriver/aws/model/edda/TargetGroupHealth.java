/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model.edda;

import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import java.util.List;

public class TargetGroupHealth {
  private List<TargetHealthDescription> health;
  private String targetGroupArn;

  public List<TargetHealthDescription> getHealth() {
    return health;
  }

  public void setHealth(List<TargetHealthDescription> health) {
    this.health = health;
  }

  public String getTargetGroupArn() {
    return targetGroupArn;
  }

  public void setTargetGroupArn(String targetGroupArn) {
    this.targetGroupArn = targetGroupArn;
  }
}
