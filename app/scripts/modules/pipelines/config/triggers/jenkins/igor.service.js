'use strict';


angular.module('deckApp.pipelines.trigger.jenkins.service', [
  'restangular',
  'deckApp.settings',
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
      return Restangular.one('builds', master).one('job', job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      listBuildsForJob: listBuildsForJob,
      getJobConfig: getJobConfig,
    };

});
