import { UISref, useCurrentStateAndParams } from '@uirouter/react';
import { set } from 'lodash';
import React, { useEffect, useState } from 'react';

import { Application } from '../../application/application.model';
import { IExecution, IPipeline } from '../../domain';
import { Execution } from '../executions/execution/Execution';
import { ManualExecutionModal } from '../manualExecution';
import { useData, useLatestPromise } from '../../presentation';
import { IStateChange, ReactInjector } from '../../reactShims';
import { SchedulerFactory } from '../../scheduler';
import { ExecutionsTransformer } from '../service/ExecutionsTransformer';
import { ExecutionState } from '../../state';
import { logger } from '../../utils';

import './singleExecutionDetails.less';

export interface ISingleExecutionDetailsProps {
  app: Application;
}

export interface ISingleExecutionStateParams {
  application: string;
  executionId: string;
}

export interface ISingleExecutionRouterStateChange extends IStateChange {
  fromParams: ISingleExecutionStateParams;
  toParams: ISingleExecutionStateParams;
}

export function getAndTransformExecution(id: string, app: Application) {
  return ReactInjector.executionService.getExecution(id).then((execution) => {
    ExecutionsTransformer.transformExecution(app, execution);
    return execution;
  });
}

// 3 generations is probably the most reasonable window to render?
function traverseLineage(execution: IExecution, maxGenerations = 3): string[] {
  const lineage: string[] = [];
  if (!execution) {
    return lineage;
  }
  let current = execution;
  // Including the deepest child (topmost, aka current, execution) in the lineage lets us
  // also cache it as part of the ancestry state (we just don't render it).
  // This buys us snappier navigation to descendants because the entire lineage will be local.
  lineage.unshift(current.id);
  while (current.trigger?.parentExecution && lineage.length < maxGenerations) {
    current = current.trigger.parentExecution;
    lineage.unshift(current.id);
  }
  return lineage;
}

