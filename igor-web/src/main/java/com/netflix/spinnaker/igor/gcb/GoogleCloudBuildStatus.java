/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

/**
 * An enum of possible statuses of a GCB build. One of the primary purposes of this enum is to
 * handle ordering of statuses to allow us to order build notifications.
 */
public enum GoogleCloudBuildStatus {
  STATUS_UNKNOWN(StatusType.UNKNOWN),
  QUEUED(StatusType.QUEUED),
  WORKING(StatusType.WORKING),
  SUCCESS(StatusType.COMPLETE),
  FAILURE(StatusType.COMPLETE),
  INTERNAL_ERROR(StatusType.COMPLETE),
  TIMEOUT(StatusType.COMPLETE),
  CANCELLED(StatusType.COMPLETE);

  private final StatusType statusType;

  GoogleCloudBuildStatus(StatusType statusType) {
    this.statusType = statusType;
  }

  public boolean greaterThanOrEqualTo(GoogleCloudBuildStatus other) {
    return this.statusType.compareTo(other.statusType) >= 0;
  }

  public boolean isComplete() {
    return statusType == StatusType.COMPLETE;
  }

  private enum StatusType {
    UNKNOWN,
    QUEUED,
    WORKING,
    COMPLETE
  }
}
