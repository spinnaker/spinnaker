export interface IKayentaAccount {
  name: string;
  type: string;
  supportedTypes: KayentaAccountType[];
}

export enum KayentaAccountType {
  MetricsStore = 'METRICS_STORE',
  ObjectStore = 'OBJECT_STORE',
  ConfigurationStore = 'CONFIGURATION_STORE',
}
