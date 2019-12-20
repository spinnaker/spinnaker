import { module } from 'angular';
import { react2angular } from 'react2angular';
import { AdditionalIpRules } from './additionalIpRules';

export const AWS_SECURITY_GROUP_ADDITIONAL_IP_RULES =
  'spinnaker.amazon.securityGroups.details.securityGroups.additionalIpRules.component';
module(AWS_SECURITY_GROUP_ADDITIONAL_IP_RULES, []).component(
  'additionalIpRules',
  react2angular(AdditionalIpRules, ['securityGroupDetails']),
);
