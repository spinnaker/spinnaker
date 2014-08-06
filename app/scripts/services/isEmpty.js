'use strict';

var angular = require('angular');

module.exports = function() {
  return function(value) {
    return angular.isUndefined(value) || value === '' || value === null || value !== value;
  };
};
