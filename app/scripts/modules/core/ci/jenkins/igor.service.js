'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.ci.jenkins.igor.service', [
  require('../../config/settings.js'),
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('igorService', function (settings, Restangular) {
    var RestangularNoEncoding;

    RestangularNoEncoding = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setEncodeIds(false);
    });

    function listMasters() {
      return Restangular.one('v2').one('builds').getList();
    }

    function listJobsForMaster(master) {
      return Restangular.one('v2').one('builds', master).all('jobs').getList();
    }

    function listBuildsForJob(master, job) {
      return RestangularNoEncoding.one('v2').one('builds', master).one('builds').one(job).getList();
    }

    function getJobConfig(master, job) {
      return RestangularNoEncoding.one('v2').one('builds', master).one('jobs', job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      listBuildsForJob: listBuildsForJob,
      getJobConfig: getJobConfig,
    };

});
