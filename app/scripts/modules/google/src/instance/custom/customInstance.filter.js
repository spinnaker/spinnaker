'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.gce.customInstance.filter', [require('./customInstanceBuilder.gce.service').name])
  .filter('customInstanceFilter', function(gceCustomInstanceBuilderService) {
    return function(instanceTypeString) {
      if (_.startsWith(instanceTypeString, 'custom')) {
        const { vCpuCount, memory } = gceCustomInstanceBuilderService.parseInstanceTypeString(instanceTypeString);
        return `${vCpuCount} vCPU / ${memory} GB RAM`;
      }
      return instanceTypeString;
    };
  });
