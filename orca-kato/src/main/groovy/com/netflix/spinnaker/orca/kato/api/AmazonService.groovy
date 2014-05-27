package com.netflix.spinnaker.orca.kato.api

import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

/**
 * An interface to the Kato REST API for Amazon cloud. See {@link http://kato.test.netflix.net:7001/manual/index.html}.
 */
interface AmazonService {

    @POST("/ops")
    Observable<TaskId> requestOperations(@Body Collection<Operation> operations)

    @GET("/task")
    Observable<List<Task>> listTasks()

    @GET("/task/{id}")
    Observable<Task> lookupTask(@Path("id") String id)

}