/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import java.util.List;
import retrofit.http.GET;

public interface FeaturesRestService {

<<<<<<< HEAD
  @GET("/features/stages")
  List<AvailableStage> getStages();
=======
  @GET("features/stages")
  Call<List<AvailableStage>> getStages();
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))

  public static class AvailableStage {
    public String name;
    public Boolean enabled;
  }
}
