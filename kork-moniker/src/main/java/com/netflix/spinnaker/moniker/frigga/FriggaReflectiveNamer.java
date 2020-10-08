/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.moniker.frigga;

import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FriggaReflectiveNamer implements Namer<Object> {
  @Override
  public Moniker deriveMoniker(Object obj) {
    String name = getName(obj);
    com.netflix.frigga.Names names = com.netflix.frigga.Names.parseName(name);
    return Moniker.builder()
        .app(names.getApp())
        .stack(names.getStack())
        .detail(names.getDetail())
        .cluster(names.getCluster())
        .sequence(names.getSequence())
        .build();
  }

  @Override
  public void applyMoniker(Object obj, Moniker moniker) {
    AutoScalingGroupNameBuilder nameBuilder = new AutoScalingGroupNameBuilder();
    nameBuilder.withAppName(moniker.getApp());
    nameBuilder.withStack(moniker.getStack());
    nameBuilder.withDetail(moniker.getDetail());

    String name = nameBuilder.buildGroupName();
    if (moniker.getSequence() != null) {
      name = String.format("%s-v%03d", name, moniker.getSequence());
    }

    setName(obj, name);
  }

  private void setName(Object obj, String name) {
    Class clazz = obj.getClass();

    // First walk up the object hierarchy and try to find some "setName" method to call
    while (clazz != Object.class) {
      try {
        Method setName = clazz.getDeclaredMethod("setName", String.class);
        setName.setAccessible(true);
        setName.invoke(obj, name);
        return;
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
      }

      clazz = clazz.getSuperclass();
    }

    clazz = obj.getClass();

    // Since the first attempt didn't work, retry with raw fields
    while (clazz != Object.class) {
      try {
        Field nameField = clazz.getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(obj, name);
        return;
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }

      clazz = clazz.getSuperclass();
    }

    throw new IllegalArgumentException(
        "No way to infer how to name " + obj.getClass().getSimpleName());
  }

  private String getName(Object obj) {
    Class clazz = obj.getClass();

    // If the object is a String, just return it.
    if (clazz == String.class) {
      return (String) obj;
    }

    // First walk up the object hierarchy and try to find some "getName" method to call
    while (clazz != Object.class) {
      try {
        Method setName = clazz.getDeclaredMethod("getName");
        setName.setAccessible(true);
        return (String) setName.invoke(obj);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
      }

      clazz = clazz.getSuperclass();
    }

    clazz = obj.getClass();

    // Since the first attempt didn't work, retry with raw fields
    while (clazz != Object.class) {
      try {
        Field nameField = clazz.getDeclaredField("name");
        nameField.setAccessible(true);
        return (String) nameField.get(obj);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }

      clazz = clazz.getSuperclass();
    }

    throw new IllegalArgumentException(
        "No way to infer how to name " + obj.getClass().getSimpleName());
  }
}
