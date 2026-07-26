import { Registry } from '../../../../registry/Registry';
import { ExecutionDetailsTasks } from '../common';
import { PreconfiguredJobExecutionDetails } from './PreconfiguredJobExecutionDetails';
import { PreconfiguredJobStageConfig } from './PreconfiguredJobStageConfig';
import { PreconfiguredJobReader } from './preconfiguredJob.reader';
import { makePreconfiguredJobStage, registerPreconfiguredJobStages } from './preconfiguredJobStage';

describe('Preconfigured Job stage registration', () => {
  it('builds the preconfigured job stage skeleton', () => {
    const stage = makePreconfiguredJobStage('myJob');

    expect(stage).toEqual(
      jasmine.objectContaining({
        label: '',
        description: '',
        key: 'myJob',
        alias: 'preconfiguredJob',
        addAliasToConfig: true,
        restartable: true,
        defaults: { parameters: {} },
        component: PreconfiguredJobStageConfig,
        executionDetailsSections: [PreconfiguredJobExecutionDetails, ExecutionDetailsTasks],
        configuration: {
          waitForCompletion: true,
          parameters: [],
        },
        producesArtifacts: false,
      }),
    );
    expect((stage as any).templateUrl).toBeUndefined();
    expect((stage as any).executionDetailsSections).toBeDefined();
  });

  it('registers only non-custom preconfigured jobs', async () => {
    spyOn(PreconfiguredJobReader, 'list').and.returnValue(
      Promise.resolve([
        { type: 'basicJob', uiType: 'BASIC', label: 'Basic', producesArtifacts: false },
        { type: 'customJob', uiType: 'CUSTOM', label: 'Custom', producesArtifacts: false },
      ]),
    );
    const registerSpy = spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').and.returnValue(
      Promise.resolve(undefined),
    );

    await registerPreconfiguredJobStages();

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ key: 'basicJob' }));
  });
});
