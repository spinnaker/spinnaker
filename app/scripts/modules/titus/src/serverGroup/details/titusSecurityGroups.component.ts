import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TitusSecurityGroupsDetailsSection } from './TitusSecurityGroups';

export const TITUS_SECURITY_GROUPS_DETAILS = 'spinnaker.titus.serverGroup.details.securityGroups.component';
module(TITUS_SECURITY_GROUPS_DETAILS, []).component(
  'titusSecurityGroups',
  react2angular(TitusSecurityGroupsDetailsSection, ['app', 'serverGroup']),
);
