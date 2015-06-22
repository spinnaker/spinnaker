'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet.read.service', [require('restangular'), 'spinnaker.caches.infrastructure'])
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
