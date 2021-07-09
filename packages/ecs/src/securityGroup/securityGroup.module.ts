import { module } from 'angular';

import { ECS_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER } from './details/securityGroupDetail.controller';
import { ECS_SECURITY_GROUP_READER } from './securityGroup.reader';
import { ECS_SECURITYGROUP_SECURITYGROUP_TRANSFORMER } from './securityGroup.transformer';

export const ECS_SECURITY_GROUP_MODULE = 'spinnaker.ecs.securityGroup';
module(ECS_SECURITY_GROUP_MODULE, [
  ECS_SECURITY_GROUP_READER,
  ECS_SECURITYGROUP_SECURITYGROUP_TRANSFORMER,
  ECS_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER,
]);
