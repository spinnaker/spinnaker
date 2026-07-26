import { REST } from '@spinnaker/core';
import type { IRoleDescriptor } from './IRole';

export class IamRoleReader {
  public listRoles(provider: string): Promise<IRoleDescriptor[]> {
    return REST('/roles').path(provider).get();
  }
}
