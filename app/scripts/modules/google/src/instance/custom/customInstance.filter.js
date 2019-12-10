'use strict';

import _ from 'lodash';

const angular = require('angular');

export const GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER = 'spinnaker.gce.customInstance.filter';
export const name = GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER; // for backwards compatibility
angular
  .module(GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER, [require('./customInstanceBuilder.gce.service').name])
  .filter('customInstanceFilter', [
    'gceCustomInstanceBuilderService',
    function(gceCustomInstanceBuilderService) {
      return function(instanceTypeString) {
        if (_.startsWith(instanceTypeString, 'custom')) {
          const { vCpuCount, memory } = gceCustomInstanceBuilderService.parseInstanceTypeString(instanceTypeString);
          return `${vCpuCount} vCPU / ${memory} GB RAM`;
        }
        return instanceTypeString;
      };
    },
  ]);
