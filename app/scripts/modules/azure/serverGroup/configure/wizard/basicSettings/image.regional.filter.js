'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.basicSettings.image.filter', [])
  .filter('regional', function () {
    return function (input, selectedRegion) {
      return _.filter(input, function (image) {
        return image.region === selectedRegion || image.region === null;
      });
    };
  });
