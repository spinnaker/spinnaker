'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/cache/infrastructureCacheConfig.js')
  ])
  .factory('keyPairsReader', function ($q, Restangular, infrastructureCaches) {

    function listKeyPairs() {
      return Restangular.all('keyPairs')
        .withHttpConfig({cache: infrastructureCaches.keyPairs})
        .getList();
    }

    return {
      listKeyPairs: listKeyPairs
    };

  })
  .name;
