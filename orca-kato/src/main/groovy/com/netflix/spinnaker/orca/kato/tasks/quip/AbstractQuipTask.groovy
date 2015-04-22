package com.netflix.spinnaker.orca.kato.tasks.quip

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.oort.InstanceService
import retrofit.RestAdapter

/**
 * Created by dzapata on 4/21/15.
 */
abstract class AbstractQuipTask implements Task {
  InstanceService createInstanceService(RestAdapter restAdapter) {
    return restAdapter.create(InstanceService.class)
  }
}