export function SingleExecutionDetails(props: ISingleExecutionDetailsProps) {
  const scheduler = SchedulerFactory.createScheduler(5000);
  const { sortFilter } = ExecutionState.filterModel.asFilterModel;
  const { app } = props;

  const [showDurations, setShowDurations] = useState(sortFilter.showDurations);
  const { params } = useCurrentStateAndParams();
  const { executionId } = params;

  const getAncestry = (execution: IExecution): Promise<IExecution[]> => {
    const youngest = ancestry[ancestry.length - 1];
    // youngest and execution don't match only during navigating between executions
    const navigating = execution && youngest && youngest.id !== execution.id;
    const lineage = traverseLineage(execution);

    // used when navigating between executions so clicking between generations is snappy
    const ancestryCache = ancestry.reduce((acc, curr) => set(acc, curr.id, curr), {
      [execution.id]: execution,
    });

    // used to skip re-fetching ancestors that are no longer active
    const inactiveCache = ancestry
      .filter((ancestor) => !ancestor.isActive)
      .reduce((acc, curr) => set(acc, curr.id, curr), { [execution.id]: execution });

    const cache = navigating ? ancestryCache : inactiveCache;

    return Promise.all(
      lineage.map((generation) =>
        cache[generation] ? Promise.resolve(cache[generation]) : getAndTransformExecution(generation, app),
      ),
    );
  };

  // responsible for getting execution whenever executionId (route param) changes
  const { result: execution, status: getExecutionStatus, refresh: refreshExecution } = useLatestPromise(
    () => getAndTransformExecution(executionId, app),
    [executionId],
  );

  const lineage = traverseLineage(execution);
  const transitioningToAncestor = lineage.includes(executionId) && executionId !== execution?.id ? executionId : '';

  // responsible for getting ancestry whenever execution changes or refreshes
  const { result: ancestry } = useData(() => getAncestry(execution), [], [execution, executionId]);

  // Manages the scheduled refresh until the entire lineage has no active executions
  const someActive = [execution]
    .concat(ancestry)
    .filter((x) => x)
    .some((x) => x.isActive);
  useEffect(() => {
    const subscription = someActive && scheduler.subscribe(() => refreshExecution());

    return () => {
      subscription && subscription.unsubscribe();
    };
  }, [someActive]);

  const { result: pipelineConfigs } = useLatestPromise<IPipeline[]>(() => {
    app.pipelineConfigs.activate();
    return app.pipelineConfigs.ready();
  }, []);
  const pipelineConfig =
    pipelineConfigs && execution && pipelineConfigs.find((p: IPipeline) => p.id === execution.pipelineConfigId);

  const showDurationsChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const checked = event.target.checked;
    setShowDurations(checked);
    ExecutionState.filterModel.asFilterModel.sortFilter.showDurations = showDurations;
    logger.log({ category: 'Pipelines', action: 'Toggle Durations', data: { label: checked.toString() } });
  };

  const rerunExecution = (execution: IExecution, application: Application, pipeline: IPipeline) => {
    ManualExecutionModal.show({
      pipeline,
      application,
      trigger: execution.trigger,
    }).then((command) => {
      const { executionService } = ReactInjector;
      executionService.startAndMonitorPipeline(application, command.pipelineName, command.trigger);
      ReactInjector.$state.go('^.^.executions');
    });
  };

  let truncateAncestry = ancestry.length - 1;
  if (executionId && execution && executionId !== execution.id) {
    // We are on the eager end of a transition to a different executionId
    const idx = ancestry.findIndex((a) => a.id === executionId);
    if (idx > -1) {
      // If the incoming executionId is part of the ancestry, we can eagerly truncate the ancestry at that generation
      // for a smoother experience during the transition. That is, if we are navigating from e to b in [a, b, c, d, e],
      // [a, b, c, d] is rendered as part of the ancestry, while [e] is the main execution.
      // We eagerly truncate the ancestry to [a, b] since that will be the end state anyways (transitioningToAncestor hides [e])
      // Once [b] loads, the ancestry is recomputed to just [a] and the rendered executions remain [a, b]
      truncateAncestry = idx + 1;
    }
  }

  // Eagerly hide the main execution when we are transitioning to an ancestor and are not rendering that ancestor
  // Once we've reached it, an effect will re-setTransitioningToAncestor to blank
  const hideMainExecution = !(!transitioningToAncestor || transitioningToAncestor === execution.id);

  return (
    <div style={{ width: '100%', paddingTop: 0 }}>
      {execution && (
        <div className="row">
          <div className="col-md-10 col-md-offset-1">
            <div className="single-execution-details">
              <div className="flex-container-h baseline">
                <h3>
                  <UISref to="^.executions">
                    <a className="clickable">{app.name}</a>
                  </UISref>
                  {' - '}
                  <UISref to="^.executions" params={{ pipeline: execution.name }}>
                    <a className="clickable">{execution.name}</a>
                  </UISref>
                </h3>

                <div className="form-group checkbox flex-pull-right">
                  <label>
                    <input type="checkbox" checked={showDurations || false} onChange={showDurationsChanged} />
                    <span> stage durations</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
      {execution &&
        ancestry
          .filter((_ancestor, i) => i < truncateAncestry)
          .map((ancestor, i) => (
            <div className="row" key={ancestor.id}>
              <div className="col-md-10 col-md-offset-1 executions">
                <Execution
                  key={ancestor.id}
                  execution={ancestor}
                  descendantExecutionId={i < ancestry.length - 1 ? ancestry[i + 1].id : execution.id}
                  showConfigureButton={true}
                  application={app}
                  pipelineConfig={null}
                  standalone={true}
                  showDurations={showDurations}
                />
              </div>
            </div>
          ))}
      {execution && !hideMainExecution && (
        <div className="row">
          <div className="col-md-10 col-md-offset-1 executions">
            <Execution
              execution={execution}
              showConfigureButton={true}
              key={execution.id}
              application={app}
              pipelineConfig={null}
              standalone={true}
              showDurations={showDurations}
              onRerun={
                pipelineConfig &&
                (() => {
                  rerunExecution(execution, app, pipelineConfig);
                })
              }
            />
          </div>
        </div>
      )}
      {getExecutionStatus === 'REJECTED' && (
        <div className="row" style={{ minHeight: '300px' }}>
          <h4 className="text-center">
            <p>The execution cannot be found.</p>
            <UISref to="^.executions" params={{ application: app.name }}>
              <a>Back to Executions.</a>
            </UISref>
          </h4>
        </div>
      )}
    </div>
  );
}
