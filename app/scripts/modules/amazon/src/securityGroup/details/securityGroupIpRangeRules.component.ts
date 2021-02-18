import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { IPRangeRules } from './IPRangeRules';

export const AWS_SECURITY_GROUP_IP_RANGE_RULES = 'spinnaker.amazon.securityGroups.details.securityGroups.component';
module(AWS_SECURITY_GROUP_IP_RANGE_RULES, []).component(
  'ipRangeRules',
  react2angular(withErrorBoundary(IPRangeRules, 'ipRangeRules'), ['ipRules']),
);
