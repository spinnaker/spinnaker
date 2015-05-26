'use strict';


angular
  .module('spinnaker.vpc.read.service', ['restangular', 'spinnaker.caches.infrastructure'])
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
