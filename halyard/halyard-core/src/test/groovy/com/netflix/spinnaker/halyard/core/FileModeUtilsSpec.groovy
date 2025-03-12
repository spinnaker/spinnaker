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

package com.netflix.spinnaker.halyard.core

import spock.lang.Specification

import java.nio.file.attribute.PosixFilePermission

class FileModeUtilsSpec extends Specification {
  def "getFileMode and getPosixPermissions are correct"() {
    setup:
    int fileMode
    Set<PosixFilePermission> permissions

    when:
    fileMode = 0644
    permissions = [
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OTHERS_READ
    ]

    then:
    FileModeUtils.getFileMode(permissions) == fileMode
    FileModeUtils.getPosixPermissions(fileMode).equals(permissions)


    when:
    fileMode = 0755
    permissions = [
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_EXECUTE
    ]

    then:
    FileModeUtils.getFileMode(permissions) == fileMode
    FileModeUtils.getPosixPermissions(fileMode).equals(permissions)

    when:
    fileMode = 0400
    permissions = [
        PosixFilePermission.OWNER_READ,
    ]

    then:
    FileModeUtils.getFileMode(permissions) == fileMode
    FileModeUtils.getPosixPermissions(fileMode).equals(permissions)

    when:
    fileMode = 0001
    permissions = [
        PosixFilePermission.OTHERS_EXECUTE
    ]

    then:
    FileModeUtils.getFileMode(permissions) == fileMode
    FileModeUtils.getPosixPermissions(fileMode).equals(permissions)

    when:
    fileMode = 0000
    permissions = []

    then:
    FileModeUtils.getFileMode(permissions) == fileMode
    FileModeUtils.getPosixPermissions(fileMode).equals(permissions)
  }

  def "setFileBit"() {
    setup:
    int fileMode

    when:
    fileMode = 0644

    then:
    FileModeUtils.setFileBit(fileMode) == 0100644

    when:
    fileMode = 0100644

    then:
    FileModeUtils.setFileBit(fileMode) == 0100644
  }

  def "setDirectoryBit"() {
    setup:
    int fileMode

    when:
    fileMode = 0755

    then:
    FileModeUtils.setDirectoryBit(fileMode) == 040755

    when:
    fileMode = 040755

    then:
    FileModeUtils.setDirectoryBit(fileMode) == 040755

    when:
    fileMode = 0100755

    then:
    FileModeUtils.setDirectoryBit(fileMode) == 040755
  }
}
