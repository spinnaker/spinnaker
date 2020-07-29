import { IServerGroupManager } from '@spinnaker/core';

export interface IKubernetesServerGroupManager extends IServerGroupManager {
  kind: string;
  displayName: string;
  apiVersion: string;
  namespace: string;
}
