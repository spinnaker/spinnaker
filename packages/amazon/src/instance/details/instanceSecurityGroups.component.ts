import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { InstanceSecurityGroups } from './InstanceSecurityGroups';

export const INSTANCE_SECURITY_GROUPS_COMPONENT = 'spinnaker.application.instanceSecurityGroups.component';

module(INSTANCE_SECURITY_GROUPS_COMPONENT, []).component(
  'instanceSecurityGroups',
  react2angular(withErrorBoundary(InstanceSecurityGroups, 'instanceSecurityGroups'), ['instance']),
);
