import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SecurityGroupFilters } from './SecurityGroupFilters';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const SECURITY_GROUP_FILTER = 'securityGroup.filter.controller';
module(SECURITY_GROUP_FILTER, []).component(
  'securityGroupFilter',
  react2angular(withErrorBoundary(SecurityGroupFilters, 'securityGroupFilter'), ['app']),
);
