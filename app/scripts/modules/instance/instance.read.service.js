'use strict';

angular
  .module('spinnaker.instance.read.service', ['restangular'])
  .factory('instanceReader', function (Restangular) {

    function getInstanceDetails(account, region, id) {
      return Restangular.all('instances').one(account).one(region).one(id).get();
    }

    return {
      getInstanceDetails: getInstanceDetails
    };

  });
