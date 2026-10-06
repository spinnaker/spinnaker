import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { CanaryExecutionSummaryComponent } from './CanaryExecutionSummary';

describe('CanaryExecutionSummary', () => {
  const summaryProps = {
    application: { executions: { data: [] } },
    execution: { id: 'execution-id', isRunning: false, limitConcurrent: false, pipelineConfigId: 'pipeline-id' },
    stage: { id: 'stage-id', name: 'Canary', isRunning: true, isCompleted: false },
    stageSummary: {
      endTime: 2,
      masterStage: { context: { canary: { status: { status: 'SUCCEEDED' } } }, exceptions: [] },
      masterStageIndex: 2,
      name: 'Canary',
      runningTimeInMs: 3,
      stages: [],
      startTime: 1,
      type: 'canary',
    },
  } as any;

  it('renders comments through sanitized Markdown', () => {
    const comments = '<img src=x onerror=alert(1)> reviewer note';
    const { container } = render(
      <CanaryExecutionSummaryComponent
        {...({ router: {}, stateParams: {}, stateService: {} } as any)}
        {...summaryProps}
        stageSummary={{ ...summaryProps.stageSummary, comments }}
      />,
    );

    expect(screen.getByText('reviewer note')).toBeVisible();
    expect(container.querySelector('img')).not.toHaveAttribute('onerror');
  });

  it('navigates step details through the injected route', () => {
    const go = vi.fn();
    render(
      <CanaryExecutionSummaryComponent
        {...summaryProps}
        {...({
          router: {},
          stateParams: { stage: '3', step: '1', subStage: '4' },
          stateService: { go },
        } as any)}
      />,
    );

    fireEvent.click(screen.getByRole('row', { name: /Canary Summary/ }));

    expect(go).toHaveBeenCalledWith('.', { stage: 3, step: 2, subStage: 4 });
  });
});
