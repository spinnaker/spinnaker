import { Registry } from '@spinnaker/core';

import {
  CanaryDeploymentAnalysisHistory,
  CanaryDeploymentCodeChanges,
  CanaryDeploymentExecutionDetails,
} from './CanaryDeploymentExecutionDetails';

Registry.pipeline.registerStage({
  synthetic: true,
  key: 'canaryDeployment',
  executionDetailsSections: [
    CanaryDeploymentExecutionDetails,
    CanaryDeploymentAnalysisHistory,
    CanaryDeploymentCodeChanges,
  ],
});
