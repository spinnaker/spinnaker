'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../modules/caches/infrastructureCaches.js')
  ])
  .factory('subnetReader', function (Restangular, infrastructureCaches) {

    function listSubnets() {
      return Restangular.all('subnets')
        .withHttpConfig({cache: infrastructureCaches.subnets})
        .getList();
    }

    return {
      listSubnets: listSubnets
    };

  })
  .name;
