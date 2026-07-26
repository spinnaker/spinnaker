import { UISref, useCurrentStateAndParams, useRouter } from '@uirouter/react';
import React, { useEffect, useState } from 'react';

import { StatusGlyph } from './StatusGlyph';
import { TaskProgressBar } from './TaskProgressBar';
import { AccountTag } from '../account';
import type { Application } from '../application';
import { PaginationControls } from '../application/search/PaginationControls';
import { ViewStateCache } from '../cache';
import { SETTINGS } from '../config/settings';
import { ConfirmationModalService } from '../confirmationModal';
import { displayableTasks } from './displayableTasks.filter';
import type { ITask, ITaskStep } from '../domain';
import { robotToHuman } from '../presentation';
import { TaskWriter } from './task.write.service';
import { duration, timestamp } from '../utils/timeFormatters';

interface ITasksProps {
  app: Application;
}

interface ITasksViewState {
  cancelling: boolean;
  expandedTasks: string[];
  itemsPerPage: number;
  loadError: boolean;
  loading: boolean;
  nameFilter: string;
  taskStateFilter: string;
}

const tasksViewStateCache = ViewStateCache.get('tasks') || ViewStateCache.createCache('tasks', { version: 1 });
const taskStateFilters = [
  { label: 'All', value: '', item: {} },
  { label: 'Not Started', value: 'NOT_STARTED', item: { hasNotStarted: true } },
  { label: 'Running', value: 'RUNNING', item: { isRunning: true } },
  { label: 'Succeeded', value: 'SUCCEEDED', item: { isCompleted: true } },
  { label: 'Terminal', value: 'TERMINAL', item: { isFailed: true } },
  { label: 'Canceled', value: 'CANCELED', item: { isCanceled: true } },
];

function getInitialViewState(applicationName: string, q: string, taskId: string): ITasksViewState {
  const cached = tasksViewStateCache.get(applicationName) || { taskStateFilter: '', expandedTasks: [] };
  const common = tasksViewStateCache.get('#common');
  const expandedTasks = cached.expandedTasks || [];

  return {
    cancelling: false,
    expandedTasks: taskId && !expandedTasks.includes(taskId) ? expandedTasks.concat(taskId) : expandedTasks,
    itemsPerPage: common ? common.itemsPerPage : cached.itemsPerPage || 20,
    loadError: false,
    loading: true,
    nameFilter: q || taskId || cached.nameFilter || '',
    taskStateFilter: taskId ? '' : cached.taskStateFilter || '',
  };
}

function getTaskValue(task: ITask, key: string): any {
  return task.getValueFor ? task.getValueFor(key) : undefined;
}

function getDisplayUser(task: ITask): string {
  return typeof (task as any).getDisplayUser === 'function'
    ? (task as any).getDisplayUser()
    : getTaskValue(task, 'user');
}

function matchesNameFilter(task: ITask, filter: string): boolean {
  if (!filter) {
    return true;
  }

  const normalizedSearch = filter.toLowerCase();
  const searchable = [
    task.name,
    task.id,
    getTaskValue(task, 'credentials'),
    getTaskValue(task, 'region'),
    (getTaskValue(task, 'regions') || []).join(' '),
    getTaskValue(task, 'user'),
    task.execution?.authentication?.user,
  ];

  return searchable.some((value) =>
    String(value || '')
      .toLowerCase()
      .includes(normalizedSearch),
  );
}

function sortTasks(tasks: ITask[], viewState: ITasksViewState): ITask[] {
  return tasks
    .filter((task) => task.name)
    .filter((task) => matchesNameFilter(task, viewState.nameFilter))
    .filter((task) => !viewState.taskStateFilter || task.status === viewState.taskStateFilter)
    .sort((taskA, taskB) => (taskB.startTime > taskA.startTime ? 1 : taskB.startTime < taskA.startTime ? -1 : 0));
}

function getFirstDeployServerGroupName(task: ITask): string | undefined {
  const stage = task.execution?.stages?.find((executionStage: any) =>
    executionStage.tasks?.some((taskStep: any) =>
      ['createCopyLastAsg', 'createDeploy', 'cloneServerGroup', 'createServerGroup'].includes(taskStep.name),
    ),
  );
  const deployServerGroups = stage?.context?.['deploy.server.groups'];
  const firstAccount = deployServerGroups && Object.values(deployServerGroups)[0];

  return Array.isArray(firstAccount) ? firstAccount[0] : undefined;
}

