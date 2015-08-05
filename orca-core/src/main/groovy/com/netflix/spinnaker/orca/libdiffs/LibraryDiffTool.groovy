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

package com.netflix.spinnaker.orca.libdiffs
/**
 * @author sthadeshwar
 */
class LibraryDiffTool {

  private final ComparableLooseVersion comparableLooseVersion

  LibraryDiffTool(ComparableLooseVersion comparableLooseVersion) {
    this.comparableLooseVersion = comparableLooseVersion
  }

  LibraryDiffs calculateLibraryDiffs(List<Library> sourceLibs, List<Library> targetLibs) {
    LibraryDiffs libraryDiffs = new LibraryDiffs()
    libraryDiffs.unknown = []
    libraryDiffs.unchanged = []
    libraryDiffs.upgraded = []
    libraryDiffs.downgraded = []
    libraryDiffs.duplicates = []
    libraryDiffs.removed = []
    libraryDiffs.added = []
    libraryDiffs.totalLibraries = targetLibs?.size()

    try {
      if (targetLibs && sourceLibs) {
        List<Library> uniqueCurrentList = targetLibs.unique(false)
        Map<String,List<Library>> duplicatesMap = targetLibs.groupBy { it.name }.findAll { it.value.size() > 1 }
        sourceLibs.each { Library oldLib ->
          if (!duplicatesMap.keySet().contains(oldLib.name)) {
            Library currentLib = uniqueCurrentList.find { it.name == oldLib.name }
            if (currentLib) {
              if (!currentLib.version || !oldLib.version) {
                libraryDiffs.unknown << new Diff(library: oldLib, displayDiff: "${oldLib.name}")
              } else if (currentLib.version && oldLib.version) {
                int comparison = comparableLooseVersion.compare(currentLib.version, oldLib.version)
                if (comparison == 0) {
                  libraryDiffs.unchanged << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${currentLib.version}")
                }
                if (comparison == 1) {
                  libraryDiffs.upgraded << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version} -> " +
                    "${currentLib.version}")
                }
                if (comparison == -1) {
                  libraryDiffs.downgraded << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version} -> " +
                    "${currentLib.version}")
                }
              }
            } else {
              libraryDiffs.removed << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version}")
            }
          }
        }

        (uniqueCurrentList - sourceLibs).each { Library newLib ->
          libraryDiffs.added << new Diff(library: newLib, displayDiff: "${newLib.name}: ${newLib.version}")
        }

        duplicatesMap.each { key, value ->
          Library currentLib = targetLibs.find { it.name == key }
          if (currentLib) {
            boolean valid = value.collect { it.version }.findAll { it != null }.groupBy { it }.keySet().size() > 1
            if (valid) {
              String displayDiff = "${currentLib.name}: ${value.collect { it.version }.join(", ")}"
              displayDiff = displayDiff.substring(0, displayDiff.length()-1)
              libraryDiffs.duplicates << new Diff(library: currentLib, displayDiff: displayDiff)
            }
          }
        }

        libraryDiffs.hasDiff = libraryDiffs.downgraded || libraryDiffs.upgraded || libraryDiffs.added || libraryDiffs.removed
      }

      return libraryDiffs
    } catch (e) {
      throw new RuntimeException("Exception occurred while calculating library diffs", e)
    }
  }

}
