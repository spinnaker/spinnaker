import { PipelineTemplates, Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'disableServerGroup',
  alias: 'disableAsg',
  cloudProvider: 'ecs',
  templateUrl: require('./disableAsgStage.html'),
  executionDetailsUrl: PipelineTemplates.disableAsgExecutionDetails,
  executionStepLabelUrl: require('./disableAsgStepLabel.html'),
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
