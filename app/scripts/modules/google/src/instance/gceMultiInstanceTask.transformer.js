'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.google.instance.multiInstance.task.transformer', [])
  .factory('gceMultiInstanceTaskTransformer', function() {
    // custom transformers for specific tasks,
    // e.g. "rebootInstances" needs an empty "interestingHealthProviderNames" array
    const transformers = {
      rebootInstances: job => {
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
