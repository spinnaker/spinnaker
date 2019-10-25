import { IFunction } from '@spinnaker/core';

export interface IAmazonFunction extends IFunction {
  credentials?: string;
  role?: string;
  runtime: string;
  s3bucket: string;
  s3key: string;
  handler: string;
  functionName: string;
  publish: boolean;
  description: string;
  tags: [{}];
  memorySize: number;
  timeout: number;
  envVariables: {};
  tracingConfig: {
    mode: string;
  };
  deadLetterConfig: {
    targetArn: string;
  };
  KMSKeyArn: string;
  vpcConfig: {
    securityGroupIds: [];
    subnetIds: [];
    vpcId: string;
  };
}
