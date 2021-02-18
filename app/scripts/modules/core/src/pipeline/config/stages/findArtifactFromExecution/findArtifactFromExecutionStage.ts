import { module } from 'angular';
import { ExecutionArtifactTab } from 'core/artifact/react/ExecutionArtifactTab';
import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { FindArtifactFromExecutionExecutionDetails } from '../findArtifactFromExecution/FindArtifactFromExecutionExecutionDetails';
import { FindArtifactFromExecutionCtrl } from '../findArtifactFromExecution/findArtifactFromExecution.controller';

export const FIND_ARTIFACT_FROM_EXECUTION_STAGE = 'spinnaker.core.pipeline.stage.findArtifactStage';

module(FIND_ARTIFACT_FROM_EXECUTION_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Find Artifacts From Execution',
      description: 'Find and bind artifacts from another execution',
      key: 'findArtifactFromExecution',
      templateUrl: require('./findArtifactFromExecutionConfig.html'),
      controller: 'findArtifactFromExecutionCtrl',
      controllerAs: 'ctrl',
      executionDetailsSections: [
        FindArtifactFromExecutionExecutionDetails,
        ExecutionDetailsTasks,
        ExecutionArtifactTab,
      ],
      validators: [
        { type: 'requiredField', fieldName: 'pipeline', fieldLabel: 'Pipeline' },
        { type: 'requiredField', fieldName: 'application', fieldLabel: 'Application' },
      ],
      producesArtifacts: true,
    });
  })
  .controller('findArtifactFromExecutionCtrl', FindArtifactFromExecutionCtrl);
