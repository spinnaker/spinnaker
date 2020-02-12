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

package com.netflix.spinnaker.clouddriver.huaweicloud;

import java.util.Collection;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class HuaweiCloudUtils {
  public static boolean isEmptyStr(Object str) {
    return StringUtils.isEmpty(str);
  }

  public static boolean isEmptyCollection(Collection c) {
    return c == null || c.isEmpty();
  }

  public static boolean isEmptyMap(Map m) {
    return m == null || m.isEmpty();
  }

  public static Logger getLogger(Class clazz) {
    return LoggerFactory.getLogger(clazz);
  }
}
