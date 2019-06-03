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

package com.netflix.spinnaker.halyard.core;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

public class FileModeUtils {
  private static PosixFilePermission[] permissionBits =
      new PosixFilePermission[] {
        PosixFilePermission.OTHERS_EXECUTE,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_READ
      };

  private static int directoryBit = 1 << 14;
  private static int fileBit = 1 << 15;

  /**
   * Given a set of PosixFilePermissions, returns an integer with the corresponding permission bits
   * set
   *
   * @param posixPermissions Set of permissions to represent as an integer
   * @return Integer representing the permission bits
   */
  public static int getFileMode(Set<PosixFilePermission> posixPermissions) {
    int fileMode = 0;
    for (int i = 0; i < permissionBits.length; i++) {
      if (posixPermissions.contains(permissionBits[i])) {
        fileMode += 1 << i;
      }
    }

    return fileMode;
  }

  /**
   * Given an integer representing file permissions, returns a set of PosixFilePermissions
   * representing the corresponding permissions.
   *
   * @param fileMode Integer representing a file mode
   * @return Set of permissions in the input mode
   */
  public static Set<PosixFilePermission> getPosixPermissions(int fileMode) {
    Set<PosixFilePermission> posixPermissions = new HashSet<>();

    for (int i = 0; i < permissionBits.length; i++) {
      int bit = fileMode & (1 << i);
      if (bit != 0) {
        posixPermissions.add(permissionBits[i]);
      }
    }

    return posixPermissions;
  }

  /**
   * Given an integer representing a file mode, sets the bit indicating this is a file, and unsets
   * the bit indicating it is a directory.
   *
   * @param fileMode Integer representing a file mode
   * @return Input file mode, with the file bit set and the directory bit unset
   */
  public static int setFileBit(int fileMode) {
    return fileMode & (~directoryBit) | fileBit;
  }

  /**
   * Given an integer representing a file mode, sets the bit indicating this is a directory, and
   * unsets the bit indicating it is a file.
   *
   * @param fileMode Integer representing a file mode
   * @return Input file mode, with the directory bit set and the file bit unset
   */
  public static int setDirectoryBit(int fileMode) {
    return fileMode & (~fileBit) | directoryBit;
  }
}
