'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs.read.service', [
    require('../../core/api/api.service')
  ])
  .factory('keyPairsReader', function ($q, API) {

    function listKeyPairs() {
      return API.one('keyPairs')
        .useCache()
        .get()
        .then(keyPairs => keyPairs.sort((a, b) => a.keyName.localeCompare(b.keyName)));
    }

    return {
      listKeyPairs: listKeyPairs
    };

  });
