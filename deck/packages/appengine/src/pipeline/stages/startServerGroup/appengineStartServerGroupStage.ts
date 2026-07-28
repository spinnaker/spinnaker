import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';
import { AppengineStartServerGroupExecutionDetails } from '../AppengineExecutionDetails';
import { AppengineServerGroupStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  label: 'Start Server Group',
  description: 'Starts a server group.',
  key: 'startAppEngineServerGroup',
  component: AppengineServerGroupStageConfig,
  executionDetailsSections: [AppengineStartServerGroupExecutionDetails, ExecutionDetailsTasks],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  cloudProvider: 'appengine',
});
