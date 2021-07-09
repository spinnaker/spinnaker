import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CustomInstanceConfigurer } from './CustomInstanceConfigurer';

export const GCE_CUSTOM_INSTANCE_CONFIGURER = 'spinnaker.gce.customInstanceConfigurer';
module(GCE_CUSTOM_INSTANCE_CONFIGURER, []).component(
  'gceCustomInstanceConfigurer',
  react2angular(withErrorBoundary(CustomInstanceConfigurer, 'gceCustomInstanceConfigurer'), [
    'vCpuList',
    'instanceFamilyList',
    'memoryList',
    'selectedVCpuCount',
    'selectedMemory',
    'selectedInstanceFamily',
    'onChange',
  ]),
);
