import { module } from 'angular';

import { ScriptExecutionDetails } from './ScriptExecutionDetails';
import { ScriptStageConfig, validate } from './ScriptStageConfig';
import { AuthenticationService } from '../../../../authentication';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

export const SCRIPT_STAGE = 'spinnaker.core.pipeline.stage.scriptStage';
module(SCRIPT_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    label: 'Script',
    description: 'Runs a script',
    supportsCustomTimeout: true,
    key: 'script',
    restartable: true,
    defaults: {
      waitForCompletion: true,
      failPipeline: true,
      get user() {
        return AuthenticationService.getAuthenticatedUser().name;
      },
    },
    component: ScriptStageConfig,
    executionDetailsSections: [ScriptExecutionDetails, ExecutionDetailsTasks],
    strategy: true,
    validateFn: validate,
  });
});
