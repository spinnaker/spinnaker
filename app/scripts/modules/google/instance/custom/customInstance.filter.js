'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.customInstance.filter', [
  require('../../../core/utils/lodash.js'),
  require('./customInstanceBuilder.gce.service.js')
])
  .filter('customInstanceFilter', function(_, gceCustomInstanceBuilderService) {
    return function (instanceTypeString) {
      if (_.startsWith(instanceTypeString, 'custom')) {
        let { vCpuCount, memory } = gceCustomInstanceBuilderService.parseInstanceTypeString(instanceTypeString);
        return `${vCpuCount} vCPU / ${memory} GB RAM`;
      }
      return instanceTypeString;
    };
  });
