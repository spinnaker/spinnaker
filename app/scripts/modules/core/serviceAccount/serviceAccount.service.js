'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serviceAccount.service', [
  API_SERVICE,
  require('../config/settings.js'),
])
  .factory('serviceAccountService', function(settings, API, $q) {

    let getServiceAccounts = function() {
      if (!settings.feature.fiatEnabled) {
        return $q.resolve([]);
      }

      return API.one('auth').one('user').one('serviceAccounts').get();
    };

    return {
      getServiceAccounts: getServiceAccounts,
    };
  });
