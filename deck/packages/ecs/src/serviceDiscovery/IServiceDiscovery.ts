export interface IServiceDiscoveryRegistryDescriptor {
  account: string;
  region: string;
  name: string;
  id: string;
  arn: string;
  displayName: string;
}

export interface IServiceDiscoveryRegistryAssociation {
  registry: IServiceDiscoveryRegistryDescriptor;
  containerPort: string;
}
