import React from 'react';
import ReactDOM from 'react-dom';
import { act } from 'react-dom/test-utils';

import type { ICreatePipelineCommand, ICreatePipelineModalProps } from './CreatePipelineModal';
import { CreatePipelineModal } from './CreatePipelineModal';
import type { Application } from '../../application/application.model';
import { PipelineConfigService } from '../config/services/PipelineConfigService';
import type { IPipeline } from '../../domain/IPipeline';
import { diagnosticLogger } from '../../utils/diagnosticLogger';

const DEFAULT_CONFIG: Partial<IPipeline> = {
  name: 'None',
  stages: [],
  triggers: [],
  application: 'app',
  limitConcurrent: true,
  keepWaitingPipelines: false,
  spelEvaluator: 'v4',
};

interface IDeferred<T> {
  promise: Promise<T>;
  reject: (reason?: any) => void;
  resolve: (value: T | PromiseLike<T>) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T | PromiseLike<T>) => void;
  let reject: (reason?: any) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, reject: reject!, resolve: resolve! };
}

function createApplication(
  pipelines: Array<Partial<IPipeline>> = [],
  strategies: Array<Partial<IPipeline>> = [],
): Application {
  const pipelineConfigs = {
    data: pipelines,
    refresh: jasmine.createSpy('refresh').and.returnValue(Promise.resolve()),
  };
  const strategyConfigs = { data: strategies };
  const dataSources = { pipelineConfigs, strategyConfigs };
  return ({
    name: 'app',
    pipelineConfigs,
    strategyConfigs,
    getDataSource: (key: keyof typeof dataSources) => dataSources[key],
  } as unknown) as Application;
}

async function resolveBoundary(
  boundary: IDeferred<void>,
  completion: Promise<unknown> = boundary.promise,
): Promise<void> {
  await act(async () => {
    boundary.resolve();
    await completion;
  });
}

async function rejectBoundary(boundary: IDeferred<void>, reason: unknown): Promise<void> {
  await act(async () => {
    boundary.reject(reason);
    await boundary.promise.catch(() => undefined);
  });
}

