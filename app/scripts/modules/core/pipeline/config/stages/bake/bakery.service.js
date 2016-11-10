'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.service', [
  API_SERVICE,
  ACCOUNT_SERVICE,
  require('core/config/settings.js'),
])
  .factory('bakeryService', function($q, API, accountService, settings) {

    function getRegions(provider) {
      if (_.has(settings, `providers.${provider}.bakeryRegions`)) {
        return $q.when(_.get(settings, `providers.${provider}.bakeryRegions`));
      }
      return accountService.getUniqueAttributeForAllAccounts(provider, 'regions').then(regions => regions.sort());
    }

    function getBaseOsOptions(provider) {
      if (provider) {
        return getBaseOsOptions().then(function(options) {
          return _.find(options, { cloudProvider: provider });
        });
      }
      return API
        .one('bakery')
        .one('options')
        .useCache()
        .getList();
    }

    function getBaseLabelOptions() {
      return $q.when(['release', 'candidate', 'previous', 'unstable']);
    }

    function getVmTypes() {
      return $q.when(['hvm', 'pv']);
    }

    function getStoreTypes() {
      return $q.when(['ebs', 's3', 'docker']);
    }

    return {
      getRegions: getRegions,
      getBaseOsOptions: getBaseOsOptions,
      getVmTypes: getVmTypes,
      getBaseLabelOptions: getBaseLabelOptions,
      getStoreTypes: getStoreTypes,
    };
  });
