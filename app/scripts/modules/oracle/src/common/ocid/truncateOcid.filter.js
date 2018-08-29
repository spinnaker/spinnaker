'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.oracle.truncateOcid.filter', []).filter('truncateOcid', function() {
  return function(ocid) {
    if (ocid) {
      return '...' + ocid.substr(ocid.length - 6);
    }
    return '';
  };
});
