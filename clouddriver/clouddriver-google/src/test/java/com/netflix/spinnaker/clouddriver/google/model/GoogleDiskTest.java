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

package com.netflix.spinnaker.clouddriver.google.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GoogleDiskTest {

  @Test
  public void testSettingDiskTypePdSsd() {
    GoogleDisk disk = new GoogleDisk();
    disk.setType("pd-ssd");

    assertEquals(GoogleDiskType.PD_SSD, disk.getType());
  }

  @Test
  public void testSettingDiskTypeHyperdiskBalanced() {
    GoogleDisk disk = new GoogleDisk();
    disk.setType("hyperdisk-balanced");

    assertEquals(GoogleDiskType.HYPERDISK_BALANCED, disk.getType());
  }

  @Test
  public void testDefaultDiskTypeOnUnknown() {
    GoogleDisk disk = new GoogleDisk();
    disk.setType("UNKNOWN");

    assertEquals(GoogleDiskType.PD_STANDARD, disk.getType());
  }

  @Test
  public void testPersistentDiskDetection() {
    GoogleDisk disk = new GoogleDisk();
    disk.setType("pd-ssd");

    assertTrue(disk.isPersistent());
  }
}
