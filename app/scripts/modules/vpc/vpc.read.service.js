'use strict';


angular
  .module('deckApp.vpc.read.service', ['restangular', 'deckApp.caches.infrastructure'])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches ) {

    function listVpcs() {
      return Restangular.all('vpcs')
        .withHttpConfig({cache: infrastructureCaches.vpcs})
        .getList();
    }

    return {
      listVpcs: listVpcs
    };

  });
