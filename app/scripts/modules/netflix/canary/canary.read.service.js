'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.canary.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('canaryReadService', function(Restangular) {

    let getCanaryById = (canaryId) => {
      return Restangular.one('canaries').one(canaryId).get();
    };

    let getCanaryConfigsByApplication = (applicationName) => {
      return Restangular.one('canaryConfigs').one(applicationName).getList();
    };

    return {
      getCanaryById: getCanaryById,
      getCanaryConfigsByApplication: getCanaryConfigsByApplication
    };

  });
