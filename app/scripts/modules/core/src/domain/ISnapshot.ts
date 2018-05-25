export interface ISnapshot {
  account: string;
  application: string;
  configLang: string; // Terraform, CloudFormation, Heat, etc.
  id: string;
  infrastructure: any;
  lastModified: number;
  lastModifiedBy: string;
  timestamp: number;
}
