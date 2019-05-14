/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.libdiffs;

import static com.google.common.collect.Maps.filterValues;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LibraryDiffTool {

  private final ComparableLooseVersion comparableLooseVersion;
  private final boolean includeLibraryDetails;

  public LibraryDiffTool(ComparableLooseVersion comparableLooseVersion) {
    this(comparableLooseVersion, true);
  }

  public LibraryDiffTool(
      ComparableLooseVersion comparableLooseVersion, boolean includeLibraryDetails) {
    this.comparableLooseVersion = comparableLooseVersion;
    this.includeLibraryDetails = includeLibraryDetails;
  }

  public LibraryDiffs calculateLibraryDiffs(List<Library> sourceLibs, List<Library> targetLibs) {
    LibraryDiffs libraryDiffs = new LibraryDiffs();
    libraryDiffs.setTotalLibraries(targetLibs != null ? targetLibs.size() : 0);

    BiFunction<Library, String, Diff> buildDiff =
        (Library library, String display) -> {
          Diff diff = new Diff();
          diff.setLibrary(includeLibraryDetails ? library : null);
          diff.setDisplayDiff(display);
          return diff;
        };

    try {
      if (!targetLibs.isEmpty() && !sourceLibs.isEmpty()) {
        Set<Library> uniqueCurrentList = new HashSet<>(targetLibs);
        Map<String, List<Library>> duplicatesMap =
            filterValues(
                targetLibs.stream().collect(groupingBy(Library::getName)), it -> it.size() > 1);
        sourceLibs.forEach(
            (Library oldLib) -> {
              if (!duplicatesMap.keySet().contains(oldLib.getName())) {
                Library currentLib =
                    uniqueCurrentList.stream()
                        .filter(it -> it.getName().equals(oldLib.getName()))
                        .findFirst()
                        .orElse(null);
                if (currentLib != null) {
                  if (isEmpty(currentLib.getVersion()) || isEmpty(oldLib.getVersion())) {
                    libraryDiffs.getUnknown().add(buildDiff.apply(oldLib, oldLib.getName()));
                  } else if (currentLib.getVersion() != null && oldLib.getVersion() != null) {
                    int comparison =
                        comparableLooseVersion.compare(
                            currentLib.getVersion(), oldLib.getVersion());
                    if (comparison == 1) {
                      libraryDiffs
                          .getUpgraded()
                          .add(
                              buildDiff.apply(
                                  oldLib,
                                  format(
                                      "%s: %s -> %s",
                                      oldLib.getName(),
                                      oldLib.getVersion(),
                                      currentLib.getVersion())));
                    }
                    if (comparison == -1) {
                      libraryDiffs
                          .getDowngraded()
                          .add(
                              buildDiff.apply(
                                  oldLib,
                                  format(
                                      "%s: %s -> %s",
                                      oldLib.getName(),
                                      oldLib.getVersion(),
                                      currentLib.getVersion())));
                    }
                  }
                } else {
                  libraryDiffs
                      .getRemoved()
                      .add(
                          buildDiff.apply(
                              oldLib, format("%s: %s", oldLib.getName(), oldLib.getVersion())));
                }
              }
            });

        uniqueCurrentList.stream()
            .filter(it -> !sourceLibs.contains(it))
            .forEach(
                newLib ->
                    libraryDiffs
                        .getAdded()
                        .add(
                            buildDiff.apply(
                                newLib, format("%s: %s", newLib.getName(), newLib.getVersion()))));

        duplicatesMap.forEach(
            (key, value) -> {
              Library currentLib =
                  targetLibs.stream()
                      .filter(it -> it.getName().equals(key))
                      .findFirst()
                      .orElse(null);
              if (currentLib != null) {
                boolean valid =
                    value.stream()
                            .map(Library::getVersion)
                            .filter(Objects::nonNull)
                            .collect(groupingBy(Function.identity()))
                            .keySet()
                            .size()
                        > 1;
                if (valid) {
                  String displayDiff =
                      format(
                          "%s: %s",
                          currentLib.getName(),
                          value.stream().map(Library::getVersion).collect(joining(", ")));
                  libraryDiffs.getDuplicates().add(buildDiff.apply(currentLib, displayDiff));
                }
              }
            });

        libraryDiffs.setHasDiff(
            !libraryDiffs.getDowngraded().isEmpty()
                || !libraryDiffs.getUpgraded().isEmpty()
                || !libraryDiffs.getAdded().isEmpty()
                || !libraryDiffs.getRemoved().isEmpty());
      }

      return libraryDiffs;
    } catch (Exception e) {
      throw new RuntimeException("Exception occurred while calculating library diffs", e);
    }
  }
}
