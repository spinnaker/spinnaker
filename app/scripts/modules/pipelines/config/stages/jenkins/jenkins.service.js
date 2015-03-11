'use strict';

angular.module('deckApp.pipelines.stage.jenkins')
  .factory('jenkinsService', function(Restangular) {

    function listMasters() {
      return Restangular.one('builds').getList();
    }

    function listJobsForMaster(master) {
      return Restangular.one('builds', master).all('jobs').getList();
    }

    function getJobConfig(master, job){
      return Restangular.one('builds', master).one('job', job).get();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster,
      getJobConfig: getJobConfig
    };
});
