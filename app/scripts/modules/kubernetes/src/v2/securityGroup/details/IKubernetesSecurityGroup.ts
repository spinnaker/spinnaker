import { IMoniker, ISecurityGroupDetail } from '@spinnaker/core';

export interface IKubernetesSecurityGroup extends ISecurityGroupDetail {
  kind: string;
  displayName: string;
  apiVersion: string;
  manifest: any;
  moniker: IMoniker;
  namespace: string;
}
