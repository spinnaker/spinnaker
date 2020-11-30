import { module } from 'angular';

import { REST } from '@spinnaker/core';
import { ISecretDescriptor } from './ISecret';

export class SecretReader {
  public listSecrets(): PromiseLike<ISecretDescriptor[]> {
    return REST('/ecs/secrets').get();
  }
}

export const ECS_SECRET_READ_SERVICE = 'spinnaker.ecs.secret.read.service';

module(ECS_SECRET_READ_SERVICE, []).service('secretReader', SecretReader);
