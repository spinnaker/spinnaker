'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.ci.jenkins.igor.service', [
  require('../../config/settings.js'),
  require('../../api/api.service'),
])
  .factory('igorService', function (settings, API) {

    function listMasters() {
      return API.one('v2').one('builds').get();
    }

    function listJobsForMaster(master) {
      return API.one('v2').one('builds').one(master).one('jobs').get();
    }

    function listBuildsForJob(master, job) {
      return API.one('v2').one('builds').one(master).one('builds').one(job).get();
    }

    function getJobConfig(master, job) {
      return API.one('v2').one('builds').one(master).one('jobs').one(job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      listBuildsForJob: listBuildsForJob,
      getJobConfig: getJobConfig,
    };

});
