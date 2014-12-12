'use strict';


angular.module('deckApp')
  .factory('igorService', function (settings, Restangular) {

    var igorEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.igorUrl);
    });

    function listMasters() {
      return igorEndpoint.one('masters/').getList();
    }

    function listJobsForMaster(master) {
      return igorEndpoint.all('jobs').one(master + '/').getList();
    }

    return {
      listMasters: listMasters,
      listJobsForMaster: listJobsForMaster
    };

});
