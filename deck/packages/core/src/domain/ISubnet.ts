export interface ISubnet {
  availabilityZone: string;
  id: string;
  name: string;
  account: string;
  region: string;
  type: string;
  label: string;
  purpose: string;
  deprecated: boolean;
  target?: string;
  vpcId?: string;
}
