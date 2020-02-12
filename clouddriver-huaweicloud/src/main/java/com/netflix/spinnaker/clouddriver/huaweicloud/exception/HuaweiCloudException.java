/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.exception;

import com.huawei.openstack4j.model.common.ActionResponse;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;

public class HuaweiCloudException extends IntegrationException {

  public HuaweiCloudException(String message) {
    super(message);
  }

  public HuaweiCloudException(String doWhat, Exception e) {
    super(String.format("Error %s, error is: %s", doWhat, e.getMessage()));
  }

  public HuaweiCloudException(String doWhat, ActionResponse actionResponse) {
    super(
        String.format(
            "Error %s, error is: %s and error code is: %d",
            doWhat, actionResponse.getFault(), actionResponse.getCode()));
  }
}
