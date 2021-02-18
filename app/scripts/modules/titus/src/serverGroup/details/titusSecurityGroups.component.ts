import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { TitusSecurityGroupsDetailsSection } from './TitusSecurityGroups';

export const TITUS_SECURITY_GROUPS_DETAILS = 'spinnaker.titus.serverGroup.details.securityGroups.component';
module(TITUS_SECURITY_GROUPS_DETAILS, []).component(
  'titusSecurityGroups',
  react2angular(withErrorBoundary(TitusSecurityGroupsDetailsSection, 'titusSecurityGroups'), ['app', 'serverGroup']),
);
