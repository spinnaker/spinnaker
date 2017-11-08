import { IInstance } from '@spinnaker/core';

export interface IKubernetesInstance extends IInstance {
  kind: string;
  name: string;
  displayName: string;
  apiVersion: string;
  manifest: any;
  namespace: string;
}
