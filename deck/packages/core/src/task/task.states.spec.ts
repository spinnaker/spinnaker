import { UIRouterReact } from '@uirouter/react';

import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { getTasksState } from './task.states';
import { TaskReader } from './task.read.service';
import { ApplicationReader } from '../application/service/ApplicationReader';
import { setDirectRouter } from '../navigation/directRouter';
import { configureRouter } from '../navigation/router';

describe('task states', () => {
  const routers: UIRouterReact[] = [];

  afterEach(() => {
    routers.splice(0).forEach((router) => router.dispose());
    setDirectRouter(null);
  });

  it('uses a React component for the application tasks view', () => {
    const state = getTasksState();

    expect(state.views.insight).toEqual(
      expect.objectContaining({
        $type: 'react',
      }),
    );
    expect(typeof state.views.insight.component).toBe('function');
  });

  it('resolves a task permalink through a real direct transition', async () => {
    vi.spyOn(TaskReader, 'getTask').mockResolvedValue({ application: 'payments' } as any);
    vi.spyOn(ApplicationReader, 'getApplication').mockResolvedValue({ name: 'payments', dataSources: [] } as any);
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services, runtime.routingState);
    routers.push(router);

    await router.stateService.go('home.taskLookup', { taskId: 'task-123' }, { location: false });

    expect(TaskReader.getTask).toHaveBeenCalledExactlyOnceWith('task-123');
    expect(router.stateService.current.name).toBe('home.applications.application.tasks.taskDetails');
    expect(router.globals.params).toEqual(expect.objectContaining({ application: 'payments', taskId: 'task-123' }));
  });
});
