import { Registry } from '@spinnaker/core';

export function registerEcsResizeServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'resizeServerGroup',
    alias: 'resizeAsg',
    cloudProvider: 'ecs',
    templateUrl: require('./resizeAsgStage.html'),
    executionConfigSections: ['resizeServerGroupConfig', 'taskStatus'],
    executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'action' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
