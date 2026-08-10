import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';
import { AppengineStopServerGroupExecutionDetails } from '../AppengineExecutionDetails';
import { AppengineServerGroupStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  label: 'Stop Server Group',
  description: 'Stops a server group.',
  key: 'stopAppEngineServerGroup',
  component: AppengineServerGroupStageConfig,
  executionDetailsSections: [AppengineStopServerGroupExecutionDetails, ExecutionDetailsTasks],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  cloudProvider: 'appengine',
});
