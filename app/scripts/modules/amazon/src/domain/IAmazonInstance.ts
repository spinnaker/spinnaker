import { IInstance } from '@spinnaker/core';
import { IAmazonSecurityGroup } from './IAmazonSecurityGroup';

interface IInstancePlacement {
  containerIp: string;
  host: string;
  region: string;
  zone: string;
}

export interface IAmazonInstance extends IInstance {
  imageId?: string;
  instancePort?: string;
  instanceType?: string;
  capacityType?: string;
  permanentIps?: string[];
  placement: IInstancePlacement;
  privateDnsName?: string;
  privateIpAddress?: string;
  publicDnsName?: string;
  publicIpAddress?: string;
  securityGroups?: IAmazonSecurityGroup[];
  subnetId?: string;
  targetGroups?: string[];
}
