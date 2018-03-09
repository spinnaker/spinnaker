/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.kayenta.canary.util;

import org.apache.commons.lang.StringUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

public class FetchControllerUtils {

  public static String determineDefaultProperty(String passedPropertyValue, String propertyName, Object defaultProperties) {
    if (!StringUtils.isEmpty(passedPropertyValue)) {
      return passedPropertyValue;
    }

    try {
      BeanInfo beanInfo = Introspector.getBeanInfo(defaultProperties.getClass());
      PropertyDescriptor propertyDescriptor = Stream.of(beanInfo.getPropertyDescriptors())
        .filter(p -> p.getName().equals(propertyName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unable to find property '" + propertyName + "'."));

      return (String)propertyDescriptor.getReadMethod().invoke(defaultProperties);
    } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
