import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { ApplicationReader } from '../../../../application/service/ApplicationReader';
import { ReactSelectInput } from '../../../../presentation';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { PipelineStageConfig } from './PipelineStageConfig';
import type { IPipeline, IStage } from '../../../../domain';

describe('PipelineStageConfig', () => {
  const flush = async () => {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await Promise.resolve();
  };

  beforeEach(() => {
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([{ name: 'app' }]) as any);
  });

  it('uses a searchable virtualized application selector for static application values', async () => {
    (ApplicationReader.listApplications as jasmine.Spy).and.returnValue(
      Promise.resolve([{ name: 'app' }, { name: 'zzz-app' }]) as any,
    );
    const parentPipeline = { id: 'parent-pipeline', parameterConfig: [], stages: [] } as IPipeline;
    const stage = { application: 'app' } as IStage;
    const updateStageField = jasmine.createSpy('updateStageField');
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([]) as any);

    const wrapper = mount(
      <PipelineStageConfig
        application={{ name: 'app' } as any}
        pipeline={parentPipeline}
        stage={stage}
        updateStageField={updateStageField}
      />,
    );

    await act(async () => {
      await flush();
    });
    wrapper.update();

    const applicationSelect = wrapper.find(ReactSelectInput).filterWhere((node) => node.prop('name') === 'application');
    expect(applicationSelect.exists()).toBe(true);
    expect(applicationSelect.prop('mode')).toBe('VIRTUALIZED');
    expect(applicationSelect.prop('stringOptions')).toEqual(['app', 'zzz-app']);

    await act(async () => {
      applicationSelect.prop('onChange')({ target: { value: 'zzz-app' } } as any);
      await flush();
    });

    expect(updateStageField).toHaveBeenCalledWith({ application: 'zzz-app' });

    wrapper.unmount();
  });

  it('keeps option parameter SpeL values editable', async () => {
    const parentPipeline = { id: 'parent-pipeline', parameterConfig: [], stages: [] } as IPipeline;
    const childPipeline = {
      id: 'child-pipeline',
      name: 'Child Pipeline',
      parameterConfig: [
        {
          default: null,
          hasOptions: true,
          name: 'choice',
          options: [{ value: 'one' }, { value: 'two' }],
        },
      ],
    } as IPipeline;
    const stage = {
      application: 'app',
      pipeline: 'child-pipeline',
      pipelineParameters: { choice: '${ trigger.properties.choice }' },
    } as IStage;
    const updateStageField = jasmine.createSpy('updateStageField');
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([childPipeline]) as any);

    const wrapper = mount(
      <PipelineStageConfig
        application={{ name: 'app' } as any}
        pipeline={parentPipeline}
        stage={stage}
        updateStageField={updateStageField}
      />,
    );

    await act(async () => {
      await flush();
    });
    wrapper.update();

    const parameterInput = wrapper.find('.well input.form-control').filterWhere((node) => !node.prop('disabled'));
    expect(parameterInput.exists()).toBe(true);
    expect(parameterInput.prop('value')).toBe('${ trigger.properties.choice }');

    await act(async () => {
      parameterInput.prop('onChange')({ target: { value: '${ parameters.choice }' } } as any);
      await flush();
    });

    expect(updateStageField).toHaveBeenCalledWith({ pipelineParameters: { choice: '${ parameters.choice }' } });

    wrapper.unmount();
  });

  it('reloads the parameter values when switching between stages that run the same pipeline', async () => {
    const parentPipeline = { id: 'parent-pipeline', parameterConfig: [], stages: [] } as IPipeline;
    const childPipeline = {
      id: 'child-pipeline',
      name: 'Child Pipeline',
      parameterConfig: [{ default: 'dev', name: 'env' }],
    } as IPipeline;
    const stageA = {
      application: 'app',
      failPipeline: false,
      pipeline: 'child-pipeline',
      pipelineParameters: { env: 'stage-a' },
      refId: '1',
      waitForCompletion: false,
    } as IStage;
    const stageB = { ...stageA, pipelineParameters: { env: 'stage-b' }, refId: '2' } as IStage;
    const updateStageField = jasmine.createSpy('updateStageField');
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([childPipeline]) as any);

    const wrapper = mount(
      <PipelineStageConfig
        application={{ name: 'app' } as any}
        pipeline={parentPipeline}
        stage={stageA}
        updateStageField={updateStageField}
      />,
    );

    await act(async () => {
      await flush();
    });
    wrapper.update();

    const parameterInput = () => wrapper.find('.well input.form-control').filterWhere((node) => !node.prop('disabled'));
    expect(parameterInput().prop('value')).toBe('stage-a');

    // The stage changes, but the application and the invoked pipeline stay the same, so the child
    // pipeline list is not refetched and the parameter form must still reload its values.
    await act(async () => {
      wrapper.setProps({ stage: stageB });
      await flush();
    });
    wrapper.update();

    expect(parameterInput().prop('value')).toBe('stage-b');

    await act(async () => {
      parameterInput().prop('onChange')({ target: { value: 'stage-b-edited' } } as any);
      await flush();
    });

    expect(updateStageField).toHaveBeenCalledWith({ pipelineParameters: { env: 'stage-b-edited' } });

    wrapper.unmount();
  });
});
