'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.canary.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('canaryReadService', function(Restangular) {

    function getCanaryById(canaryId) {
      return Restangular.one('canaries').one(canaryId).get();
    }

    return {
      getCanaryById: getCanaryById,
    };

  });
