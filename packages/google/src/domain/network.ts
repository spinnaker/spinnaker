export interface IGceNetwork {
  account: string;
  autoCreateSubnets: boolean;
  cloudProvider: string;
  id: string;
  name: string;
  region: string;
  selfLink: string;
  subnets: string[];
}
