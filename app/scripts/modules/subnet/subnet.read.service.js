'use strict';

angular
  .module('deckApp.subnet.read.service', ['restangular', 'deckApp.caches.infrastructure'])
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
