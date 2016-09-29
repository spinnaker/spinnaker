'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.service', [
  require('../../../../api/api.service'),
  require('../../../../account/account.service.js'),
  require('../../../../config/settings.js'),
])
  .factory('bakeryService', function($q, API, accountService, settings) {

    function getRegions(provider) {
      if (_.has(settings, `providers.${provider}.bakeryRegions`)) {
        return $q.when(_.get(settings, `providers.${provider}.bakeryRegions`));
      }
      return accountService.getUniqueAttributeForAllAccounts('regions')(provider).then(regions => regions.sort());
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
