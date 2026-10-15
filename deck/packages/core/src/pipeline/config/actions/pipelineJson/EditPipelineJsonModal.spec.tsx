import { mount } from 'enzyme';
import React from 'react';

import { EditPipelineJsonModal } from './EditPipelineJsonModal';
import type { IPipeline } from '../../../../domain';

describe('EditPipelineJsonModal', () => {
  const pipeline = (): IPipeline => ({
    application: 'app',
    id: 'pipeline-id',
    name: 'Pipeline',
    stages: [],
    triggers: [],
    parameterConfig: [],
    notifications: [],
    limitConcurrent: true,
    keepWaitingPipelines: false,
  });

  const updatePipeline = (edited: any) => {
    const original = pipeline();
    const closeModal = jasmine.createSpy('closeModal');
    const wrapper = mount(
      <EditPipelineJsonModal pipeline={original} plan={null} closeModal={closeModal} dismissModal={() => {}} />,
    );
    wrapper.setState({ pipelineJSON: JSON.stringify(edited) });
    (wrapper.instance() as any).updatePipeline();
    return { closeModal, original, errorMessage: (wrapper.state() as any).errorMessage as string };
  };

  it('rejects triggers that are not objects', () => {
    const { closeModal, original, errorMessage } = updatePipeline({ ...pipeline(), triggers: ['x'] });

    expect(errorMessage).toBe('The triggers property must be a list of objects.');
    expect(closeModal).not.toHaveBeenCalled();
    expect(original.triggers).toEqual([]);
  });

  it('rejects stages that are not a list', () => {
    const { closeModal, original, errorMessage } = updatePipeline({ ...pipeline(), stages: 'x' });

    expect(errorMessage).toBe('The stages property must be a list of objects.');
    expect(closeModal).not.toHaveBeenCalled();
    expect(original.stages).toEqual([]);
  });

  it('accepts stages and triggers that are lists of objects', () => {
    const edited = { ...pipeline(), triggers: [{ type: 'cron' }] };
    const { closeModal, original, errorMessage } = updatePipeline(edited);

    expect(errorMessage).toBeFalsy();
    expect(closeModal).toHaveBeenCalled();
    expect(original.triggers).toEqual([{ type: 'cron' } as any]);
  });
});
