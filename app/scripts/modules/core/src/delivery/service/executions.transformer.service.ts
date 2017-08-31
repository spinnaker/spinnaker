import { module } from 'angular';

import { find, findLast, flattenDeep, get, has, maxBy, uniq, sortBy } from 'lodash';

import { Application } from 'core/application';
import { ExecutionBarLabel } from 'core/pipeline/config/stages/core/ExecutionBarLabel';
import { ExecutionMarkerIcon } from 'core/pipeline/config/stages/core/ExecutionMarkerIcon';
import { IExecution, IExecutionStage, IExecutionStageSummary, IOrchestratedItem } from 'core/domain';
import { OrchestratedItemTransformer } from 'core/orchestratedItem/orchestratedItem.transformer';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export class ExecutionsTransformerService {
  private hiddenStageTypes = ['initialization', 'pipelineInitialization', 'waitForRequisiteCompletion', 'determineTargetServerGroup'];

  constructor(private pipelineConfig: PipelineConfigProvider) {
    'ngInject';
  }

  private addDeploymentTargets(execution: IExecution): void {
    const targets: string[] = [];
    execution.stages.forEach((stage) => {
      const stageConfig = this.pipelineConfig.getStageConfig(stage);
      if (stageConfig && stageConfig.accountExtractor) {
        targets.push(stageConfig.accountExtractor(stage));
      }
    });
    execution.deploymentTargets = uniq(flattenDeep(targets)).sort();
  }

  private siblingStageSorter(a: IOrchestratedItem, b: IOrchestratedItem): number {
    if (!a.startTime && !b.startTime) {
      return 0;
    }
    if (!a.startTime) {
      return 1;
    }
    if (!b.startTime) {
      return -1;
    }
    return a.startTime - b.startTime;
  }

  private flattenStages(stages: IExecutionStage[], stage: IExecutionStage | IExecutionStageSummary): IExecutionStage[] {
    if (stage.before && stage.before.length) {
      stage.before.sort((a, b) => this.siblingStageSorter(a, b));
      stage.before.forEach((beforeStage) => this.flattenStages(stages, beforeStage));
    }
    if ((stage as IExecutionStageSummary).masterStage) {
      stages.push((stage as IExecutionStageSummary).masterStage);
    } else {
      stages.push(stage as IExecutionStage);
    }
    if (stage.after && stage.after.length) {
      stage.after.sort((a, b) => this.siblingStageSorter(a, b));
      stage.after.forEach((afterStage) => this.flattenStages(stages, afterStage));
    }
    return stages;
  }

  private flattenAndFilter(stage: IExecutionStage | IExecutionStageSummary): IExecutionStage[] {
    return this.flattenStages([], stage)
      .filter(s => s.isFailed ||
        (!this.hiddenStageTypes.includes(s.type) && s.initializationStage !== true));
  }

  private getCurrentStages(execution: IExecution) {
    const currentStages = execution.stageSummaries.filter((stage) => stage.isRunning);
    // if there are no running stages, find the first enqueued stage
    if (!currentStages.length) {
      const enqueued = execution.stageSummaries.find((stage) => stage.hasNotStarted);
      if (enqueued) {
        currentStages.push(enqueued);
      }
    }
    return currentStages;
  }

  // TODO: remove if we ever figure out why quickPatchAsgStage never has a startTime
  private setMasterStageStartTime(stages: IExecutionStage[], stage: IExecutionStageSummary): void {
    const allStartTimes = stages
      .filter((child) => child.startTime)
      .map((child) => child.startTime);
    if (allStartTimes.length) {
      stage.startTime = Math.min(...allStartTimes);
    }
  }

  private transformStage(stage: IExecutionStageSummary): void {
    const stages = this.flattenAndFilter(stage);
    if (!stages.length) {
      return;
    }

    if ((stage as IExecutionStageSummary).masterStage) {
      const lastStage = stages[stages.length - 1];
      this.setMasterStageStartTime(stages, stage as IExecutionStageSummary);

      const lastNotStartedStage = findLast<IExecutionStage>(stages, (childStage) => childStage.hasNotStarted);
      const lastFailedStage = findLast<IExecutionStage>(stages, (childStage) => childStage.isFailed);
      const lastRunningStage = findLast<IExecutionStage>(stages, (childStage) => childStage.isRunning);
      const lastCanceledStage = findLast<IExecutionStage>(stages, (childStage) => childStage.isCanceled);
      const lastStoppedStage = findLast<IExecutionStage>(stages, (childStage) => childStage.isStopped);
      const currentStage = lastRunningStage || lastFailedStage || lastStoppedStage || lastCanceledStage || lastNotStartedStage || lastStage;

      stage.status = currentStage.status;

      // if a stage is running, ignore the endTime of the parent stage
      if (!currentStage.endTime) { delete stage.endTime; }

      const lastEndingStage = maxBy<IExecutionStage>(stages, 'endTime');
      // if the current stage has an end time (i.e. it failed or completed), use the maximum end time
      // of all the child stages as the end time for the parent - we do this because the parent might
      // have been an initialization stage, which ends within a few milliseconds
      if (currentStage.endTime && lastEndingStage.endTime) {
        stage.endTime = Math.max(currentStage.endTime, lastEndingStage.endTime, stage.endTime);
      }
    }

    stage.stages = stages;
  }

  private applyPhasesAndLink(execution: IExecution): void {
    const stages = execution.stages;
    let allPhasesResolved = true;
    // remove any invalid requisiteStageRefIds, set requisiteStageRefIds to empty for synthetic stages
    stages.forEach((stage) => {
      if (has(stage, 'context.requisiteIds')) {
        stage.context.requisiteIds = uniq(stage.context.requisiteIds);
      }
      stage.requisiteStageRefIds = uniq(stage.requisiteStageRefIds || []);
      stage.requisiteStageRefIds = stage.requisiteStageRefIds.filter((parentId) => find(stages, { refId: parentId }));
    });

    stages.forEach((stage) => {
      let phaseResolvable = true;
      let phase = 0;
      // if there are no dependencies or it's a synthetic stage, set it to 0
      if (stage.phase === undefined && !stage.requisiteStageRefIds.length) {
        stage.phase = phase;
      } else {
        stage.requisiteStageRefIds.forEach((parentId) => {
          const parent = find(stages, { refId: parentId });
          if (!parent || parent.phase === undefined) {
            phaseResolvable = false;
          } else {
            phase = Math.max(phase, parent.phase);
          }
        });
        if (phaseResolvable) {
          stage.phase = phase + 1;
        } else {
          allPhasesResolved = false;
        }
      }
    });
    execution.stages = sortBy(stages, 'phase', 'refId');
    if (!allPhasesResolved) {
      this.applyPhasesAndLink(execution);
    }
  }

  private addStageWidths(execution: IExecution): void {
    execution.stageWidth = 100 / execution.stageSummaries.length + '%';
  }

  private styleStage(stage: IExecutionStageSummary): void {
    const stageConfig = this.pipelineConfig.getStageConfig(stage);
    if (stageConfig) {
      stage.labelComponent = stageConfig.executionLabelComponent || ExecutionBarLabel;
      stage.markerIcon = stageConfig.markerIcon || ExecutionMarkerIcon;
      stage.useCustomTooltip = !!stageConfig.useCustomTooltip;
      stage.extraLabelLines = stageConfig.extraLabelLines;
    }
  }

  private findNearestBuildInfo(execution: IExecution): any {
    if (has(execution, 'trigger.buildInfo.number')) {
      return execution.trigger.buildInfo;
    }
    if (has(execution, 'trigger.parentExecution')) {
      return this.findNearestBuildInfo(execution.trigger.parentExecution);
    }
    return null;
  }

  private addBuildInfo(execution: IExecution): void {
    execution.buildInfo = this.findNearestBuildInfo(execution);

    if (has(execution, 'trigger.buildInfo.lastBuild.number')) {
      execution.buildInfo = execution.trigger.buildInfo.lastBuild;
    }
    const deployStage = execution.stages.find((stage) => stage.type === 'deploy');
    // TODO - remove 'deploymentDetails || ...' once we've finalized the migration away from per-stage deploymentDetails
    const deploymentDetails = get<any>(deployStage, 'context.deploymentDetails') || get<any>(execution, 'context.deploymentDetails');
    if (deploymentDetails && deploymentDetails.length && deploymentDetails[0].jenkins) {
      const jenkins = deploymentDetails[0].jenkins;
      execution.buildInfo = {
        number: jenkins.number,
        url: deploymentDetails[0].buildInfoUrl || `${jenkins.host}job/${jenkins.name}/${jenkins.number}`
      };
    }
  }

  private transformStageSummary(summary: IExecutionStageSummary, index: number): void {
    summary.index = index;
    summary.stages = this.flattenAndFilter(summary);
    summary.stages.forEach((stage) => delete stage.stages);
    summary.masterStageIndex = summary.stages.includes(summary.masterStage) ? summary.stages.indexOf(summary.masterStage) : 0;
    this.filterStages(summary);
    this.setFirstActiveStage(summary);
    this.setExecutionWindow(summary);
    this.transformStage(summary);
    this.styleStage(summary);
    OrchestratedItemTransformer.defineProperties(summary);
  }

  private filterStages(summary: IExecutionStageSummary): void {
    const stageConfig = this.pipelineConfig.getStageConfig(summary.masterStage);
    if (stageConfig && stageConfig.stageFilter) {
      summary.stages = summary.stages.filter((s) => stageConfig.stageFilter(s));
    }
  }

  private setExecutionWindow(summary: IExecutionStageSummary): void {
    if (summary.stages.some(s => s.type === 'restrictExecutionDuringTimeWindow' && s.isSuspended)) {
      summary.inSuspendedExecutionWindow = true;
    }
  }

  private setFirstActiveStage(summary: IExecutionStageSummary): void {
    summary.firstActiveStage = 0;
    const steps = summary.stages || [];
    if (steps.find(s => s.isRunning)) {
      summary.firstActiveStage = steps.findIndex(s => s.isRunning);
    }
    if (steps.find(s => s.isFailed)) {
      summary.firstActiveStage = steps.findIndex(s => s.isFailed);
    }
  }

  public transformExecution(application: Application, execution: IExecution): void {
    if (execution.trigger) {
      execution.isStrategy = execution.trigger.isPipeline === false && execution.trigger.type === 'pipeline';
    }
    this.applyPhasesAndLink(execution);
    this.pipelineConfig.getExecutionTransformers().forEach((transformer) => {
      transformer.transform(application, execution);
    });
    const stageSummaries: IExecutionStageSummary[] = [];

    execution.context = execution.context || {};
    execution.stages.forEach((stage, index) => {
      stage.before = stage.before || [];
      stage.after = stage.after || [];
      stage.index = index;
      OrchestratedItemTransformer.defineProperties(stage);
      if (stage.tasks && stage.tasks.length) {
        stage.tasks.forEach(t => OrchestratedItemTransformer.addRunningTime(t));
      }
    });

    execution.stages.forEach((stage) => {
      const context = stage.context || {};
      const owner = stage.syntheticStageOwner;
      const parent = find(execution.stages, { id: stage.parentStageId });
      if (parent) {
        if (owner === 'STAGE_BEFORE') {
          parent.before.push(stage);
        }
        if (owner === 'STAGE_AFTER') {
          parent.after.push(stage);
        }
      }
      stage.cloudProvider = context.cloudProvider || context.cloudProviderType;
    });

    execution.stages.forEach((stage) => {
      if (!stage.syntheticStageOwner && !this.hiddenStageTypes.includes(stage.type)) {
        // HACK: Orca sometimes (always?) incorrectly reports a parent stage as running when a child stage has stopped
        if (stage.status === 'RUNNING' && stage.after.some(s => s.status === 'STOPPED')) {
          stage.status = 'STOPPED';
        }
        const context = stage.context || {};
        const summary: IExecutionStageSummary = {
          name: stage.name,
          id: stage.id,
          startTime: stage.startTime,
          endTime: stage.endTime,
          masterStage: stage,
          type: stage.type,
          before: stage.before,
          after: stage.after,
          status: stage.status,
          comments: context.comments || null,
          cloudProvider: stage.cloudProvider,
          refId: stage.refId,
          requisiteStageRefIds: stage.requisiteStageRefIds && stage.requisiteStageRefIds[0] === '*' ? [] : stage.requisiteStageRefIds || [],
          stages: [],
          index: undefined
        } as IExecutionStageSummary;
        stageSummaries.push(summary);
      }
    });

    OrchestratedItemTransformer.defineProperties(execution);

    stageSummaries.forEach((summary, index) => this.transformStageSummary(summary, index));
    execution.stageSummaries = stageSummaries;
    execution.currentStages = this.getCurrentStages(execution);
    this.addStageWidths(execution);
    this.addBuildInfo(execution);
    this.addDeploymentTargets(execution);
  }
}

export const EXECUTIONS_TRANSFORMER_SERVICE = 'spinnaker.core.delivery.executionTransformer.service';
module(EXECUTIONS_TRANSFORMER_SERVICE, [
  PIPELINE_CONFIG_PROVIDER,
]).service('executionsTransformer', (pipelineConfig: PipelineConfigProvider) =>
                                      new ExecutionsTransformerService(pipelineConfig));
