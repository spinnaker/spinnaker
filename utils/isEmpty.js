'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils.isEmpty', [])
  .factory('isEmpty', function () {
    // Essentially the same as lodash's isEmpty method, except returns false for 0
    return function (value) {
      if (angular.isArray(value)) {
        return value.length === 0;
      }
      return angular.isUndefined(value) || value === '' || value === null || value !== value;
    };
  }
)
.name;
