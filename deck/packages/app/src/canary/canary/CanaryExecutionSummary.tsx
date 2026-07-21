import React from 'react';

import type { IStageSummaryProps } from '@spinnaker/core';
import {
  AngularServices,
  ConfirmationModalService,
  duration,
  Markdown,
  Registry,
  REST,
  timestamp,
} from '@spinnaker/core';

import { EndCanaryModal } from './actions/EndCanaryModal';
import { GenerateScoreModal } from './actions/GenerateScoreModal';

function canShowCanaryActions(status: string) {
  return status === 'LAUNCHED' || status === 'RUNNING' || status === 'DISABLED';
}

export function CanaryExecutionSummary({ application, execution, stage, stageSummary }: IStageSummaryProps) {
  const canaryId = stageSummary.masterStage.context.canary?.id;
  const status = stageSummary.masterStage.context.canary?.status?.status;
  const isRestartable = (summaryStage: any) => {
    const stageConfig = Registry.pipeline.getStageConfig(summaryStage);
    return !!stageConfig && summaryStage.isRestarting !== true && !!stageConfig.restartable;
  };
  const getCurrentStep = () => parseInt(AngularServices.$stateParams.step, 10);
  const isStepCurrent = (index: number) => index === getCurrentStep();
  const toggleDetails = (index: number) => {
    const newStepDetails = getCurrentStep() === index ? null : index;
    if (newStepDetails !== null) {
      const newState = { step: newStepDetails } as any;
      const selectedStage = parseInt(AngularServices.$stateParams.stage, 10);
      if (selectedStage) {
        newState.stage = selectedStage;
      }
      const subStage = parseInt(AngularServices.$stateParams.subStage, 10);
      if (subStage) {
        newState.subStage = subStage;
      }
      AngularServices.$state.go('.', newState);
    }
  };
  const restartStage = () => {
    let body = null;
    if (execution.isRunning) {
      body =
        '<p><strong>This pipeline is currently running - restarting this stage will result in multiple concurrently running pipelines.</strong></p>';
    }

    const concurrentExecutions = application.executions.data.filter(
      (candidate: any) => candidate.pipelineConfigId === execution.pipelineConfigId && candidate.status === 'RUNNING',
    );
    if (concurrentExecutions.length && execution.limitConcurrent) {
      body =
        '<p class="alert alert-warning"><i class="fa fa-exclamation-triangle sp-margin-xs-right"></i>This stage <strong>will not</strong> restart until the running execution completes since concurrency is disabled for this pipeline';
    }

    ConfirmationModalService.confirm({
      header: `Really restart ${stage.name}?`,
      buttonText: `Restart ${stage.name}`,
      body,
      submitMethod: () =>
        REST('/pipelines')
          .path(execution.id, 'stages', stage.id, 'restart')
          .put({ skip: false })
          .then(() => {
            stage.isRestarting = true;
          }),
    });
  };

  return (
    <div>
      <h5 className="execution-details-title">
        {stageSummary.name || stageSummary.type} Details
        {canShowCanaryActions(status) && (
          <div className="btn-group pull-right">
            <button
              type="button"
              className="btn btn-default btn-sm dropdown-toggle"
              data-toggle="dropdown"
              aria-expanded="false"
            >
              Actions <span className="caret" />
            </button>
            <ul className="dropdown-menu" role="menu">
              <li>
                <a onClick={() => GenerateScoreModal.show({ canaryId })}>Generate Canary Result</a>
              </li>
              <li>
                <a onClick={() => EndCanaryModal.show({ canaryId })}>End Canary</a>
              </li>
            </ul>
          </div>
        )}
        {!stage.isRunning && !stage.isCompleted && isRestartable(stage) && (
          <div className="btn-group pull-right">
            <button
              type="button"
              className="btn btn-default btn-sm dropdown-toggle"
              data-toggle="dropdown"
              aria-expanded="false"
            >
              Actions <span className="caret" />
            </button>
            <ul className="dropdown-menu" role="menu">
              <li>
                <a onClick={restartStage}>Restart {stageSummary.name}</a>
              </li>
            </ul>
          </div>
        )}
      </h5>
      <h6 className="duration">Duration: {duration(stageSummary.runningTimeInMs)}</h6>
      <table className="table canary-summary">
        <thead>
          <tr>
            <th style={{ width: '40%' }}>Deployment</th>
            <th>Started</th>
            <th>Completed</th>
            <th>Running Time</th>
          </tr>
        </thead>
        <tbody>
          <tr
            className={`clickable ${isStepCurrent(stageSummary.masterStageIndex) ? 'info' : ''}`}
            onClick={() => toggleDetails(stageSummary.masterStageIndex)}
          >
            <td>
              {stageSummary.masterStage.exceptions?.length ? (
                <span className="glyphicon glyphicon-exclamation-sign" />
              ) : null}{' '}
              <strong>Canary Summary</strong>
            </td>
            <td>{timestamp(stageSummary.startTime)}</td>
            <td>{timestamp(stageSummary.endTime)}</td>
            <td>{duration(stageSummary.runningTimeInMs)}</td>
          </tr>
          {stageSummary.stages.map((summaryStage: any, index: number) =>
            summaryStage.type === 'canaryDeployment' || summaryStage.type === 'monitorAcaTask' ? (
              <tr
                className={`clickable canary-deployment-row ${isStepCurrent(index) ? 'info' : ''}`}
                key={index}
                onClick={() => toggleDetails(index)}
              >
                <td>{summaryStage.name}</td>
                <td>{timestamp(summaryStage.startTime)}</td>
                <td>{timestamp(stageSummary.endTime)}</td>
                <td>{duration(summaryStage.runningTimeInMs)}</td>
              </tr>
            ) : null,
          )}
        </tbody>
      </table>
      {stageSummary.comments && (
        <div>
          <strong>Comments</strong>
          <Markdown message={stageSummary.comments} />
        </div>
      )}
    </div>
  );
}
