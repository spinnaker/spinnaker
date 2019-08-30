import { module } from 'angular';

import { ECS_SECURITY_GROUP_READER } from './securityGroup.reader';

export const ECS_SECURITY_GROUP_MODULE = 'spinnaker.ecs.securityGroup';
module(ECS_SECURITY_GROUP_MODULE, [
  ECS_SECURITY_GROUP_READER,
  require('./securityGroup.transformer').name,
  require('./details/securityGroupDetail.controller').name,
]);
