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
 *
 */
package com.netflix.spinnaker.front50;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.SneakyThrows;

/**
 * Helper class for dealing with untyped data / Groovy migration.
 *
 * @deprecated This class is only meant to help with backwards compatibility of migrating from
 *     Groovy to Java. It should not be used for any new contributions.
 */
@Deprecated
@NonnullByDefault
public class UntypedUtils {

  @Nullable
  @SneakyThrows
  public static Object getProperty(Object obj, String propertyName) {
    Field f = obj.getClass().getDeclaredField(propertyName);
    f.setAccessible(true);
    return f.get(obj);
  }

  @SneakyThrows
  public static void setProperty(Object obj, String propertyName, @Nullable Object value) {
    Field f = obj.getClass().getDeclaredField(propertyName);
    f.setAccessible(true);
    f.set(obj, value);
  }

  public static boolean hasProperty(Object obj, String propertyName) {
    return Arrays.stream(obj.getClass().getDeclaredFields())
        .anyMatch(it -> it.getName().equals(propertyName));
  }

  @SneakyThrows
  public static Map<String, String> getProperties(Object obj) {
    Map<String, String> m = new HashMap<>();
    Arrays.stream(obj.getClass().getDeclaredFields())
        .filter(UntypedUtils::notGroovyField)
        .forEach(
            f -> {
              Object v = getProperty(obj, f.getName());
              if (v == null) {
                m.put(f.getName(), null);
              } else {
                m.put(f.getName(), v.toString());
              }
            });
    return m;
  }

  /** This is hardly scientific... */
  private static boolean notGroovyField(Field f) {
    return !(Modifier.isTransient(f.getModifiers())
        || f.getName().startsWith("$")
        || f.getName().startsWith("this$"));
  }
}
