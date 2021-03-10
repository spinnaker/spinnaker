import { UISref } from '@uirouter/react';
import React, { useEffect, useState } from 'react';
import ReactGA from 'react-ga';

import { Application } from 'core/application/application.model';
import { IExecution, IPipeline } from 'core/domain';
import { Tooltip, useLatestPromise } from 'core/presentation';
import { IStateChange, ReactInjector } from 'core/reactShims';
import { SchedulerFactory } from 'core/scheduler';
import { ExecutionState } from 'core/state';

import { Execution } from '../executions/execution/Execution';
import { ManualExecutionModal } from '../manualExecution';
import { ExecutionsTransformer } from '../service/ExecutionsTransformer';

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

export function SingleExecutionDetails(props: ISingleExecutionDetailsProps) {
  const scheduler = SchedulerFactory.createScheduler(5000);
  const { $state, stateEvents } = ReactInjector;
  const { sortFilter } = ExecutionState.filterModel.asFilterModel;
  const { app } = props;

  const [showDurations, setShowDurations] = useState(sortFilter.showDurations);
  const [executionId, setExecutionId] = useState($state.params.executionId);

  // responsible for getting execution whenever executionId (route param) changes
  const { result: execution, status: getExecutionStatus, refresh: refreshExecution } = useLatestPromise(
    () => getAndTransformExecution(executionId, app),
    [executionId],
  );

  // Manages the scheduled refresh until the execution no is active har har
  const someActive = [execution].filter((x) => x).some((x) => x.isActive);
  useEffect(() => {
    const subscription = someActive && scheduler.subscribe(() => refreshExecution());

    return () => {
      subscription && subscription.unsubscribe();
    };
  }, [someActive]);

  // Responsible for listening to state changes and updating executionId
  useEffect(() => {
    const subscription = stateEvents.stateChangeSuccess.subscribe((stateChange: ISingleExecutionRouterStateChange) => {
      if (
        !stateChange.to.name.includes('pipelineConfig') &&
        !stateChange.to.name.includes('executions') &&
        (stateChange.toParams.application !== stateChange.fromParams.application ||
          stateChange.toParams.executionId !== stateChange.fromParams.executionId)
      ) {
        setExecutionId(stateChange.toParams.executionId);
      }
    });
    return () => {
      subscription.unsubscribe();
    };
  }, [execution]);

  const { result: pipelineConfigs } = useLatestPromise<IPipeline[]>(() => {
    app.pipelineConfigs.activate();
    return app.pipelineConfigs.ready();
  }, []);

  const pipelineConfig =
    pipelineConfigs && execution && pipelineConfigs.find((p: IPipeline) => p.id === execution.pipelineConfigId);

  // Responsible for propagating showDurations changes to the filterModel
  useEffect(() => {
    ExecutionState.filterModel.asFilterModel.sortFilter.showDurations = showDurations;
  }, [showDurations]);

  const showDurationsChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const checked = event.target.checked;
    setShowDurations(checked);
    ReactGA.event({ category: 'Pipelines', action: 'Toggle Durations', label: checked.toString() });
  };

  const handleConfigureClicked = (e: React.MouseEvent<HTMLElement>): void => {
    ReactGA.event({ category: 'Execution', action: 'Configuration' });
    ReactInjector.$state.go('^.pipelineConfig', {
      application: app.name,
      pipelineId: execution.pipelineConfigId,
    });
    e.stopPropagation();
  };

  const rerunExecution = (execution: IExecution) => {
    ManualExecutionModal.show({
      pipeline: pipelineConfig,
      application: app,
      trigger: execution.trigger,
    }).then((command) => {
      const { executionService } = ReactInjector;
      executionService.startAndMonitorPipeline(app, command.pipelineName, command.trigger);
      ReactInjector.$state.go('^.^.executions');
    });
  };

  const defaultExecutionParams = { application: app.name, executionId: execution?.id || '' };
  const executionParams = ReactInjector.$state.params.executionParams || defaultExecutionParams;

  return (
    <div style={{ width: '100%', paddingTop: 0 }}>
      {execution && (
        <div className="row">
          <div className="col-md-10 col-md-offset-1">
            <div className="single-execution-details">
              <div className="flex-container-h baseline">
                <h3>
                  <Tooltip value="Back to Executions">
                    <UISref to="^.executions.execution" params={executionParams}>
                      <a className="btn btn-configure">
                        <span className="glyphicon glyphicon glyphicon-circle-arrow-left" />
                      </a>
                    </UISref>
                  </Tooltip>
                  {execution.name}
                </h3>

                <div className="form-group checkbox flex-pull-right">
                  <label>
                    <input type="checkbox" checked={showDurations || false} onChange={showDurationsChanged} />
                    <span> stage durations</span>
                  </label>
                </div>
                <Tooltip value="Navigate to Pipeline Configuration">
                  <UISref
                    to="^.pipelineConfig"
                    params={{ application: app.name, pipelineId: execution.pipelineConfigId }}
                  >
                    <button
                      className="btn btn-sm btn-default single-execution-details__configure"
                      onClick={handleConfigureClicked}
                    >
                      <span className="glyphicon glyphicon-cog" />
                      <span className="visible-md-inline visible-lg-inline"> Configure</span>
                    </button>
                  </UISref>
                </Tooltip>
              </div>
            </div>
          </div>
        </div>
      )}
      {execution && (
        <div className="row">
          <div className="col-md-10 col-md-offset-1 executions">
            <Execution
              execution={execution}
              key={execution.id}
              application={app}
              pipelineConfig={null}
              standalone={true}
              showDurations={showDurations}
              onRerun={
                pipelineConfig &&
                (() => {
                  rerunExecution(execution);
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
