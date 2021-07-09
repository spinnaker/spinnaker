import { isEmpty } from 'lodash';

import { Application } from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';
import { IAmazonFunction, IAmazonFunctionUpsertCommand } from '../domain';

export class AwsFunctionTransformer {
  public normalizeFunction(functionDef: IAmazonFunction): IAmazonFunction {
    const normalizedFunctionDef: IAmazonFunction = functionDef;
    normalizedFunctionDef.credentials = functionDef.account;
    return normalizedFunctionDef;
  }

  public convertFunctionForEditing = (functionDef: IAmazonFunction): IAmazonFunctionUpsertCommand => ({
    ...functionDef,
    envVariables: functionDef.environment ? functionDef.environment.variables : {},
    credentials: functionDef.account,
    tracingConfig: {
      mode: functionDef.tracingConfig ? functionDef.tracingConfig.mode : '',
    },
    deadLetterConfig: {
      targetArn: functionDef.deadLetterConfig ? functionDef.deadLetterConfig.targetArn : '',
    },
    KMSKeyArn: functionDef.kmskeyArn ? functionDef.kmskeyArn : '',
    subnetIds: functionDef.vpcConfig ? functionDef.vpcConfig.subnetIds : [],
    securityGroupIds: functionDef.vpcConfig ? functionDef.vpcConfig.securityGroupIds : [],
    vpcId: functionDef.vpcConfig ? functionDef.vpcConfig.vpcId : '',
    operation: '',
    cloudProvider: functionDef.cloudProvider,
    region: functionDef.region,
    targetGroups: isEmpty(functionDef.targetGroups) ? '' : functionDef.targetGroups,
  });

  public constructNewAwsFunctionTemplate(application: Application): IAmazonFunctionUpsertCommand {
    const defaultCredentials = application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
    const defaultRegion = application.defaultRegions.aws || AWSProviderSettings.defaults.region;

    return {
      role: '',
      runtime: '',
      s3bucket: '',
      s3key: '',
      handler: '',
      functionName: '',
      publish: false,
      tags: {},
      memorySize: 128,
      description: '',

      credentials: defaultCredentials,
      cloudProvider: 'aws',
      detail: '',
      region: defaultRegion,
      envVariables: {},

      tracingConfig: {
        mode: 'PassThrough',
      },
      kmskeyArn: '',
      vpcId: '',
      subnetIds: [],
      securityGroupIds: [],
      timeout: 3,
      deadLetterConfig: {
        targetArn: '',
      },
      operation: '',
      targetGroups: '',
    };
  }
}
