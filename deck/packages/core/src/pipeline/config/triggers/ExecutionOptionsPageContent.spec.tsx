import { fireEvent, render, screen } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
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
      it('sets keepWaitingPipelines to true if limitConcurrent and keepWaitingPipelines are both not truthy', async () => {
        const user = setupUser();
        setPipeline();
        render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
        await user.click(screen.getByRole('checkbox', { name: /Disable concurrent pipeline executions/ }));
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
      });

      it('does not alter pipeline if limitConcurrent is true', async () => {
        const user = setupUser();
        setPipeline({ limitConcurrent: true });
        render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
        await user.click(screen.getByRole('checkbox', { name: /Disable concurrent pipeline executions/ }));
        expect(pipeline.keepWaitingPipelines).toBeFalsy();
      });

      it('does not alter pipeline if keepWaitingPipelines is true', async () => {
        const user = setupUser();
        setPipeline({ keepWaitingPipelines: true });
        render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
        await user.click(screen.getByRole('checkbox', { name: /Disable concurrent pipeline executions/ }));
        expect(pipeline.keepWaitingPipelines).toBeTruthy();
      });

      it('enables limitConcurrent and keepWaitingPipelines in one update', async () => {
        const user = setupUser();
        setPipeline();
        const rendered = pipeline;
        // Like the pipeline editor, each update starts from the pipeline of the last render
        const updateFromRender = (changes: any) => {
          pipeline = { ...rendered, ...changes };
        };
        render(<ExecutionOptionsPageContent pipeline={rendered} updatePipelineConfig={updateFromRender} />);
        await user.click(screen.getByRole('checkbox', { name: /Disable concurrent pipeline executions/ }));
        expect(pipeline.limitConcurrent).toBe(true);
        expect(pipeline.keepWaitingPipelines).toBe(true);
        expect(rendered.keepWaitingPipelines).toBe(false);
      });

      it('defaults the max concurrent value to 0', async () => {
        const user = setupUser();
        setPipeline();
        render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
        await user.click(screen.getByRole('checkbox', { name: /Disable concurrent pipeline executions/ }));
        expect(screen.getByRole('spinbutton')).toHaveValue(0);
      });
    });

    it('updates the max concurrent config value when the input is changed', () => {
      setPipeline();
      const value = 22;
      render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      fireEvent.change(screen.getByRole('spinbutton'), { target: { value } });
      expect(pipeline.maxConcurrentExecutions).toEqual(value);
    });

    it('sets the max concurrent value to a whole number if a float is entered', () => {
      setPipeline();
      const value = 3.3;
      render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      fireEvent.change(screen.getByRole('spinbutton'), { target: { value } });
      expect(pipeline.maxConcurrentExecutions).toEqual(3);
    });

    it('stores 0 when the max concurrent input is cleared', () => {
      setPipeline({ maxConcurrentExecutions: 4 });
      render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '' } });
      expect(pipeline.maxConcurrentExecutions).toEqual(0);
    });

    it('follows max concurrent changes made outside the input', () => {
      setPipeline({ maxConcurrentExecutions: 2 });
      const { rerender } = render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      rerender(
        <ExecutionOptionsPageContent
          pipeline={{ ...pipeline, maxConcurrentExecutions: 5 }}
          updatePipelineConfig={update}
        />,
      );
      expect(screen.getByRole('spinbutton')).toHaveValue(5);
    });

    it('keeps the typed max concurrent value when the pipeline prop is not updated', () => {
      setPipeline({ maxConcurrentExecutions: 2 });
      render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      fireEvent.change(screen.getByRole('spinbutton'), { target: { value: 7 } });
      expect(screen.getByRole('spinbutton')).toHaveValue(7);
    });

    it('shows the pipeline value when the pipeline is replaced with the same value', () => {
      setPipeline({ maxConcurrentExecutions: 2 });
      const replacement = { ...pipeline };
      const { rerender } = render(<ExecutionOptionsPageContent pipeline={pipeline} updatePipelineConfig={update} />);
      fireEvent.change(screen.getByRole('spinbutton'), { target: { value: 7 } });
      // Like a revert in a templated pipeline, whose pipeline prop never had the typed value
      rerender(<ExecutionOptionsPageContent pipeline={replacement} updatePipelineConfig={update} />);
      expect(screen.getByRole('spinbutton')).toHaveValue(2);
    });
  });
});
