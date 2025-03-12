'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE } from './customInstanceBuilder.gce.service';

export const GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER = 'spinnaker.gce.customInstance.filter';
export const name = GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER; // for backwards compatibility
module(GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER, [GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE]).filter(
  'customInstanceFilter',
  [
    'gceCustomInstanceBuilderService',
    function (gceCustomInstanceBuilderService) {
      return function (instanceTypeString) {
        if (_.includes(instanceTypeString, 'custom-')) {
          const { instanceFamily, vCpuCount, memory } = gceCustomInstanceBuilderService.parseInstanceTypeString(
            instanceTypeString,
          );
          return `${instanceFamily} ${vCpuCount} vCPU / ${memory} GB RAM`;
        }
        return instanceTypeString;
      };
    },
  ],
);
