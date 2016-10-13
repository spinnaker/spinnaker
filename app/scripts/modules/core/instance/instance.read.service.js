'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.read.service', [API_SERVICE])
  .factory('instanceReader', function (API) {

    function getInstanceDetails(account, region, id) {
      return API.one('instances').one(account).one(region).one(id).get();
    }

    function getConsoleOutput(account, region, id, provider) {
      return API.one('instances').all(account).all(region).one(id, 'console').withParams({provider: provider}).get();
    }

    return {
      getInstanceDetails: getInstanceDetails,
      getConsoleOutput: getConsoleOutput,
    };

  });
