import { FunctionReader } from '@spinnaker/core';

import { AwsFunctionTransformer } from './function';
import { AwsInstanceTypeService } from './instance/awsInstanceType.service';
import { AwsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

let awsInstanceTypeService: AwsInstanceTypeService;
let awsServerGroupTransformer: AwsServerGroupTransformer;
let functionReader: FunctionReader;

export const AwsServices = {
  get awsInstanceTypeService() {
    return (awsInstanceTypeService = awsInstanceTypeService || new AwsInstanceTypeService());
  },
  get awsServerGroupTransformer() {
    return (awsServerGroupTransformer = awsServerGroupTransformer || new AwsServerGroupTransformer());
  },
  get functionReader() {
    return (functionReader = functionReader || new FunctionReader(new AwsFunctionTransformer()));
  },
};
