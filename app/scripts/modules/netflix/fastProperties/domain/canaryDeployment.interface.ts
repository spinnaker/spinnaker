export interface ICanaryDeployment {
  region: string;
  accountName: string;
  baseline: string;
  canary: string;
  type: string;
}
