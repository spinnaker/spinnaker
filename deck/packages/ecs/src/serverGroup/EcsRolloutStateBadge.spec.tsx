import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { EcsRolloutStateBadge } from './EcsRolloutStateBadge';

describe('EcsRolloutStateBadge', () => {
  it('renders nothing without a rollout state', () => {
    const { container } = render(<EcsRolloutStateBadge rolloutState={undefined as any} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a completed dot', () => {
    const { container } = render(<EcsRolloutStateBadge rolloutState="COMPLETED" />);
    expect(container.querySelectorAll('.ecs-rollout-state-dot--completed').length).toBe(1);
  });

  it('renders an in-progress dot', () => {
    const { container } = render(<EcsRolloutStateBadge rolloutState="IN_PROGRESS" />);
    expect(container.querySelectorAll('.ecs-rollout-state-dot--in-progress').length).toBe(1);
  });

  it('escalates a failed rollout to a labeled pill with the reason in a tooltip', async () => {
    const { container } = render(
      <EcsRolloutStateBadge rolloutState="FAILED" rolloutStateReason="circuit breaker rolled back" />,
    );
    const pills = container.querySelectorAll('.ecs-rollout-state--failed');
    expect(pills.length).toBe(1);
    expect(pills[0]).toHaveTextContent(/^FAILED$/);

    fireEvent.mouseOver(pills[0]);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('FAILED: circuit breaker rolled back');
  });
});
