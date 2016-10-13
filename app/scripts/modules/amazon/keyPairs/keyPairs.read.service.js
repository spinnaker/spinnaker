'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs.read.service', [API_SERVICE])
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
