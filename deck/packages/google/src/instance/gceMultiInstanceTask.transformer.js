'use strict';

export const GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER =
  'spinnaker.google.instance.multiInstance.task.transformer';
export const name = GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER; // for backwards compatibility
const transformers = {
  rebootInstances: (job) => {
    job.interestingHealthProviderNames = [];
  },
};

const transformAll = (instanceGroup, job) => {
  job.zone = instanceGroup.instances[0].availabilityZone;
};

export class GceMultiInstanceTaskTransformer {
  transform = (instanceGroup, job) => {
    transformAll(instanceGroup, job);
    if (transformers[job.type]) {
      transformers[job.type](job);
    }
  };
}
