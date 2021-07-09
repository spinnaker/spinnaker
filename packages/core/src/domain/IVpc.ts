export interface IVpc {
  account: string;
  id: string;
  name: string;
  region: string;
  cloudProvider: string;
  label?: string;
  deprecated?: boolean;
}
