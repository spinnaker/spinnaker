import { REST } from '@spinnaker/core';
import type { ISecretDescriptor } from './ISecret';

export class SecretReader {
  public listSecrets(): PromiseLike<ISecretDescriptor[]> {
    return REST('/ecs/secrets').get();
  }
}
