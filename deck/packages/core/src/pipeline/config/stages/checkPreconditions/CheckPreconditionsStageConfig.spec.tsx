import { mount } from 'enzyme';
import React from 'react';

import { PreconditionList } from '../../preconditions/PreconditionList';
import { CheckPreconditionsStageConfig } from './CheckPreconditionsStageConfig';

describe('<CheckPreconditionsStageConfig />', () => {
  const createProps = (stageOverrides = {}, pipelineOverrides = {}) => {
    const parentStage: any = {
      name: 'Bake',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'bake',
    };
    const stage: any = {
      name: 'Check Preconditions',
      refId: '2',
      requisiteStageRefIds: ['1'],
      type: 'checkPreconditions',
      ...stageOverrides,
    };
    const pipeline: any = {
      stages: [parentStage, stage],
      strategy: false,
      ...pipelineOverrides,
    };

    return {
      application: {} as any,
      pipeline,
      stage,
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine
        .createSpy('updateStageField')
        .and.callFake((changes: any) => Object.assign(stage, changes)),
    };
  };

  it('passes an empty precondition list without mutating the stage during render', () => {
    const props = createProps();

    const component = mount(<CheckPreconditionsStageConfig {...props} />);

    expect(props.stage.preconditions).toBeUndefined();
    expect(component.find(PreconditionList).prop('preconditions')).toEqual([]);
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('renders the precondition list with strategy and upstream stages', () => {
    const preconditions = [{ context: { expression: '${true}' }, failPipeline: true, type: 'expression' }];
    const props = createProps({ preconditions }, { strategy: true });
    const component = mount(<CheckPreconditionsStageConfig {...props} />);

    const preconditionList = component.find(PreconditionList);

    expect(preconditionList.prop('application')).toBe(props.application);
    expect(preconditionList.prop('preconditions')).toBe(preconditions);
    expect(preconditionList.prop('strategy')).toBe(true);
    expect(preconditionList.prop('upstreamStages')).toEqual([props.pipeline.stages[0]]);
  });

  it('updates stage preconditions through updateStageField', () => {
    const props = createProps({ preconditions: [] });
    const component = mount(<CheckPreconditionsStageConfig {...props} />);
    const updatedPreconditions = [{ context: { expression: '${true}' }, failPipeline: true, type: 'expression' }];

    component.find(PreconditionList).prop('onChange')(updatedPreconditions as any);

    expect(props.updateStageField).toHaveBeenCalledWith({ preconditions: updatedPreconditions });
  });
});
