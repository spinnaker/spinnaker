import { NoConfigurationStageConfig, Registry } from '@spinnaker/core';

import {
  CanaryDeploymentAnalysisHistory,
  CanaryDeploymentCodeChanges,
  CanaryDeploymentExecutionDetails,
} from './CanaryDeploymentExecutionDetails';

Registry.pipeline.registerStage({
  synthetic: true,
  key: 'canaryDeployment',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [
    CanaryDeploymentExecutionDetails,
    CanaryDeploymentAnalysisHistory,
    CanaryDeploymentCodeChanges,
  ],
});
