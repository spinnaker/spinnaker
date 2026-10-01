import type { Transition } from '@uirouter/core';
import { act, screen } from '@testing-library/react';
import React from 'react';
import { Subject } from 'rxjs';

import { ProjectHeader } from './ProjectHeader';
import type { IProject } from '../domain';
import { renderWithRouter } from '../utils/testUtils/rtl';

describe('<ProjectHeader />', () => {
  it('renders the dashboard header in a direct React route', () => {
    const success$ = new Subject<Transition>();
    const transition = ({
      router: {
        globals: { success$ },
        stateService: {},
      },
    } as unknown) as Transition;
    const projectConfiguration = {
      name: 'kubernetesproject',
      config: { applications: ['kubernetesapp'] },
    } as IProject;

    const { unmount } = renderWithRouter(
      <ProjectHeader projectConfiguration={projectConfiguration} transition={transition} />,
    );
    act(() => {
      success$.next(({
        to: () => ({ name: 'home.project.dashboard' }),
        params: () => ({}),
      } as unknown) as Transition);
    });

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('kubernetesproject / Project Dashboard');
    expect(screen.getByText('Project Dashboard', { selector: '.clickable' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Project Configuration/ })).toBeInTheDocument();
    expect(success$.observers).toHaveLength(1);

    unmount();
    expect(success$.observers).toHaveLength(0);
  });
});
