'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/cache/infrastructureCaches.js')
  ])
  .factory('subnetReader', function (Restangular, infrastructureCaches) {

    function listSubnets() {
      return Restangular.all('subnets')
        .withHttpConfig({cache: infrastructureCaches.subnets})
        .getList()
        .then(function(subnets) {
          return subnets.map(function(subnet) {
            subnet.label = subnet.purpose;
            subnet.deprecated = !!subnet.deprecated;
            if (subnet.deprecated) {
              subnet.label += ' (deprecated)';
            }
            return subnet.plain();
          });
        });
    }

    return {
      listSubnets: listSubnets
    };

  });
