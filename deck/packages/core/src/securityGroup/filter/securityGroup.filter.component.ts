import { module } from 'angular';

import { SecurityGroupFilters } from './SecurityGroupFilters';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const SECURITY_GROUP_FILTER = 'securityGroup.filter.controller';
module(SECURITY_GROUP_FILTER, []).component(
  'securityGroupFilter',
  angularComponentFromReact(SecurityGroupFilters, 'securityGroupFilter', ['app']),
);
