'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.job.transformer', [ ])
  .factory('kubernetesJobTransformer', function ($q) {

    function normalizeJob(job) {
      return $q.when(job); // no-op
    }

    return {
      normalizeJob: normalizeJob,
    };
  });
