'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.vpc.read.service', [
    require('../../core/network/network.read.service.js')
  ])
  .factory('vpcReader', function ($q, networkReader) {

    let cachedVpcs = null;

    function listVpcs() {
      if (cachedVpcs) {
        return $q.when(cachedVpcs);
      }
      return networkReader.listNetworksByProvider('aws').then(function(vpcs) {
        let results = vpcs.map(function(vpc) {
          vpc.label = vpc.name;
          vpc.deprecated = !!vpc.deprecated;
          if (vpc.deprecated) {
            vpc.label += ' (deprecated)';
          }
          return vpc.plain();
        });
        cachedVpcs = results;
        return results;
      });
    }

    function resetCache() {
      cachedVpcs = null;
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
      resetCache: resetCache,
    };

  });
