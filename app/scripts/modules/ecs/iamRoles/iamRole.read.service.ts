import { IPromise, module } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';
import { IRoleDescriptor } from './IRole';

export class IamRoleReader {
  public constructor(private API: Api) {
    'ngInject';
  }

  public listRoles(provider: string): IPromise<IRoleDescriptor[]> {
    return this.API.all('roles')
      .all(provider)
      .getList();
  }
}

export const IAM_ROLE_READ_SERVICE = 'spinnaker.ecs.iamRole.read.service';

module(IAM_ROLE_READ_SERVICE, [API_SERVICE]).service('iamRoleReader', IamRoleReader);
