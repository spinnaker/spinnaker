import type { UIRouterReact } from '@uirouter/react';
import { UIRouterContext, UIViewContext } from '@uirouter/react';
import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ViewStateCache } from '../cache';
import { REACT_MODULE } from '../reactShims';
import { getSelectedItemsPerPage, Tasks } from './Tasks';
import type { ITask } from '../domain';

describe('Tasks', () => {
  let $uiRouter: UIRouterReact;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
      if (!$uiRouter.stateRegistry.get('tasks')) {
        $uiRouter.stateRegistry.register({ name: 'tasks', url: '/tasks' } as any);
      }
      if (!$uiRouter.stateRegistry.get('tasks.taskDetails')) {
        $uiRouter.stateRegistry.register({ name: 'tasks.taskDetails', url: '/:taskId' } as any);
      }
      spyOn($uiRouter.stateService, 'go').and.returnValue(Promise.resolve(null) as any);
      ViewStateCache.get('tasks').removeAll();
    }),
  );

  afterEach(() => ViewStateCache.get('tasks').removeAll());

  it('updates the per-page count without reading from a pooled event', (done) => {
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'tasks',
      defaultData: [{ id: 'task-1', name: 'deploy', status: 'SUCCEEDED', variables: [], steps: [] } as ITask],
    });
    app.tasks.loadFailure = false;
    spyOn(app.tasks, 'activate').and.callThrough();
    spyOn(app.tasks, 'deactivate').and.callThrough();
    spyOn(app.tasks, 'ready').and.returnValue(Promise.resolve(app.tasks.data) as any);

    const wrapper = mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <UIViewContext.Provider value={{ fqn: 'tasks', context: $uiRouter.stateRegistry.get('tasks') as any }}>
          <Tasks app={app} />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

    setTimeout(() => {
      wrapper.update();
      wrapper.find('select').simulate('change', { target: { value: '50' } });
      wrapper.update();

      expect(wrapper.find('select').prop('value')).toBe(50);
      done();
    });
  });

  it('reads the per-page value before React pools the change event', () => {
    const event = { target: { value: '50' } } as React.ChangeEvent<HTMLSelectElement>;

    const itemsPerPage = getSelectedItemsPerPage(event);
    (event as any).target = null;

    expect(itemsPerPage).toBe(50);
  });

  it('renders task failure and reason values as text', (done) => {
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
    spyOn(app.tasks, 'activate').and.callThrough();
    spyOn(app.tasks, 'deactivate').and.callThrough();
    spyOn(app.tasks, 'ready').and.returnValue(Promise.resolve(app.tasks.data) as any);

    const wrapper = mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <UIViewContext.Provider value={{ fqn: 'tasks', context: $uiRouter.stateRegistry.get('tasks') as any }}>
          <Tasks app={app} />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

    setTimeout(() => {
      wrapper.update();

      expect(wrapper.find('.task-error-message img').exists()).toBe(false);
      expect(wrapper.find('.task-reason script').exists()).toBe(false);
      expect(wrapper.find('.task-error-message').text()).toContain('<img src=x onerror=alert(1)>');
      expect(wrapper.find('.task-reason').text()).toContain('<script>alert(1)</script>');
      wrapper.unmount();
      done();
    });
  });
});
