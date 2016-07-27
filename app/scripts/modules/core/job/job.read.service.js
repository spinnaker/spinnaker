'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.job.read.service', [
    require('../api/api.service')
  ])
  .factory('jobReader', function (API) {

    function getJob(application, account, region, jobName) {
      return API.one('applications').one(application).all('jobs').all(account).all(region).one(jobName).get();
    }

    return {
      getJob: getJob,
    };
  });
