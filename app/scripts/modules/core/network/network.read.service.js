'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.network.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../utils/lodash.js'),
    require('../cache/infrastructureCaches.js')
  ])
  .factory('networkReader', function (Restangular, infrastructureCaches ) {

    function listNetworks() {
      return Restangular.one('networks')
        .withHttpConfig({cache: infrastructureCaches.networks})
        .get();
    }

    function listNetworksByProvider(cloudProvider) {
      return Restangular.one('networks', cloudProvider)
        .withHttpConfig({cache: infrastructureCaches.networks})
        .getList();
    }

    return {
      listNetworks: listNetworks,
      listNetworksByProvider: listNetworksByProvider,
    };

  });