function getAccountId(task: ITask): string | undefined {
  return task.variables?.find((variable: any) => variable.key === 'account')?.value;
}

function getRegion(task: ITask): string | undefined {
  const regionVariable = (task.variables || []).find(
    (variable: any) =>
      ['deploy.server.groups', 'availabilityZones'].includes(variable.key) && Object.keys(variable.value).length,
  );

  return regionVariable && Object.keys(regionVariable.value)[0];
}

function getProviderForServerGroupByTask(application: Application, task: ITask): string | undefined {
  const serverGroupName = getFirstDeployServerGroupName(task);

  return application.serverGroups?.data?.find((serverGroup: any) => serverGroup.name === serverGroupName)?.type;
}

function TaskStateFilter({
  viewState,
  setViewState,
}: {
  viewState: ITasksViewState;
  setViewState: React.Dispatch<React.SetStateAction<ITasksViewState>>;
}) {
  return (
    <div className="btn-group">
      {taskStateFilters.map((filter) => (
        <button
          className={`btn btn-sm btn-default ${viewState.taskStateFilter === filter.value ? 'active' : ''}`}
          key={filter.value || 'all'}
          onClick={() => setViewState((state) => ({ ...state, taskStateFilter: filter.value }))}
          type="button"
        >
          {filter.value && <StatusGlyph item={filter.item as ITaskStep} />} {filter.label}
        </button>
      ))}
    </div>
  );
}

export function getSelectedItemsPerPage(event: React.ChangeEvent<HTMLSelectElement>): number {
  return Number(event.target.value);
}

