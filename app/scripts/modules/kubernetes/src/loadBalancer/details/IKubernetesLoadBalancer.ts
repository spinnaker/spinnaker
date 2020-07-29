import { ILoadBalancer } from '@spinnaker/core';

export interface IKubernetesLoadBalancer extends ILoadBalancer {
  kind: string;
  displayName: string;
  apiVersion: string;
  namespace: string;
}
