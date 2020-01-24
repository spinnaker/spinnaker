import { module } from 'angular';
import { react2angular } from 'react2angular';
import { SecurityGroupDetailsCustom } from './securityGroupDetailsCustom';

export const AWS_SECURITY_GROUP_DETAILS_CUSTOM = 'spinnaker.amazon.securityGroups.details.custom.component';
module(AWS_SECURITY_GROUP_DETAILS_CUSTOM, []).component(
  'securityGroupDetailsCustom',
  react2angular(SecurityGroupDetailsCustom, ['securityGroupDetails', 'ctrl', 'scope']),
);
