'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs.read.service', [require('restangular'), 'spinnaker.caches.infrastructure'])
  .factory('keyPairsReader', function ($q, Restangular, infrastructureCaches) {

    function listKeyPairs() {
      return Restangular.all('keyPairs')
        .withHttpConfig({cache: infrastructureCaches.keyPairs})
        .getList();
    }

    return {
      listKeyPairs: listKeyPairs
    };

  });
