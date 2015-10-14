'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.vpc.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/utils/lodash.js'),
    require('../../core/cache/infrastructureCaches.js')
  ])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches) {

    let cachedVpcs = null;

    function listVpcs() {
      if (cachedVpcs) {
        return $q.when(cachedVpcs);
      }
      return Restangular.all('vpcs')
        .withHttpConfig({cache: infrastructureCaches.vpcs})
        .getList()
        .then(function(vpcs) {
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

  }).name;
