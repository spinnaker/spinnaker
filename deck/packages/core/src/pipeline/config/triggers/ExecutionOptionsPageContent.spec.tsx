import { mount } from 'enzyme';
import React from 'react';

import { ExecutionOptionsPageContent } from './ExecutionOptionsPageContent';
import type { IPipeline } from '../../../domain';

describe('Execution Options Page Content', () => {
  describe('Max Concurrent Options', () => {
    let pipeline: IPipeline;
    const setPipeline = (overrides: any = {}) => {
      pipeline = {
        application: 'test',
        id: 'test1',
        keepWaitingPipelines: false,
        limitConcurrent: false,
        maxConcurrentExecutions: 0,
        name: 'test p 1', // @ts-ignore
        parameterConfig: [], // @ts-ignore
        stages: [], // @ts-ignore
        triggers: [],
        ...overrides,
      };
    };
    const update = (changes: any = {}) => {
      pipeline = {
        ...pipeline,
        ...changes,
      };
    };
    describe('enabling max concurrent', () => {
      it('sets keepWaitingPipelines to true if limitConcurrent and keepWaitingPipelines are both not truthy', () => {
        setPipeline();
        const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
        const checkbox = wrapper.find('input').at(0);
        checkbox.simulate('change', { target: { checked: true } });
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
      });

      it('does not alter pipeline if limitConcurrent is true', () => {
        setPipeline({ limitConcurrent: true });
        const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
        const checkbox = wrapper.find('input').at(0);
        checkbox.simulate('change', { target: { checked: true } });
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
      });

      it('does not alter pipeline if keepWaitingPipelines is true', () => {
        setPipeline({ keepWaitingPipelines: true });
        const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
        const checkbox = wrapper.find('input').at(0);
        checkbox.simulate('change', { target: { checked: true } });
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
      });

      it('defaults the max concurrent value to 0', () => {
        setPipeline();
        const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        const checkbox = wrapper.find('input').at(0);
        checkbox.simulate('change', { target: { checked: true } });
        const concurrentInput = wrapper.find('input[type="number"]').at(0);
        expect(concurrentInput.prop('value')).toEqual(0);
      });
    });

    it('updates the max concurrent config value when the input is changed', () => {
      setPipeline();
      const value = 22;
      const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      const checkbox = wrapper.find('input').at(0);
      checkbox.simulate('change', { target: { checked: true } });
      const concurrentInput = wrapper.find('input[type="number"]').at(0);
      concurrentInput.simulate('change', { target: { value } });
      expect(pipeline.maxConcurrentExecutions).toEqual(value);
    });

    it('sets the max concurrent value to a whole number if a float is entered', () => {
      setPipeline();
      const value = 3.3;
      const wrapper = mount(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      const checkbox = wrapper.find('input').at(0);
      checkbox.simulate('change', { target: { checked: true } });
      const concurrentInput = wrapper.find('input[type="number"]').at(0);
      concurrentInput.simulate('change', { target: { value } });
      expect(pipeline.maxConcurrentExecutions).toEqual(3);
    });
  });
});
