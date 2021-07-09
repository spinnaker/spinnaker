import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { TitusCustomScalingPolicy } from './TitusCustomScalingPolicy';

export const TITUS_SERVERGROUP_CUSTOM_SCALING_COMPONENT =
  'spinnaker.application.titus.serverGroup.customScaling.component';

module(TITUS_SERVERGROUP_CUSTOM_SCALING_COMPONENT, []).component(
  'titusCustomScalingPolicy',
  react2angular(withErrorBoundary(TitusCustomScalingPolicy, 'titusCustomScalingPolicy'), [
    'application',
    'serverGroup',
  ]),
);
