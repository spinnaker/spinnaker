'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.oraclebmcs.truncateOcid.filter', [])
  .filter('truncateOcid', function() {
    return function (ocid) {
      if (ocid) {
        return '...' + ocid.substr(ocid.length - 6);
      }
      return '';
    };
  });
