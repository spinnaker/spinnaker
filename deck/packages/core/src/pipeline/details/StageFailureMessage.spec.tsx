import { screen } from '@testing-library/react';
import React from 'react';

import { getStageFailureRoute, StageFailureMessageComponent } from './StageFailureMessage';
import { renderWithRouter } from '../../utils/testUtils';

describe('StageFailureMessage', () => {
  it('builds failed-stage navigation from the injected state service', () => {
    const stateService = { current: { name: 'home.applications.application.pipelines.execution' } } as any;

    expect(getStageFailureRoute(stateService, 42, 7)).toEqual({
      params: { executionId: 42, stageId: 7 },
      state: 'home.applications.application.pipelines.execution',
    });
  });

  it('omits an absent parent execution id from failed-stage navigation', () => {
    const stateService = { current: { name: 'home.applications.application.pipelines.execution' } } as any;

    expect(getStageFailureRoute(stateService, undefined, 7)).toEqual({
      params: { stageId: 7 },
      state: 'home.applications.application.pipelines.execution',
    });
  });
});

describe('StageFailureMessageComponent', () => {
  it('renders a single failure message with the wrap-friendly class', () => {
    renderWithRouter(<StageFailureMessageComponent {...({ stage: { isFailed: true }, message: 'boom' } as any)} />);

    expect(screen.getByText('boom').closest('.Markdown')).toHaveClass('break-word-wrap');
  });

  it('renders multiple exception messages with the wrap-friendly class', () => {
    const { container } = renderWithRouter(
      <StageFailureMessageComponent {...({ stage: { isFailed: true }, messages: ['first', 'second'] } as any)} />,
    );

    const markdowns = container.querySelectorAll('.Markdown');
    expect(markdowns).toHaveLength(2);
    expect(screen.getByText('first').closest('.Markdown')).toHaveClass('break-word-wrap');
    expect(screen.getByText('second').closest('.Markdown')).toHaveClass('break-word-wrap');
    markdowns.forEach((node) => expect(node).toHaveClass('break-word-wrap'));
  });
});
