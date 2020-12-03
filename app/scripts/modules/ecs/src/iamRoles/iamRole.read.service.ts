import { module } from 'angular';

import { API } from '@spinnaker/core';
import { IRoleDescriptor } from './IRole';

export class IamRoleReader {
  public listRoles(provider: string): PromiseLike<IRoleDescriptor[]> {
    return API.path('roles').path(provider).get();
  }
}

export const IAM_ROLE_READ_SERVICE = 'spinnaker.ecs.iamRole.read.service';

module(IAM_ROLE_READ_SERVICE, []).service('iamRoleReader', IamRoleReader);
