import { AuthenticationService } from '../../../../authentication';
import { SETTINGS } from '../../../../config';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';
import { ExecutionDetailsTasks } from '../common';

import { ScriptExecutionDetails } from './ScriptExecutionDetails';
import { ScriptStageConfig, validate } from './ScriptStageConfig';
import { registerScriptStage } from './scriptStage';

describe('Script stage runtime registration', () => {
  const originalHiddenStages = SETTINGS.hiddenStages;
  let originalPipeline: typeof Registry.pipeline;
  let originalUrlBuilder: typeof Registry.urlBuilder;

  beforeEach(() => {
    originalPipeline = Registry.pipeline;
    originalUrlBuilder = Registry.urlBuilder;
    Registry.reinitialize();
    AuthenticationService.reset();
    SETTINGS.hiddenStages = [];
  });

  afterEach(() => {
    Registry.pipeline = originalPipeline;
    Registry.urlBuilder = originalUrlBuilder;
    AuthenticationService.reset();
    SETTINGS.hiddenStages = originalHiddenStages;
  });

  it('registers the complete Script stage exactly once', () => {
    const registerStage = spyOn(Registry.pipeline, 'registerStage').and.callThrough();

    registerScriptStage();
    registerScriptStage();

    expect(Registry.pipeline.getStageTypes().filter(({ key }) => key === 'script').length).toBe(1);
    expect(registerStage).toHaveBeenCalledTimes(1);

    const scriptStage = registerStage.calls.mostRecent().args[0] as IStageTypeConfig;
    expect(scriptStage).toEqual(
      jasmine.objectContaining({
        label: 'Script',
        description: 'Runs a script',
        supportsCustomTimeout: true,
        key: 'script',
        restartable: true,
        component: ScriptStageConfig,
        executionDetailsSections: [ScriptExecutionDetails, ExecutionDetailsTasks],
        strategy: true,
        validateFn: validate,
      }),
    );
    expect(scriptStage.defaults).toEqual(
      jasmine.objectContaining({
        waitForCompletion: true,
        failPipeline: true,
      }),
    );
  });

  it('reads the current authenticated user from the Script defaults getter', () => {
    registerScriptStage();
    AuthenticationService.setAuthenticatedUser({ name: 'test-user', authenticated: true });

    expect(Registry.pipeline.getStageConfig({ type: 'script' } as any).defaults.user).toBe('test-user');
  });
});
