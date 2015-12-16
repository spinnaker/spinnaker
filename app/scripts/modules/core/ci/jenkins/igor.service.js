'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.ci.jenkins.igor.service', [
  require('../../config/settings.js'),
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('igorService', function (settings, Restangular) {

    function listMasters() {
      return Restangular.one('builds').getList();
    }

    function listJobsForMaster(master) {
      return Restangular.one('builds', master).all('jobs').getList();
    }

    function listBuildsForJob(master, job) {
      return Restangular.one('builds', master).one('jobs', job).all('builds').getList();
    }

    function getJobConfig(master, job){
      return Restangular.one('builds', master).one('jobs', job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      listBuildsForJob: listBuildsForJob,
      getJobConfig: getJobConfig,
    };

});
