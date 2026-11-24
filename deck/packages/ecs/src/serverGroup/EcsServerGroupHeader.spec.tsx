import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { EcsSequenceAndBuildAndImages, EcsServerGroupHeader } from './EcsServerGroupHeader';

const baseProps = (serverGroup: any) =>
  ({
    application: { serverGroups: { refresh: vi.fn() } } as any,
    isMultiSelected: false,
    jenkins: undefined as any,
    docker: undefined as any,
    sortFilter: { multiselect: false } as any,
    serverGroup: { runningExecutions: [], runningTasks: [], ...serverGroup },
  } as any);

const sequenceLabels = (container: HTMLElement) =>
  Array.from(container.querySelectorAll('.server-group-sequence')).map((element) => element.textContent.trim());

describe('EcsSequenceAndBuildAndImages', () => {
  it('shows the task-definition revision when there is no server-group sequence', () => {
    const { container } = render(
      <EcsSequenceAndBuildAndImages {...baseProps({ moniker: {}, taskDefinitionRevision: 42 })} />,
    );
    expect(sequenceLabels(container)).toEqual(['v042']);
  });

  it('does not truncate task-definition revisions past three digits', () => {
    const { container } = render(
      <EcsSequenceAndBuildAndImages {...baseProps({ moniker: {}, taskDefinitionRevision: 1234 })} />,
    );
    expect(sequenceLabels(container)).toEqual(['v1234']);
  });

  it('leaves the sequence, build and images to the core component', () => {
    const props = baseProps({ moniker: { sequence: 3 }, taskDefinitionRevision: 42 });
    const { container } = render(<EcsSequenceAndBuildAndImages {...props} />);
    // A versioned (classic) server group shows only core's vNNN sequence (rendered from the same
    // server group), not the revision label.
    expect(sequenceLabels(container)).toEqual(['v003']);
    expect(screen.queryByText('v042')).not.toBeInTheDocument();
  });

  it('renders the rollout badge for ecs-native server groups', async () => {
    const { container } = render(
      <EcsSequenceAndBuildAndImages
        {...baseProps({
          isNative: true,
          moniker: {},
          rolloutState: 'FAILED',
          rolloutStateReason: 'circuit breaker',
          taskDefinitionRevision: 42,
        })}
      />,
    );
    const badge = container.querySelector('.ecs-rollout-state--failed');
    expect(badge).toHaveTextContent('FAILED');
    fireEvent.mouseOver(badge);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('FAILED: circuit breaker');
  });

  it('does not render the rollout badge for classic ECS server groups', () => {
    const { container } = render(
      <EcsSequenceAndBuildAndImages {...baseProps({ moniker: { sequence: 3 }, rolloutState: 'IN_PROGRESS' })} />,
    );
    expect(container.querySelector('.ecs-rollout-state, .ecs-rollout-state-dot')).toBeNull();
  });
});

describe('EcsServerGroupHeader', () => {
  it('renders the ECS sequence component in the provider header override', () => {
    const { container } = render(<EcsServerGroupHeader {...baseProps({ moniker: {}, taskDefinitionRevision: 7 })} />);
    expect(container.querySelector('.server-group-title .section-title')).toBeInTheDocument();
    // The task-definition revision label is only produced by EcsSequenceAndBuildAndImages.
    expect(sequenceLabels(container)).toEqual(['v007']);
  });
});
