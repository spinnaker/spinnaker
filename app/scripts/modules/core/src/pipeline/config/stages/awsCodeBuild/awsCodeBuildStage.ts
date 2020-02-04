import { ExecutionDetailsTasks, Registry } from 'core';

import { AwsCodeBuildStageConfig } from './AwsCodeBuildStageConfig';
import { AwsCodeBuildExecutionDetails } from './AwsCodeBuildExecutionDetails';
import { validate } from './AwsCodeBuildValidator';

Registry.pipeline.registerStage({
  label: 'AWS CodeBuild',
  description: 'Trigger a build in AWS CodeBuild',
  key: 'awsCodeBuild',
  producesArtifacts: false,
  component: AwsCodeBuildStageConfig,
  validateFn: validate,
  executionDetailsSections: [AwsCodeBuildExecutionDetails, ExecutionDetailsTasks],
});
