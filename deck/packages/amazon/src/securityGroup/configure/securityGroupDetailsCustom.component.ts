import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { SecurityGroupDetailsCustom } from './securityGroupDetailsCustom';

export const AWS_SECURITY_GROUP_DETAILS_CUSTOM = 'spinnaker.amazon.securityGroups.details.custom.component';
module(AWS_SECURITY_GROUP_DETAILS_CUSTOM, []).component(
  'securityGroupDetailsCustom',
  react2angular(withErrorBoundary(SecurityGroupDetailsCustom, 'securityGroupDetailsCustom'), [
    'securityGroupDetails',
    'ctrl',
    'scope',
  ]),
);
