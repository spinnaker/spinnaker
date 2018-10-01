import { module } from 'angular';

import { API } from '@spinnaker/core';
import { ISecretDescriptor } from './ISecret';

export class SecretReader {
  public listSecrets(): ng.IPromise<ISecretDescriptor[]> {
    return API.all('ecs')
      .all('secrets')
      .getList();
  }
}

export const ECS_SECRET_READ_SERVICE = 'spinnaker.ecs.secret.read.service';

module(ECS_SECRET_READ_SERVICE, []).service('secretReader', SecretReader);
