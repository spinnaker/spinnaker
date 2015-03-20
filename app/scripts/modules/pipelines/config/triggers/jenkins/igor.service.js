'use strict';


angular.module('deckApp.pipelines.trigger.jenkins.service', [
  'deckApp.caches.infrastructure',
])
  .factory('igorService', function (settings, Restangular, infrastructureCaches) {

    function listMasters() {
      return Restangular
        .one('builds')
        .withHttpConfig({cache: infrastructureCaches.buildMasters})
        .getList();
    }

    function listJobsForMaster(master) {
      return Restangular
        .one('builds', master)
        .all('jobs')
        .withHttpConfig({cache: infrastructureCaches.buildJobs})
        .getList();
    }

    function getJobConfig(master, job){
      return Restangular.one('builds', master).one('job', job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      getJobConfig: getJobConfig,
    };

});
