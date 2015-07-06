'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.instance.read.service', [require('exports?"restangular"!imports?_=lodash!restangular')])
  .factory('instanceReader', function (Restangular) {

    function getInstanceDetails(account, region, id) {
      return Restangular.all('instances').one(account).one(region).one(id).get();
    }

    return {
      getInstanceDetails: getInstanceDetails
    };

  }).name;
