'use strict';


angular
  .module('spinnaker.vpc.read.service', ['restangular', 'spinnaker.caches.infrastructure'])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches ) {

    function listVpcs() {
      return Restangular.all('vpcs')
        .withHttpConfig({cache: infrastructureCaches.vpcs})
        .getList();
    }

    function getVpcName(id) {
      return listVpcs().then(function(vpcs) {
        var matches = vpcs.filter(function(test) {
          return test.id === id;
        });
        return matches.length ? matches[0].name : null;
      });
    }

    return {
      listVpcs: listVpcs,
      getVpcName: getVpcName,
    };

  });
