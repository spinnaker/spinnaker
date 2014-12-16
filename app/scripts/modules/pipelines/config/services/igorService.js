'use strict';


angular.module('deckApp')
  .factory('igorService', function (settings, Restangular) {

    var igorEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);
    });

    function listMasters() {
      return igorEndpoint.one('builds').getList();
    }

    function listJobsForMaster(master) {
      return igorEndpoint.one('builds', master).all('jobs').getList();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster
    };

});
