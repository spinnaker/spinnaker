'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../cache/infrastructureCaches.js'),
    require('../utils/lodash')
  ])
  .factory('subnetReader', function (_, Restangular, infrastructureCaches) {

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

    function listSubnetsByProvider(cloudProvider) {
      return Restangular.one('subnets', cloudProvider)
        .withHttpConfig({cache: infrastructureCaches.subnets})
        .getList();
    }

    function getSubnetByIdAndProvider(subnetId, cloudProvider = 'aws') {
      return listSubnetsByProvider(cloudProvider)
        .then((results) => {
          return _.first(_.filter(results.plain(), subnet => subnet.id === subnetId));
        });
    }

    return {
      listSubnets: listSubnets,
      listSubnetsByProvider: listSubnetsByProvider,
      getSubnetByIdAndProvider: getSubnetByIdAndProvider,
    };

  });
