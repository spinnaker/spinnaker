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
      expect.objectContaining({
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
    expect((stage as any).executionDetailsSections).toBeDefined();
  });

  it('registers only non-custom preconfigured jobs', async () => {
    vi.spyOn(PreconfiguredJobReader, 'list').mockReturnValue(
      Promise.resolve([
        { type: 'basicJob', uiType: 'BASIC', label: 'Basic', producesArtifacts: false },
        { type: 'customJob', uiType: 'CUSTOM', label: 'Custom', producesArtifacts: false },
      ]),
    );
    const registerSpy = vi
      .spyOn(Registry.pipeline, 'registerPreconfiguredJobStage')
      .mockReturnValue(Promise.resolve(undefined));

    await registerPreconfiguredJobStages();

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy.mock.lastCall[0]).toEqual(expect.objectContaining({ key: 'basicJob' }));
  });
});
