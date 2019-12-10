'use strict';

import _ from 'lodash';
import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE } from './customInstanceBuilder.gce.service';

import { module } from 'angular';

export const GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER = 'spinnaker.gce.customInstance.filter';
export const name = GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER; // for backwards compatibility
module(GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER, [GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE]).filter(
  'customInstanceFilter',
  [
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
  ],
);
