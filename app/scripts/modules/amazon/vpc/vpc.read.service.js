'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.vpc.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../utils/lodash.js'),
    require('../../core/cache/infrastructureCaches.js')
  ])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches ) {

    function listVpcs() {
      return Restangular.all('vpcs')
        .withHttpConfig({cache: infrastructureCaches.vpcs})
        .getList()
        .then(function(vpcs) {
          return vpcs.map(function(vpc) {
            vpc.label = vpc.name;
            vpc.deprecated = !!vpc.deprecated;
            if (vpc.deprecated) {
              vpc.label += ' (deprecated)';
            }
            return vpc.plain();
          });
        });
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

  }).name;
