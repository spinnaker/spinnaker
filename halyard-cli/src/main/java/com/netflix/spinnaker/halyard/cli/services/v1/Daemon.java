/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.services.v1;

import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;

import java.net.ConnectException;

public class Daemon {
  public static DaemonService getService() {
    if (service == null) {
      boolean debug =  GlobalOptions.getGlobalOptions().isDebug();
      service = createService(debug);
    }

    return service;
  }

  // TODO(lwander): setup config file for this
  static final private String endpoint = "http://localhost:8064";

  static private DaemonService service;

  private static DaemonService createService(boolean log) {
    return new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(new OkClient())
        .setLogLevel(log ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
        .build()
        .create(DaemonService.class);
  }
}

