'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular')
  ])
  .factory('instanceReader', function (Restangular) {

    function getInstanceDetails(account, region, id) {
      return Restangular.all('instances').one(account).one(region).one(id).get();
    }

    function getConsoleOutput(account, region, id, provider) {
      return Restangular.all('instances').all(account).all(region).one(id, 'console').get({provider: provider});
    }

    return {
      getInstanceDetails: getInstanceDetails,
      getConsoleOutput: getConsoleOutput,
    };

  });
