'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.subnet.read.service', [
    require('core/api/api.service'),
    require('core/cache/infrastructureCaches.js')
  ])
  .factory('azureSubnetReader', function (API, infrastructureCaches) {

    function listSubnets() {
      return API.all('subnets')
        .useCache(infrastructureCaches.subnets)
        .getList()
        .then(function(subnets) {
          return subnets.map(function(subnet) {
            subnet.label = subnet.purpose;
            subnet.deprecated = !!subnet.deprecated;
            if (subnet.deprecated) {
              subnet.label += ' (deprecated)';
            }
            return subnet;
          });
        });
    }

    return {
      listSubnets: listSubnets
    };

  });
