'use strict';

angular
  .module('spinnaker.subnet.read.service', ['restangular', 'spinnaker.caches.infrastructure'])
  .factory('subnetReader', function (Restangular, infrastructureCaches) {

    function listSubnets() {
      return Restangular.all('subnets')
        .withHttpConfig({cache: infrastructureCaches.subnets})
        .getList();
    }

    return {
      listSubnets: listSubnets
    };

  });
