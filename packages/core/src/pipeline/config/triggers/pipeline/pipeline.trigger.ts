'use strict';

import { PipelineTrigger } from './PipelineTrigger';
import { PipelineTriggerTemplate } from './PipelineTriggerTemplate';
import { ArtifactTypePatterns } from '../../../../artifact';
import { Registry } from '../../../../registry';
import { ExecutionUserStatus } from '../../../status/ExecutionUserStatus';

Registry.pipeline.registerTrigger({
  component: PipelineTrigger,
  description: 'Listens to a pipeline execution',
  label: 'Pipeline',
  key: 'pipeline',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  executionStatusComponent: ExecutionUserStatus,
  manualExecutionComponent: PipelineTriggerTemplate,
  executionTriggerLabel: () => 'Pipeline',
});
