/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.libdiffs;

import java.util.ArrayList;
import java.util.List;

public class LibraryDiffs {

  private List<Diff> unknown = new ArrayList<>();
  private List<Diff> unchanged = new ArrayList<>();
  private List<Diff> upgraded = new ArrayList<>();
  private List<Diff> downgraded = new ArrayList<>();
  private List<Diff> duplicates = new ArrayList<>();
  private List<Diff> removed = new ArrayList<>();
  private List<Diff> added = new ArrayList<>();
  private boolean hasDiff;
  private int totalLibraries;

  public List<Diff> getUnknown() {
    return unknown;
  }

  public void setUnknown(List<Diff> unknown) {
    this.unknown = unknown;
  }

  public List<Diff> getUnchanged() {
    return unchanged;
  }

  public void setUnchanged(List<Diff> unchanged) {
    this.unchanged = unchanged;
  }

  public List<Diff> getUpgraded() {
    return upgraded;
  }

  public void setUpgraded(List<Diff> upgraded) {
    this.upgraded = upgraded;
  }

  public List<Diff> getDowngraded() {
    return downgraded;
  }

  public void setDowngraded(List<Diff> downgraded) {
    this.downgraded = downgraded;
  }

  public List<Diff> getDuplicates() {
    return duplicates;
  }

  public void setDuplicates(List<Diff> duplicates) {
    this.duplicates = duplicates;
  }

  public List<Diff> getRemoved() {
    return removed;
  }

  public void setRemoved(List<Diff> removed) {
    this.removed = removed;
  }

  public List<Diff> getAdded() {
    return added;
  }

  public void setAdded(List<Diff> added) {
    this.added = added;
  }

  public boolean getHasDiff() {
    return hasDiff;
  }

  public boolean isHasDiff() {
    return hasDiff;
  }

  public void setHasDiff(boolean hasDiff) {
    this.hasDiff = hasDiff;
  }

  public int getTotalLibraries() {
    return totalLibraries;
  }

  public void setTotalLibraries(int totalLibraries) {
    this.totalLibraries = totalLibraries;
  }
}
