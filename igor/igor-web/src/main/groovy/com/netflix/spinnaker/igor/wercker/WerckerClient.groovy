/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import com.netflix.spinnaker.igor.wercker.model.Application
import com.netflix.spinnaker.igor.wercker.model.Pipeline
import com.netflix.spinnaker.igor.wercker.model.Run
import com.netflix.spinnaker.igor.wercker.model.RunPayload
import com.netflix.spinnaker.igor.wercker.model.Workflow
import okhttp3.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface for interacting with a Wercker service using retrofit
 */
interface WerckerClient {

    @GET('api/v3/applications/{owner}')
    Call<List<Application>> getApplicationsByOwner(
      @Header('Authorization') String authHeader,
      @Path('owner') owner)

    @GET('api/spinnaker/v1/applications')
    Call<List<Application>> getApplications(@Header('Authorization') String authHeader, @Query('limit') int limit)

    @GET('api/v3/runs')
    Call<List<Run>> getRunsForApplication(
            @Header('Authorization') String authHeader,
    @Query('applicationId') String applicationId)

    @GET('api/v3/runs')
    Call<List<Run>> getRunsForPipeline(
            @Header('Authorization') String authHeader,
    @Query('pipelineId') String pipelineId)

    @GET('api/spinnaker/v1/runs')
    Call<List<Run>> getRunsSince(
            @Header('Authorization') String authHeader,
            @Query('branch') String branch,
            @Query('pipelineIds') List<String> pipelineIds,
            @Query('limit') int limit,
    @Query('since') long since)

    @GET('api/v3/workflows')
    Call<List<Workflow>> getWorkflowsForApplication(
            @Header('Authorization') String authHeader,
    @Query('applicationId') String applicationId)

    @GET('api/v3/applications/{username}/{appName}/pipelines')
    Call<List<Pipeline>> getPipelinesForApplication(
            @Header('Authorization') String authHeader,
            @Path('username') username,
    @Path('appName') appName)

    @GET('api/v3/pipelines/{pipelineId}')
    Call<Pipeline> getPipeline(
            @Header('Authorization') String authHeader,
    @Path('pipelineId') String pipelineId)

    @POST('api/v3/runs')
    Call<Map<String, Object>> triggerBuild(
            @Header('Authorization') String authHeader,
            @Body RunPayload runPayload
    )

    @GET('api/v3/runs/{runId}')
    Call<Run> getRunById(@Header('Authorization') String authHeader,
    @Path('runId') String runId)

    @PUT('api/v3/runs/{runId}/abort')
    Call<Response> abortRun(@Header('Authorization') String authHeader,
                            @Path('runId') String runId,
                            @Body Map body)
}
