export interface IKayentaAccount {
  name: string;
  type: string;
  supportedTypes: KayentaAccountType[];
  metricsStoreType?: string;
}

export enum KayentaAccountType {
  MetricsStore = 'METRICS_STORE',
  ObjectStore = 'OBJECT_STORE',
  ConfigurationStore = 'CONFIGURATION_STORE',
}
