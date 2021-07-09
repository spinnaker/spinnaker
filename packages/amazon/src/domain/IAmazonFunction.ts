import { IFunction, IFunctionDeleteCommand, IFunctionUpsertCommand } from '@spinnaker/core';

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
  tags: string | { [key: string]: string };
  memorySize: number;
  timeout: number;
  envVariables: {};
  environment: {
    variables: {};
  };
  tracingConfig: {
    mode: string;
  };
  deadLetterConfig: {
    targetArn: string;
  };
  kmskeyArn: string;
  vpcConfig: {
    securityGroupIds: [];
    subnetIds: [];
    vpcId: string;
  };
  targetGroups: string | string[];
}

export interface IAmazonFunctionUpsertCommand extends IFunctionUpsertCommand {
  role?: string;
  runtime: string;
  s3bucket: string;
  s3key: string;
  handler: string;
  tags: string | { [key: string]: string };
  memorySize: number;
  timeout: number;
  envVariables: {};
  publish: boolean;
  tracingConfig: {
    mode: string;
  };
  deadLetterConfig: {
    targetArn: string;
  };
  kmskeyArn: string;
  securityGroupIds: string[];
  subnetIds: string[];
  vpcId: string;
  targetGroups: string | string[];
}

export interface IAmazonFunctionDeleteCommand extends IFunctionDeleteCommand {
  cloudProvider: string;
  functionName: string;
  region: string;
  credentials: string;
}
