'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.job.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('jobReader', function (Restangular) {

    function getJob(application, account, region, jobName) {
      return Restangular.one('applications', application).all('jobs').all(account).all(region).one(jobName).get();
    }

    return {
      getJob: getJob,
    };
  });
