import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import React from 'react';

import type { Application } from '../application';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ViewStateCache } from '../cache';
import { getSelectedItemsPerPage, Tasks } from './Tasks';
import type { ITask } from '../domain';

describe('Tasks', () => {
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.stateRegistry.register({ name: 'tasks', url: '/tasks' });
    router.stateRegistry.register({ name: 'tasks.taskDetails', url: '/:taskId' });
    vi.spyOn(router.stateService, 'go').mockImplementation(() => Promise.resolve(null));
    ViewStateCache.get('tasks').removeAll();
  });

  afterEach(() => {
    ViewStateCache.get('tasks').removeAll();
    router.dispose();
  });

  const renderTasks = (app: Application) =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={
            { fqn: 'tasks', context: router.stateRegistry.get('tasks') } as React.ContextType<typeof UIViewContext>
          }
        >
          <Tasks app={app} />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  it('updates the per-page count without reading from a pooled event', async () => {
    const user = setupUser();
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'tasks',
      defaultData: [{ id: 'task-1', name: 'deploy', status: 'SUCCEEDED', variables: [], steps: [] } as ITask],
    });
    app.tasks.loadFailure = false;
    const activate = vi.spyOn(app.tasks, 'activate');
    const deactivate = vi.spyOn(app.tasks, 'deactivate');
    vi.spyOn(app.tasks, 'ready').mockResolvedValue(app.tasks.data);

    const { unmount } = renderTasks(app);
    const select = await screen.findByRole('combobox');
    await user.selectOptions(select, '50');

    expect(select).toHaveValue('50');
    expect(activate).toHaveBeenCalledTimes(1);

    unmount();
    expect(deactivate).toHaveBeenCalledTimes(1);
  });

  it('reads the per-page value before React pools the change event', () => {
    const event = { target: { value: '50' } } as React.ChangeEvent<HTMLSelectElement>;

    const itemsPerPage = getSelectedItemsPerPage(event);
    Object.defineProperty(event, 'target', { value: null });

    expect(itemsPerPage).toBe(50);
  });

  it('renders task failure and reason values as text', async () => {
    ViewStateCache.get('tasks').put('app', { expandedTasks: ['task-1'] });
    const task = {
      id: 'task-1',
      name: 'deploy',
      status: 'TERMINAL',
      isFailed: true,
      failureMessage: '<img src=x onerror=alert(1)>',
      variables: [],
      steps: [],
      getValueFor: (key: string) => (key === 'reason' ? '<script>alert(1)</script>' : undefined),
    } as ITask;
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'tasks',
      defaultData: [task],
    });
    app.tasks.loadFailure = false;
    vi.spyOn(app.tasks, 'ready').mockResolvedValue(app.tasks.data);

    const { container } = renderTasks(app);

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(container.querySelector('.task-error-message img')).not.toBeInTheDocument();
    expect(container.querySelector('.task-reason script')).not.toBeInTheDocument();
  });
});
