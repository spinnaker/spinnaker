import { REST } from '@spinnaker/core';
import type { IRoleDescriptor } from './IRole';

export class IamRoleReader {
  public listRoles(provider: string): PromiseLike<IRoleDescriptor[]> {
    return REST('/roles').path(provider).get();
  }
}