describe('CreatePipelineModal', () => {
  let container: HTMLDivElement;
  let component: CreatePipelineModal;
  let pipelineSavedCallback: jasmine.Spy;

  function renderModal(application: Application): void {
    const props: ICreatePipelineModalProps = {
      application,
      pipelineSavedCallback,
      show: false,
      showCallback: jasmine.createSpy('showCallback'),
    };
    act(() => {
      ReactDOM.render(
        <CreatePipelineModal
          {...props}
          ref={(instance) => {
            component = instance;
          }}
        />,
        container,
      );
    });
  }

  function updateCommand(update: Partial<ICreatePipelineCommand>): void {
    act(() => component.setState({ command: { ...component.state.command, ...update } }));
  }

  function prepareSuccessfulSave(application: Application, savedPipeline: Partial<IPipeline>) {
    const save = deferred<void>();
    const refresh = deferred<void>();
    const refreshCompletion = refresh.promise.then(() => {
      application.pipelineConfigs.data = [...application.pipelineConfigs.data, savedPipeline];
    });
    const savePipeline = spyOn(PipelineConfigService, 'savePipeline').and.returnValue(save.promise);
    application.pipelineConfigs.refresh.and.returnValue(refreshCompletion);
    return { refresh, refreshCompletion, save, savePipeline };
  }

  beforeEach(() => {
    container = document.createElement('div');
    pipelineSavedCallback = jasmine.createSpy('pipelineSavedCallback');
    spyOn(diagnosticLogger, 'warn');
  });

  afterEach(() => {
    act(() => {
      ReactDOM.unmountComponentAtNode(container);
    });
  });

  it('initializes pipeline and strategy names with the application default config', () => {
    const existingPipeline = { name: 'Existing pipeline' };
    const existingStrategy = { name: 'Existing strategy' };

    renderModal(createApplication([existingPipeline], [existingStrategy]));

    expect(component.state.configs).toEqual([DEFAULT_CONFIG, existingPipeline]);
    expect(component.state.existingNames).toEqual(['None', 'Existing pipeline', 'Existing strategy']);
    expect(component.state.command).toEqual({ strategy: false, name: '', config: DEFAULT_CONFIG, template: null });
  });

  it('submits an immutable clone with a trimmed name, current index, and no copied id', async () => {
    const sourceConfig: Partial<IPipeline> = {
      id: 'source-id',
      name: 'Source pipeline',
      application: 'source-app',
      stages: [{ refId: '1', type: 'wait', name: 'Wait' }],
      triggers: [{ type: 'manual', enabled: true }],
      limitConcurrent: false,
      keepWaitingPipelines: true,
      spelEvaluator: 'v4',
    };
    const sourceSnapshot = JSON.parse(JSON.stringify(sourceConfig));
    const application = createApplication([sourceConfig, { name: 'Another pipeline' }]);
    const boundaries = prepareSuccessfulSave(application, { id: 'copied-id', name: 'Copied pipeline' });
    renderModal(application);
    updateCommand({ name: '  Copied pipeline  ', config: sourceConfig });

    act(() => component.submit());

    const expectedPayload = { ...sourceConfig, name: 'Copied pipeline', index: 2 };
    delete expectedPayload.id;
    expect(boundaries.savePipeline).toHaveBeenCalledOnceWith(expectedPayload as IPipeline);
    const submitted = boundaries.savePipeline.calls.mostRecent().args[0];
    expect(submitted).not.toBe(sourceConfig as IPipeline);
    expect(submitted.stages).not.toBe(sourceConfig.stages);
    expect(sourceConfig).toEqual(sourceSnapshot);

    await resolveBoundary(boundaries.save);
    await resolveBoundary(boundaries.refresh, boundaries.refreshCompletion);
  });

  it('refreshes, marks the saved pipeline as new, resets state, and reports its id', async () => {
    const existingPipeline = { id: 'existing-id', name: 'Existing pipeline' };
    const savedPipeline = { id: 'saved-id', name: 'New pipeline', isNew: false };
    const application = createApplication([existingPipeline]);
    const boundaries = prepareSuccessfulSave(application, savedPipeline);
    renderModal(application);
    updateCommand({ name: '  New pipeline  ' });

    act(() => component.submit());
    expect(boundaries.savePipeline).toHaveBeenCalledOnceWith({
      ...DEFAULT_CONFIG,
      name: 'New pipeline',
      index: 1,
    } as IPipeline);
    await resolveBoundary(boundaries.save);
    expect(application.pipelineConfigs.refresh).toHaveBeenCalledWith(true);
    await resolveBoundary(boundaries.refresh, boundaries.refreshCompletion);

    expect(savedPipeline.isNew).toBe(true);
    expect(component.state.submitting).toBe(false);
    expect(component.state.saveError).toBe(false);
    expect(component.state.command.name).toBe('');
    expect(component.state.command.config.name).toBe('None');
    expect(pipelineSavedCallback).toHaveBeenCalledOnceWith('saved-id');
  });

  [
    {
      description: 'exposes a backend save error',
      response: { data: { message: 'Backend rejected the pipeline' } },
      message: 'Backend rejected the pipeline',
    },
    {
      description: 'uses the default save error when the backend omits a message',
      response: {},
      message: 'No message provided',
    },
  ].forEach(({ description, response, message }) => {
    it(`${description}, clears submitting, and does not report a saved pipeline`, async () => {
      const save = deferred<void>();
      spyOn(PipelineConfigService, 'savePipeline').and.returnValue(save.promise);
      renderModal(createApplication());
      updateCommand({ name: 'Rejected pipeline' });

      act(() => component.submit());
      expect(component.state.submitting).toBe(true);
      await rejectBoundary(save, response);

      expect(component.state.submitting).toBe(false);
      expect(component.state.saveError).toBe(true);
      expect(component.state.saveErrorMessage).toBe(message);
      expect(pipelineSavedCallback).not.toHaveBeenCalled();
    });
  });
});
