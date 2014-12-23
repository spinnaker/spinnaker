'use strict';


angular.module('deckApp')
  .factory('igorService', function (settings, Restangular) {

    function listMasters() {
      return Restangular.one('builds').getList();
    }

    function listJobsForMaster(master) {
      return Restangular.one('builds', master).all('jobs').getList();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster
    };

});
