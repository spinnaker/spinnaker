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

package com.netflix.spinnaker.clouddriver.titus.client;

import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface TitusRestAdapter {

    @GET("/v2/jobs/{jobId}")
    Call<Job> getJob(@Path("jobId") String jobId);

    @POST("/v2/jobs")
    Call<SubmitJobResponse> submitJob(@Body JobDescription jobDescription);

    @PATCH("/v2/jobs/{jobId}")
    Call<Void> updateJob(@Path("jobId") String jobId, @Body Map<String, Object>jobAttributes);

    @POST("/v2/jobs/kill")
    Call<Void> killJob(@Body RequestBody requestBody); //String jobId (but specifying that triggers the JSON converter..)

    @GET("/v2/tasks/{taskId}")
    Call<Task> getTask(@Path("taskId") String taskId);

    @GET("/v2/jobs")
    Call<List<Job>> getJobsByTaskState(@Query("taskState") TaskState taskState);

    @GET("/v2/jobs")
    Call<List<Job>> getJobsByType(@Query("type") String type);

    @GET("/v2/jobs")
    Call<List<Job>> getJobsByTag(@Query("tags") String tag);

    @GET("/v2/jobs")
    Call<List<Job>> getJobsByUser(@Query("user") String user);

    @POST("/v2/tasks/terminate/{taskId}")
    Call<Void> terminateTask(@Path("taskId") String taskId);

    @GET("/v2/logs/{taskId}")
    Call<Logs> getLogs(@Path("taskId") String taskId);
}
