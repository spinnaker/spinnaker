import { ExecutionDetailsTasks, Registry, SETTINGS } from '@spinnaker/core';

import { AcaTaskConfigDetails, AcaTaskExecutionDetails, AcaTaskHistoryDetails } from './AcaTaskExecutionDetails';
import { AcaTaskStageConfig } from './AcaTaskStageConfig';
import { acaTaskTransformer } from './acaTaskStage.transformer';
import { CanaryExecutionLabel } from '../canary/CanaryExecutionLabel';
import { CanaryExecutionSummary } from '../canary/CanaryExecutionSummary';

Registry.pipeline.registerTransformer(acaTaskTransformer);

if (SETTINGS.feature.canary) {
  Registry.pipeline.registerStage({
    label: 'ACA Task',
    description: 'Runs a canary task against an existing cluster, asg, or query',
    key: 'acaTask',
    restartable: true,
    component: AcaTaskStageConfig,
    executionDetailsSections: [
      AcaTaskExecutionDetails,
      AcaTaskConfigDetails,
      AcaTaskHistoryDetails,
      ExecutionDetailsTasks,
    ],
    executionSummaryComponent: CanaryExecutionSummary,
    stageFilter: (stage) => ['monitorAcaTask', 'acaTask'].includes(stage.type),
    executionLabelComponent: CanaryExecutionLabel,
    validators: [],
  });
}
