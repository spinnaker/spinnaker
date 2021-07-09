import { IFunctionSourceData } from '@spinnaker/core';

export interface IAmazonFunctionSourceData extends IFunctionSourceData {
  account: string;
  cloudProvider: string;
  createdTime: number;
  functionName: string;
  runtime: string;
  region: string;
  publish: boolean;
  description: string;
  eventSourceMappings: string[];
  functionArn: string;
  handler: string;
  layers: string;
  lastModified: number;
  type: string;
  memorySize: number;
  revisionId: string;
  revisions: {};
  role: string;
  timeout: number;
  tracingConfig: {
    mode: string;
  };
  version: string;
  envVariables: {};
  environment: {
    variables: {};
  };
  vpcConfig: {
    subnetIds: [];
    securityGroupIds: [];
    vpcId: '';
  };
  s3bucket: string;
  s3key: string;
  tags: string | { [key: string]: string };
  deadLetterConfig: {
    targetArn: string;
  };
  kmskeyArn: string;
  targetGroups: string[];
}
