/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OnDemandTypeTest {

  @Test
  void onDemandTypesWithSameValueShouldBeEqual() {
    assertEquals(new OnDemandType("instance"), new OnDemandType("instance"));
  }

  @Test
  void onDemandTypesWithSameValueShouldBeEqualRegardlessOfCase() {
    assertEquals(new OnDemandType("Instance"), new OnDemandType("instance"));
  }

  @Test
  void onDemandTypesWithDifferentValuesShouldNotBeEqual() {
    assertNotEquals(new OnDemandType("instance"), new OnDemandType("job"));
  }

  @Test
  void onDemandTypesWithSameValueShouldHaveSameHashCode() {
    assertEquals(new OnDemandType("instance").hashCode(), new OnDemandType("instance").hashCode());
  }

  @Test
  void onDemandTypesWithSameValueShouldHaveSameHashCodeRegardlessOfCase() {
    assertEquals(new OnDemandType("Instance").hashCode(), new OnDemandType("instance").hashCode());
  }

  @Test
  void onDemandTypesWithDifferentValuesShouldHaveDifferentHashCodes() {
    assertNotEquals(new OnDemandType("instance").hashCode(), new OnDemandType("job").hashCode());
  }

  @Test
  void toStringShouldReturnValue() {
    assertEquals(new OnDemandType("instance").toString(), "instance");
  }
}
