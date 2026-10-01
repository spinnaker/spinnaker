import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { ExecutionBarLabel, ExecutionBarLabelComponent } from './ExecutionBarLabel';

describe('ExecutionBarLabel', () => {
  it('uses injected route params to include the active grouped stage name', () => {
    render(
      <ExecutionBarLabelComponent
        stage={
          {
            groupStages: [{ name: 'Child stage' }],
            index: 987654,
            name: 'Parent stage',
            type: 'group',
          } as any
        }
        stateParams={{ stage: '987654', subStage: '0' }}
      />,
    );

    expect(screen.getByText('Parent stage: Child stage')).toBeVisible();
  });

  it('renders the default tooltip without runtime context in the overlay root', () => {
    const stage = {
      id: 'stage-id',
      labelComponent: ExecutionBarLabel,
      name: 'Default stage',
      stages: [],
      suspendedStageTypes: new Set(),
      type: 'test',
    } as any;
    render(
      <ExecutionBarLabelComponent
        application={{} as any}
        deckRuntimeServices={{ executionService: {} } as any}
        execution={{ hydrated: true } as any}
        executionMarker={true}
        router={{} as any}
        stage={stage}
        stateParams={{}}
        stateService={{} as any}
      >
        <span className="tooltip-trigger">marker</span>
      </ExecutionBarLabelComponent>,
    );

    expect(() => fireEvent.mouseOver(screen.getByText('marker'))).not.toThrow();
  });
});
