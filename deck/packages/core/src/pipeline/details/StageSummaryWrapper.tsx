import React from 'react';

import { REST } from '../../api';
import type { Application } from '../../application';
import type { IDeckRuntimeServicesInjectedProps } from '../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import { ConfirmationModalService } from '../../confirmationModal';
import type { IExecution, IExecutionStage, IExecutionStageSummary } from '../../domain';
import type { IStage } from '../../domain';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { Markdown } from '../../presentation/Markdown';
import { robotToHuman } from '../../presentation/robotToHumanFilter/robotToHuman.filter';
import { Registry } from '../../registry/Registry';
import { duration, timestamp } from '../../utils/timeFormatters';

export interface IStageSummaryWrapperProps {
  application: Application;
  execution: IExecution;
  sourceUrl: string;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}

export function StageSummaryWrapperComponent(
  props: IStageSummaryWrapperProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
) {
  const { application, deckRuntimeServices, execution, stage, stageSummary, stateParams, stateService } = props;
  const { executionService } = deckRuntimeServices;

  const renderStepLabel = (step: IStage) => {
    const StepLabelComponent = Registry.pipeline.getStageConfig(step)?.executionStepLabelComponent;
    return StepLabelComponent ? (
      <StepLabelComponent application={application} execution={execution} step={step} />
    ) : (
      <span className="task-label">{robotToHuman(step.name)}</span>
    );
  };

  const getCurrentStep = () => parseInt(stateParams.step, 10);
  const getTopLevelStage = (): IExecutionStage => {
    let parentStageId = stage.parentStageId;
    let topLevelStage = stage;
    while (parentStageId) {
      topLevelStage = execution.stages.find((candidate) => candidate.id === parentStageId);
      parentStageId = topLevelStage.parentStageId;
    }
    return topLevelStage;
  };

  const isRestartable = (candidate: IStage): boolean => {
    if (candidate.isRunning || candidate.isCompleted) {
      return false;
    }

    const stageConfig = Registry.pipeline.getStageConfig(candidate);
    if (!stageConfig || candidate.isRestarting === true) {
      return false;
    }

    const allowRestart = application.attributes.enableRestartRunningExecutions || false;
    if (execution.isRunning && !allowRestart) {
      return false;
    }

    return stageConfig.restartable || false;
  };

  const canManuallySkip = (): boolean => {
    const topLevelStage = getTopLevelStage();
    return stage.isRunning && topLevelStage && topLevelStage.context.canManuallySkip;
  };

  const restartStage = (): PromiseLike<void> => {
    return REST('/pipelines')
      .path(execution.id, 'stages', stage.id, 'restart')
      .put({ skip: false })
      .then(() => {
        stage.isRestarting = true;
      });
  };

  const openRestartStageModal = () => {
    let body = null;
    if (execution.isRunning) {
      body =
        '<p><strong>This pipeline is currently running - restarting this stage will result in multiple concurrently running pipelines.</strong></p>';
    }

    const concurrentExecutions = (application.executions?.data || []).filter(
      (candidate: IExecution) =>
        candidate.pipelineConfigId === execution.pipelineConfigId && candidate.status === 'RUNNING',
    );
    if (concurrentExecutions.length && execution.limitConcurrent) {
      body =
        '<p class="alert alert-warning"><i class="fa fa-exclamation-triangle sp-margin-xs-right"></i>This stage <strong>will not</strong> restart until the running execution completes since concurrency is disabled for this pipeline';
    }

    ConfirmationModalService.confirm({
      header: `Really restart ${stage.name}?`,
      buttonText: `Restart ${stage.name}`,
      body,
      submitMethod: restartStage,
    });
  };

  const openManualSkipStageModal = () => {
    const topLevelStage = getTopLevelStage();
    ConfirmationModalService.confirm({
      header: 'Really skip this stage?',
      buttonText: 'Skip',
      askForReason: true,
      submitJustWithReason: true,
      body: `<div class="alert alert-warning">
          <b>Warning:</b> Skipping this stage may have unpredictable results.
          <ul>
            <li>Mutating changes initiated by this stage will continue and will need to be cleaned up manually.</li>
            <li>Downstream stages that depend on the outputs of this stage may fail or behave unexpectedly.</li>
          </ul>
        </div>
      `,
      submitMethod: (reason: string) =>
        executionService
          .patchExecution(execution.id, topLevelStage.id, { manualSkip: true, reason })
          .then(() =>
            executionService.waitUntilExecutionMatches(execution.id, (updatedExecution) => {
              const updatedStage = updatedExecution.stages.find((candidate) => candidate.id === topLevelStage.id);
              return updatedStage && updatedStage.status === 'SKIPPED';
            }),
          )
          .then((updated) => executionService.updateExecution(application, updated)),
    });
  };

  const toggleDetails = (index: number) => {
    if (getCurrentStep() === index) {
      return;
    }

    const newState = { step: index } as any;
    const stageIndex = parseInt(stateParams.stage, 10);
    if (stageIndex) {
      newState.stage = stageIndex;
    }
    const subStage = parseInt(stateParams.subStage, 10);
    if (subStage) {
      newState.subStage = subStage;
    }
    stateService.go('.', newState);
  };

  const topLevelStage = getTopLevelStage();

  return (
    <div className="stage-summary-wrapper execution-details-summary">
      <h5 className="execution-details-title">
        Stage details: {stageSummary.name || stageSummary.type}
        {(isRestartable(stage) || canManuallySkip()) && (
          <div className="btn-group pull-right">
            {isRestartable(stage) && (
              <button className="btn btn-default btn-sm restart-stage" type="button" onClick={openRestartStageModal}>
                Restart {stageSummary.name}
              </button>
            )}
            {canManuallySkip() && (
              <button className="btn btn-default btn-sm manual-skip" type="button" onClick={openManualSkipStageModal}>
                Skip {topLevelStage.name}
              </button>
            )}
          </div>
        )}
      </h5>
      <h6 className="duration">Duration: {duration(stageSummary.runningTimeInMs)}</h6>
      {stage.context.restartDetails && (
        <h6>
          Restarted by {stage.context.restartDetails.restartedBy} &mdash;{' '}
          {timestamp(stage.context.restartDetails.restartTime)}
        </h6>
      )}
      {topLevelStage.context.manualSkip && (
        <h6 title={topLevelStage.context.reason}>
          Manually skipped by {topLevelStage.lastModified.user} &mdash;{' '}
          {timestamp(topLevelStage.lastModified.lastModifiedTime)}
        </h6>
      )}

      <table className="table">
        <thead>
          <tr>
            <th style={{ width: '30%' }}>Step</th>
            <th>Started</th>
            <th>Completed</th>
            <th>Duration</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {(stageSummary.stages || []).map((step, index) => (
            <tr
              className={`clickable${index === getCurrentStep() ? ' info' : ''}`}
              key={index}
              onClick={() => toggleDetails(index)}
            >
              <td>{renderStepLabel(step)}</td>
              <td>{timestamp(step.startTime)}</td>
              <td>{timestamp(step.endTime)}</td>
              <td>{duration(step.runningTimeInMs)}</td>
              <td>
                <span className={`label label-default label-${(step.status || '').toLowerCase()}`}>
                  {robotToHuman((step.status || '').toLowerCase())}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {stageSummary.comments && (
        <div className="execution-details-comments">
          <strong>Comments</strong>
          <Markdown message={stageSummary.comments + ''} />
        </div>
      )}
    </div>
  );
}

export const StageSummaryWrapper = withDeckRuntimeServices(withRouter(StageSummaryWrapperComponent));
