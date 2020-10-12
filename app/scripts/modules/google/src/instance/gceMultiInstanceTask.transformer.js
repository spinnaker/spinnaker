'use strict';

import { module } from 'angular';

export const GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER =
  'spinnaker.google.instance.multiInstance.task.transformer';
export const name = GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER; // for backwards compatibility
module(GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER, []).factory('gceMultiInstanceTaskTransformer', function () {
  // custom transformers for specific tasks,
  // e.g. "rebootInstances" needs an empty "interestingHealthProviderNames" array
  const transformers = {
    rebootInstances: (job) => {
      job.interestingHealthProviderNames = [];
    },
  };

  // adds the "zone" field to all jobs
  const transformAll = (instanceGroup, job) => {
    job.zone = instanceGroup.instances[0].availabilityZone;
  };

  const transformJob = (instanceGroup, job) => {
    transformAll(instanceGroup, job);
    if (transformers[job.type]) {
      transformers[job.type](job);
    }
  };

  return {
    transform: transformJob,
  };
});
