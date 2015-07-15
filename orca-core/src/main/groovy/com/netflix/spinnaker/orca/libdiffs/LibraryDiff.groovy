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

class LibraryDiff {
  List<Library> sourceLibs
  List<Library> targetLibs

  List<Diff> unknown = []
  List<Diff> unchanged = []
  List<Diff> upgraded = []
  List<Diff> downgraded = []
  List<Diff> duplicates = []
  List<Diff> removed = []
  List<Diff> added = []

  boolean hasDiff
  int totalLibraries

  LibraryDiff(List<Library> sourceLibs, List<Library> targetLibs) {
    this.sourceLibs = sourceLibs
    this.targetLibs = targetLibs
  }

  Map diffJars() {
    try {
      if (targetLibs && sourceLibs) {
        List<Library> uniqueCurrentList = targetLibs.unique(false)
        Map<String,List<Library>> duplicatesMap = targetLibs.groupBy { it.name }.findAll { it.value.size() > 1 }
        sourceLibs.each { Library oldLib ->
          if (!duplicatesMap.keySet().contains(oldLib.name)) {
            Library currentLib = uniqueCurrentList.find { it.name == oldLib.name }
            if (currentLib) {
              if (!currentLib.version || !oldLib.version) {
                unknown << new Diff(library: oldLib, displayDiff: "${oldLib.name}")
              } else if (currentLib.version && oldLib.version) {
                int comparison = new LooseVersion(currentLib.version).compareTo(new LooseVersion(oldLib.version))
                if (comparison == 0) unchanged << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${currentLib.version}")
                if (comparison == 1) upgraded << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version} -> ${currentLib.version}")
                if (comparison == -1) {
                  downgraded << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version} -> ${currentLib.version}")
                }
              }
            } else {
              removed << new Diff(library: oldLib, displayDiff: "${oldLib.name}: ${oldLib.version}")
            }
          }
        }
        (uniqueCurrentList - sourceLibs).each { Library newLib ->
          added << new Diff(library: newLib, displayDiff: "${newLib.name}: ${newLib.version}")
        }
        duplicatesMap.each { key, value ->
          Library currentLib = targetLibs.find { it.name == key }
          if (currentLib) {
            StringBuffer versions = new StringBuffer()
            boolean valid = value.collect { it.version }.findAll { it != null }.groupBy { it }.keySet().size() > 1
            if (valid) {
              value.each {
                versions << "${it.version}, "
              }

              String displayDiff = "${currentLib.name}: ${versions.toString()}".trim()
              displayDiff = displayDiff.substring(0, displayDiff.length()-1)


              duplicates << new Diff(library: currentLib, displayDiff: displayDiff)
            }
          }
        }

        totalLibraries = targetLibs.size()
        hasDiff = downgraded || upgraded || added || removed

      }
      return [downgraded : downgraded, duplicates: duplicates, removed: removed, upgraded: upgraded, added: added, unknown: unknown]
    } catch (e) {
      throw new RuntimeException("Exception occurred while calculating library diff", e)
    }
  }
}
