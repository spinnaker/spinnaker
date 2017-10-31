import { IServerGroup } from '@spinnaker/core';

export interface IKubernetesServerGroup extends IServerGroup {
  kind: string;
  displayName: string;
  apiVersion: string;
  disabled: boolean;
  manifest: any;
}
