'use strict';

angular
  .module('deckApp.instance.read.service', ['restangular'])
  .factory('instanceReader', function (Restangular) {

    function getInstanceDetails(account, region, id) {
      return Restangular.all('instances').one(account).one(region).one(id).get();
    }

    return {
      getInstanceDetails: getInstanceDetails
    };

  });
