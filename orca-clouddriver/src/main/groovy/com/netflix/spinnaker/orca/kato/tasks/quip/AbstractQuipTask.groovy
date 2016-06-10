package com.netflix.spinnaker.orca.kato.tasks.quip

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient
import static retrofit.RestAdapter.LogLevel.BASIC

abstract class AbstractQuipTask implements Task {
  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(new OkClient(new OkHttpClient(retryOnConnectionFailure: false)))
      .setLogLevel(BASIC)
      .build()
    return restAdapter.create(InstanceService.class)
  }
}