export function Tasks({ app }: ITasksProps) {
  const router = useRouter();
  const { params, state } = useCurrentStateAndParams();
  const [viewState, setViewState] = useState(() =>
    getInitialViewState(app.name, params.q as string, params.taskId as string),
  );
  const [currentPage, setCurrentPage] = useState(1);

  useEffect(() => {
    setViewState((current) => {
      const taskId = params.taskId as string;
      const expandedTasks =
        taskId && !current.expandedTasks.includes(taskId)
          ? current.expandedTasks.concat(taskId)
          : current.expandedTasks;

      return {
        ...current,
        expandedTasks,
        nameFilter: ((params.q as string) || taskId || current.nameFilter) as string,
        taskStateFilter: taskId ? '' : current.taskStateFilter,
      };
    });
  }, [params.q, params.taskId]);

  useEffect(() => {
    app.setActiveState(app.tasks);
    app.tasks.activate();

    const unsubscribe = app.tasks.onRefresh(
      null,
      () => setViewState((current) => ({ ...current, loadError: app.tasks.loadFailure, loading: false })),
      () => setViewState((current) => ({ ...current, loadError: true, loading: false })),
    );

    app.tasks
      .ready()
      .then(() => setViewState((current) => ({ ...current, loadError: app.tasks.loadFailure, loading: false })));

    return () => {
      unsubscribe();
      app.setActiveState();
      app.tasks.deactivate();
    };
  }, [app]);

  useEffect(() => {
    tasksViewStateCache.put(app.name, viewState);
    tasksViewStateCache.put('#common', { itemsPerPage: viewState.itemsPerPage });
  }, [app.name, viewState]);

  const sortedTasks = sortTasks(app.tasks.data || [], viewState);
  const totalPages = Math.max(1, Math.ceil(sortedTasks.length / viewState.itemsPerPage));
  const pageTasks = sortedTasks.slice((currentPage - 1) * viewState.itemsPerPage, currentPage * viewState.itemsPerPage);
  const tasksUrl = [SETTINGS.gateUrl, 'applications', app.name, 'tasks/'].join('/');

  function updateNameFilter(nameFilter: string) {
    setCurrentPage(1);
    setViewState((current) => ({ ...current, nameFilter }));
    router.stateService.go(state.name.includes('.taskDetails') ? '^' : '.', { q: nameFilter });
  }

  function toggleDetails(taskId: string) {
    setViewState((current) => ({
      ...current,
      expandedTasks: current.expandedTasks.includes(taskId)
        ? current.expandedTasks.filter((id) => id !== taskId)
        : current.expandedTasks.concat(taskId),
    }));
  }

  function cancelTask(taskId: string) {
    const task = app.tasks.data.find((candidate: ITask) => candidate.id === taskId);
    ConfirmationModalService.confirm({
      header: `Really cancel ${task.name}?`,
      buttonText: 'Yes',
      cancelButtonText: 'No',
      submitMethod: () => {
        setViewState((current) => ({ ...current, cancelling: true }));
        return TaskWriter.cancelTask(taskId).then(() => {
          setViewState((current) => ({ ...current, cancelling: false }));
          app.tasks.refresh();
        });
      },
    });
  }

  if (viewState.loading) {
    return (
      <div className="row tasks-wrapper">
        <div className="horizontal center spinner-container" style={{ width: '100%' }}>
          <span className="fa fa-spin fa-spinner" />
        </div>
      </div>
    );
  }

  if (viewState.loadError) {
    return (
      <div className="row tasks-wrapper">
        <div className="col-md-12">
          <h4 className="text-center">There was an error loading tasks. Please try again later.</h4>
        </div>
      </div>
    );
  }

  return (
    <div className="row tasks-wrapper">
      <div className="col-md-12 tasks">
        <div className="row tasks-header">
          <div className="col-md-5">
            <TaskStateFilter viewState={viewState} setViewState={setViewState} />
          </div>
          <div className="col-md-4 text-right horizontal right" style={{ paddingRight: 0, marginLeft: 'auto' }}>
            {viewState.nameFilter.length > 0 && (
              <button
                className="btn btn-link"
                onClick={() => updateNameFilter('')}
                style={{ paddingRight: 5 }}
                type="button"
              >
                <span className="glyphicon glyphicon-remove" />
              </button>
            )}
            <div className="form-group has-feedback">
              <label className="sr-only" htmlFor="task-filter">
                Filter tasks by name, user, or task ID
              </label>
              <input
                className="form-control input-sm"
                id="task-filter"
                onChange={(event) => updateNameFilter(event.target.value)}
                placeholder="Filter tasks by name, user, or task ID"
                type="search"
                value={viewState.nameFilter}
              />
              <span className="glyphicon glyphicon-search form-control-feedback" />
            </div>
          </div>
        </div>
        <div className="row tasks-content">
          {!sortedTasks.length && (
            <div className="col-md-10 col-md-offset-1 text-center" style={{ marginTop: 30 }}>
              {!viewState.nameFilter && !viewState.taskStateFilter ? (
                <h4>We couldn't find any tasks for {app.name}</h4>
              ) : (
                <div>
                  <h4>We couldn't find any tasks matching the filters you've specified.</h4>
                  <p style={{ marginTop: 20 }}>
                    <strong>Note</strong> that Spinnaker only tracks tasks for two weeks.
                  </p>
                </div>
              )}
            </div>
          )}
          {!!sortedTasks.length && (
            <div className="col-md-12">
              <table className="table table-condensed">
                <thead>
                  <tr>
                    <th style={{ width: '30%' }}>Task</th>
                    <th style={{ width: '5%' }}>Account</th>
                    <th style={{ width: '8%' }}>Region</th>
                    <th style={{ width: '12%' }}>Progress</th>
                    <th style={{ width: '12%' }}>Started</th>
                    <th style={{ width: '12%' }}>Ended</th>
                    <th style={{ width: '8%' }}>Running Time</th>
                    <th>User</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pageTasks.map((task) => {
                    const expanded = viewState.expandedTasks.includes(task.id);
                    const serverGroupName = getFirstDeployServerGroupName(task);
                    const provider = getProviderForServerGroupByTask(app, task);
                    return (
                      <React.Fragment key={task.id}>
                        <tr className="clickable" onClick={() => toggleDetails(task.id)}>
                          <td>
                            <div className="task-name">
                              <a>
                                <span className={`glyphicon glyphicon-chevron-${expanded ? 'down' : 'right'}`} />
                              </a>
                              {task.name}
                            </div>
                            {serverGroupName && (
                              <div className="task-name">
                                <a>
                                  <span className="glyphicon glyphicon-none" />
                                </a>
                                {provider ? (
                                  <span>
                                    Deployed{' '}
                                    <UISref
                                      params={{
                                        accountId: getAccountId(task),
                                        application: app.name,
                                        provider,
                                        region: getRegion(task),
                                        serverGroup: serverGroupName,
                                      }}
                                      to="^.insight.clusters.serverGroup"
                                    >
                                      <a onClick={(event) => event.stopPropagation()}>{serverGroupName}</a>
                                    </UISref>
                                  </span>
                                ) : (
                                  <span>Deployed {serverGroupName}</span>
                                )}
                              </div>
                            )}
                          </td>
                          <td>
                            <AccountTag account={getTaskValue(task, 'credentials') || getTaskValue(task, 'account')} />
                          </td>
                          <td>
                            {getTaskValue(task, 'region') ||
                              (getTaskValue(task, 'regions') || []).join(', ') ||
                              getTaskValue(task, 'location')}
                          </td>
                          <td>
                            <TaskProgressBar task={task} />
                          </td>
                          <td>{timestamp(task.startTime)}</td>
                          <td>{timestamp(task.endTime)}</td>
                          <td>
                            {task.runningTimeInMs && !viewState.cancelling ? duration(task.runningTimeInMs) : '-'}
                          </td>
                          <td>{getDisplayUser(task)}</td>
                          <td className="small text-center">
                            {(task.isRunning || task.hasNotStarted) && (
                              <a
                                onClick={(event) => {
                                  event.stopPropagation();
                                  cancelTask(task.id);
                                }}
                              >
                                <span className="glyphicon glyphicon-remove" />
                              </a>
                            )}
                          </td>
                        </tr>
                        {expanded &&
                          displayableTasks(task.steps || []).map((step: ITaskStep) => (
                            <tr className="task-step" key={`${task.id}-${step.name}-${step.startTime}`}>
                              <td colSpan={4}>
                                <StatusGlyph item={step} /> {robotToHuman(step.name)}
                              </td>
                              <td>{timestamp(step.startTime)}</td>
                              <td>{timestamp(step.endTime)}</td>
                              <td colSpan={3}>{duration(step.runningTimeInMs)}</td>
                            </tr>
                          ))}
                        {expanded && task.isFailed && (
                          <tr className="task-error-message danger">
                            <td colSpan={9}>
                              <strong>Exception:</strong> <span>{task.failureMessage || 'No reason provided'}</span>
                            </td>
                          </tr>
                        )}
                        {expanded && getTaskValue(task, 'reason') && (
                          <tr className="task-reason">
                            <td colSpan={9}>
                              <strong>Reason:</strong> <span>{getTaskValue(task, 'reason')}</span>
                            </td>
                          </tr>
                        )}
                        {expanded && (
                          <tr>
                            <td className="small text-right" colSpan={9}>
                              <a href={`${tasksUrl}${task.id}`} target="_blank">
                                View as JSON
                              </a>{' '}
                              |{' '}
                              <UISref params={{ taskId: task.id }} to=".taskDetails">
                                <a target="_blank">Permalink</a>
                              </UISref>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="row pagination-footer">
          <div className="col-md-9">
            {!viewState.loading && sortedTasks.length > viewState.itemsPerPage && (
              <PaginationControls
                activePage={currentPage}
                onPageChanged={((page: number) => setCurrentPage(page)) as any}
                totalPages={totalPages}
              />
            )}
          </div>
          <div className="col-md-3 text-right">
            <div className="form-group">
              Show{' '}
              <select
                className="input input-sm"
                onChange={(event) => {
                  const itemsPerPage = getSelectedItemsPerPage(event);
                  setCurrentPage(1);
                  setViewState((current) => ({ ...current, itemsPerPage }));
                }}
                style={{ width: 50 }}
                value={viewState.itemsPerPage}
              >
                {[10, 20, 30, 50, 100, 200].map((count) => (
                  <option key={count} value={count}>
                    {count}
                  </option>
                ))}
              </select>{' '}
              per page
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
