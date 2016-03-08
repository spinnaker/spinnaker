'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.basicSettings.image.filter', [
  require('../../../../../core/utils/lodash.js'),
]).filter('regional', function(_) {
  return function(input, selectedRegion) {
    var result = _.filter(input, function(image) {
      return image.region === selectedRegion || image.region === null;
    });
    return result;
  };
});
