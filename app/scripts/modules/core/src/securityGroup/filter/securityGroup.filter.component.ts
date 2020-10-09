import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SecurityGroupFilters } from './SecurityGroupFilters';

export const SECURITY_GROUP_FILTER = 'securityGroup.filter.controller';
module(SECURITY_GROUP_FILTER, []).component(
  'securityGroupFilter',
  react2angular(withErrorBoundary(SecurityGroupFilters, 'securityGroupFilter'), ['app']),
);
