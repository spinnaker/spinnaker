'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.canary.read.service', [
    require('core/api/api.service')
  ])
  .factory('canaryReadService', function(API) {

    let getCanaryById = (canaryId) => {
      return API.one('canaries').one(canaryId).get();
    };

    let getCanaryConfigsByApplication = (applicationName) => {
      return API.one('canaryConfigs').one(applicationName).getList();
    };

    return {
      getCanaryById: getCanaryById,
      getCanaryConfigsByApplication: getCanaryConfigsByApplication
    };

  });
