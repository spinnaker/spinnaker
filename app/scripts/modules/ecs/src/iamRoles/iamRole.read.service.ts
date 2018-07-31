import { IPromise, module } from 'angular';

import { API } from '@spinnaker/core';
import { IRoleDescriptor } from './IRole';

export class IamRoleReader {
  public listRoles(provider: string): IPromise<IRoleDescriptor[]> {
    return API.all('roles')
      .all(provider)
      .getList();
  }
}

export const IAM_ROLE_READ_SERVICE = 'spinnaker.ecs.iamRole.read.service';

module(IAM_ROLE_READ_SERVICE, []).service('iamRoleReader', IamRoleReader);
