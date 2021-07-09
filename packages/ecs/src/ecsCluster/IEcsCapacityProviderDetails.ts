export interface IEcsCapacityProviderDetails {
  capacityProviders: string[];
  clusterName: string;
  defaultCapacityProviderStrategy: IEcsDefaultCapacityProviderStrategyItem[];
}

export interface IEcsDefaultCapacityProviderStrategyItem {
  base: number;
  capacityProvider: string;
  weight: number;
}
