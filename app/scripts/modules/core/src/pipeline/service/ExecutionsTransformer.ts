import { Dictionary, find, findLast, get, has, maxBy, sortBy, uniq } from 'lodash';

import { Application } from '../../application';
import { ExecutionBarLabel } from '../config/stages/common/ExecutionBarLabel';
import { ExecutionMarkerIcon } from '../config/stages/common/ExecutionMarkerIcon';
import { IExecution, IExecutionStage, IExecutionStageSummary, IOrchestratedItem, IStage } from '../../domain';
import { OrchestratedItemTransformer } from '../../orchestratedItem/orchestratedItem.transformer';
import { Registry } from '../../registry';
import { duration } from '../../utils/timeFormatters';

export class ExecutionsTransformer {
  private static hiddenStageTypes = [
    'initialization',
    'pipelineInitialization',
    'waitForRequisiteCompletion',
    'determineTargetServerGroup',
  ];

  private static addDeploymentTargets(execution: IExecution): void {
    const targets: string[] = [];
    execution.stages.forEach((stage) => {
      const stageConfig = Registry.pipeline.getStageConfig(stage);
      if (stageConfig && stageConfig.accountExtractor) {
        targets.push(...stageConfig.accountExtractor(stage));
      }
    });
    execution.deploymentTargets = uniq(targets)
      .filter((a) => !!a)
      .sort();
  }

  private static siblingStageSorter(a: IOrchestratedItem, b: IOrchestratedItem): number {
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

  private static flattenStages(
    stages: IExecutionStage[],
    stage: IExecutionStage | IExecutionStageSummary,
  ): IExecutionStage[] {
    const stageSummary = stage as IExecutionStageSummary;
    if (stageSummary.groupStages) {
      stageSummary.groupStages.forEach((s) => this.flattenStages(stages, s));
      return stages;
    }
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

  private static flattenAndFilter(stage: IExecutionStage | IExecutionStageSummary): IExecutionStage[] {
    return this.flattenStages([], stage).filter(
      (s) => s.isFailed || (!this.hiddenStageTypes.includes(s.type) && s.initializationStage !== true),
    );
  }

  private static getCurrentStages(execution: IExecution) {
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
  private static setMasterStageStartTime(stages: IExecutionStage[], stage: IExecutionStageSummary): void {
    const allStartTimes = stages.filter((child) => child.startTime).map((child) => child.startTime);
    if (allStartTimes.length) {
      stage.startTime = Math.min(...allStartTimes);
    }
  }

  private static getCurrentStage<T extends IOrchestratedItem>(stages: T[]) {
    const lastStage = stages[stages.length - 1];
    const lastNotStartedStage = findLast<T>(stages, (childStage) => childStage.hasNotStarted);
    const lastFailedStage = findLast<T>(stages, (childStage) => childStage.isFailed);
    const lastRunningStage = findLast<T>(stages, (childStage) => childStage.isRunning);
    const lastCanceledStage = findLast<T>(stages, (childStage) => childStage.isCanceled);
    const lastStoppedStage = findLast<T>(stages, (childStage) => childStage.isStopped);
    return (
      lastRunningStage || lastFailedStage || lastStoppedStage || lastCanceledStage || lastNotStartedStage || lastStage
    );
  }

  private static cleanupLockStages(stages: IExecutionStage[]): IExecutionStage[] {
    const retainFirstOfType = (type: string, toRetain: IExecutionStage[]) => {
      let foundType = false;
      return toRetain.filter((s) => {
        if (s.type === type) {
          if (foundType) {
            return false;
          }
          foundType = true;
        }
        return true;
      });
    };

    return retainFirstOfType('releaseLock', retainFirstOfType('acquireLock', stages).reverse()).reverse();
  }

  private static transformStage(stage: IExecutionStageSummary): void {
    const stages = this.cleanupLockStages(this.flattenAndFilter(stage));
    if (!stages.length) {
      return;
    }

    if ((stage as IExecutionStageSummary).masterStage) {
      this.setMasterStageStartTime(stages, stage as IExecutionStageSummary);

      const currentStage = this.getCurrentStage(stages);
      stage.status = currentStage.status;

      // if a stage is running, ignore the endTime of the parent stage
      if (!currentStage.endTime) {
        delete stage.endTime;
      }

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

  private static cleanRequisiteStageRefIds(execution: IExecution, stageMap: Dictionary<IStage>): void {
    const { stages } = execution;
    // remove any invalid requisiteStageRefIds, set requisiteStageRefIds to empty for synthetic stages
    stages.forEach((stage) => {
      if (stage.context && stage.context.requisiteIds) {
        stage.context.requisiteIds = uniq(stage.context.requisiteIds);
      }
      stage.requisiteStageRefIds = uniq(stage.requisiteStageRefIds || []);
      stage.requisiteStageRefIds = stage.requisiteStageRefIds.filter((parentId) => !!stageMap[parentId]);
    });
  }

  private static getStagesMappedByRefId(execution: IExecution): Dictionary<IStage> {
    const map: Dictionary<IStage> = {};
    execution.stages.forEach((s) => (map[s.refId] = s));
    return map;
  }

  private static applyPhasesAndLink(execution: IExecution, stageMap: Dictionary<IStage>): void {
    const stages = execution.stages;
    let allPhasesResolved = true;

    stages.forEach((stage) => {
      let phaseResolvable = true;
      let phase = 0;
      // if there are no dependencies or it's a synthetic stage, set it to 0
      if (stage.phase === undefined && !stage.requisiteStageRefIds.length) {
        stage.phase = phase;
      } else {
        stage.requisiteStageRefIds.forEach((parentId) => {
          const parent = stageMap[parentId];
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
      this.applyPhasesAndLink(execution, stageMap);
    }
  }

  private static addStageWidths(execution: IExecution): void {
    execution.stageWidth = 100 / execution.stageSummaries.length + '%';
  }

  private static styleStage(stage: IExecutionStageSummary, styleStage?: IExecutionStageSummary): void {
    styleStage = styleStage || stage;
    const stageConfig = Registry.pipeline.getStageConfig(styleStage);
    if (stageConfig) {
      stage.labelComponent = stageConfig.executionLabelComponent || ExecutionBarLabel;
      stage.markerIcon = stageConfig.markerIcon || ExecutionMarkerIcon;
      stage.useCustomTooltip = !!stageConfig.useCustomTooltip;
      stage.extraLabelLines = stageConfig.extraLabelLines;
    }
  }

  private static findNearestBuildInfo(execution: IExecution): any {
    if (has(execution, 'trigger.buildInfo.number')) {
      return execution.trigger.buildInfo;
    }
    if (has(execution, 'trigger.parentExecution')) {
      return this.findNearestBuildInfo(execution.trigger.parentExecution);
    }
    return null;
  }

  public static addBuildInfo(execution: IExecution): void {
    execution.buildInfo = this.findNearestBuildInfo(execution);

    if (has(execution, 'trigger.buildInfo.lastBuild.number')) {
      execution.buildInfo = execution.trigger.buildInfo.lastBuild;
    }
    const deployStage = execution.stages.find((stage) => stage.type === 'deploy');
    // TODO - remove 'deploymentDetails || ...' once we've finalized the migration away from per-stage deploymentDetails
    const deploymentDetails =
      get<any>(deployStage, 'context.deploymentDetails') || get<any>(execution, 'context.deploymentDetails');
    if (deploymentDetails && deploymentDetails.length && deploymentDetails[0].jenkins) {
      const jenkins = deploymentDetails[0].jenkins;
      execution.buildInfo = {
        number: jenkins.number,
        url: deploymentDetails[0].buildInfoUrl || `${jenkins.host}job/${jenkins.name}/${jenkins.number}`,
      };
    }
  }

  private static transformStageSummary(summary: IExecutionStageSummary, index: number): void {
    summary.index = index;
    summary.stages = this.flattenAndFilter(summary);
    summary.stages.forEach((stage) => delete stage.stages);
    summary.masterStageIndex = summary.stages.includes(summary.masterStage)
      ? summary.stages.indexOf(summary.masterStage)
      : 0;
    this.filterStages(summary);
    this.setFirstActiveStage(summary);
    this.setSuspendedStageTypes(summary);
    this.transformStage(summary);
    this.styleStage(summary);
    OrchestratedItemTransformer.defineProperties(summary);

    if (summary.type === 'group') {
      summary.groupStages.forEach((stage, i) => this.transformStageSummary(stage, i));
    }
  }

  private static filterStages(summary: IExecutionStageSummary): void {
    const stageConfig = Registry.pipeline.getStageConfig(summary.masterStage);
    if (stageConfig && stageConfig.stageFilter) {
      summary.stages = summary.stages.filter((s) => stageConfig.stageFilter(s));
    }
  }

  private static setSuspendedStageTypes(summary: IExecutionStageSummary): void {
    summary.suspendedStageTypes = new Set(
      summary.stages.filter(({ isSuspended }) => isSuspended).map(({ type }) => type),
    );
  }

  private static setFirstActiveStage(summary: IExecutionStageSummary): void {
    summary.firstActiveStage = 0;
    const steps = summary.stages || [];
    if (steps.find((s) => s.isRunning)) {
      summary.firstActiveStage = steps.findIndex((s) => s.isRunning);
    }
    if (steps.find((s) => s.isFailed)) {
      summary.firstActiveStage = steps.findIndex((s) => s.isFailed);
    }
    if (steps.find((s) => s.isFailed && !!s.failureMessage)) {
      summary.firstActiveStage = steps.findIndex((s) => s.isFailed && !!s.failureMessage);
    }
  }

  public static transformExecution(application: Application, execution: IExecution): void {
    if (execution.trigger) {
      execution.isStrategy = execution.trigger.isPipeline === false && execution.trigger.type === 'pipeline';
    }
    const stageRefIdsMap = this.getStagesMappedByRefId(execution);
    this.cleanRequisiteStageRefIds(execution, stageRefIdsMap);
    this.applyPhasesAndLink(execution, stageRefIdsMap);
    Registry.pipeline.getExecutionTransformers().forEach((transformer) => {
      transformer.transform(application, execution);
    });

    execution.context = execution.context || {};
    execution.stages.forEach((stage, index) => {
      stage.before = stage.before || [];
      stage.after = stage.after || [];
      stage.index = index;
      OrchestratedItemTransformer.defineProperties(stage);
      if (stage.tasks && stage.tasks.length) {
        stage.tasks.forEach((t) => OrchestratedItemTransformer.addRunningTime(t));
      }
    });

    // Handle synthetic stages
    execution.stages.forEach((stage) => {
      const parent = find(execution.stages, { id: stage.parentStageId });
      if (parent) {
        if (stage.syntheticStageOwner === 'STAGE_BEFORE') {
          parent.before.push(stage);
        }
        if (stage.syntheticStageOwner === 'STAGE_AFTER') {
          parent.after.push(stage);
        }
      }
      const context = stage.context || {};
      stage.cloudProvider = context.cloudProvider || context.cloudProviderType;

      if (context.alias) {
        stage.alias = context.alias;
      }
    });

    OrchestratedItemTransformer.defineProperties(execution);
    this.processStageSummaries(execution);
    execution.graphStatusHash = ExecutionsTransformer.calculateGraphStatusHash(execution);
  }

  private static calculateGraphStatusHash(execution: IExecution): string {
    return (execution.stageSummaries || [])
      .map((stage) => {
        const stageConfig = Registry.pipeline.getStageConfig(stage);
        if (stageConfig && stageConfig.extraLabelLines) {
          return [stageConfig.extraLabelLines(stage), stage.status].join('-');
        }
        return stage.status;
      })
      .join(':');
  }

  private static calculateRunningTime(stage: IExecutionStageSummary): () => number {
    return () => {
      // Find the earliest startTime and latest endTime
      stage.groupStages.forEach((subStage) => {
        if (subStage.startTime && subStage.startTime < stage.startTime) {
          stage.startTime = subStage.startTime;
        }
        if (subStage.endTime && subStage.endTime > stage.endTime) {
          stage.endTime = subStage.endTime;
        }
      });

      if (!stage.startTime) {
        return null;
      }
      const normalizedNow: number = Math.max(Date.now(), stage.startTime);
      return (stage.endTime || normalizedNow) - stage.startTime;
    };
  }

  public static processStageSummaries(execution: IExecution): void {
    let stageSummaries: IExecutionStageSummary[] = [];
    execution.stages.forEach((stage) => {
      if (!stage.syntheticStageOwner && !this.hiddenStageTypes.includes(stage.type)) {
        // HACK: Orca sometimes (always?) incorrectly reports a parent stage as running when a child stage has stopped
        if (stage.status === 'RUNNING' && stage.after.some((s) => s.status === 'STOPPED')) {
          stage.status = 'STOPPED';
        }
        const context = stage.context || {};
        const summary: IExecutionStageSummary = {
          after: stage.after,
          before: stage.before,
          cloudProvider: stage.cloudProvider,
          comments: context.comments || null,
          endTime: stage.endTime,
          group: context.group,
          id: stage.id,
          index: undefined,
          graphRowOverride: context.graphRowOverride || 0,
          masterStage: stage,
          name: stage.name,
          refId: stage.refId,
          requisiteStageRefIds:
            stage.requisiteStageRefIds && stage.requisiteStageRefIds[0] === '*' ? [] : stage.requisiteStageRefIds || [],
          stages: [],
          startTime: stage.startTime,
          status: stage.status,
          type: stage.type,
        } as IExecutionStageSummary;
        stageSummaries.push(summary);
      }
    });

    const idToGroupIdMap: { [key: string]: number | string } = {};
    stageSummaries = stageSummaries.reduce((groupedStages, stage) => {
      // Since everything should already be sorted, if the stage is not in a group, just push it on and continue
      if (!stage.group) {
        groupedStages.push(stage);
        return groupedStages;
      }

      // The stage is in a group
      let groupedStage = groupedStages.find((s) => s.type === 'group' && s.name === stage.group);
      if (!groupedStage) {
        // Create a new grouped stage
        groupedStage = {
          activeStageType: undefined,
          after: undefined,
          before: stage.before,
          cloudProvider: stage.cloudProvider, // what if the group has two different cloud providers?
          comments: '',
          endTime: stage.endTime,
          groupStages: [],
          id: stage.group, // TODO: Can't key off group name because a partial 'group' can be used multiple times...
          index: undefined,
          masterStage: undefined,
          name: stage.group,
          refId: stage.group, // TODO: Can't key off group name because a partial 'group' can be used multiple times...
          requisiteStageRefIds:
            stage.requisiteStageRefIds && stage.requisiteStageRefIds[0] === '*' ? [] : stage.requisiteStageRefIds || [], // TODO: No idea what to do with refids...
          stages: [],
          startTime: stage.startTime,
          status: undefined,
          type: 'group',
        } as IExecutionStageSummary;
        groupedStages.push(groupedStage);
      }
      OrchestratedItemTransformer.defineProperties(stage);

      // Update the runningTimeInMs function to account for the group
      Object.defineProperties(groupedStage, {
        runningTime: {
          get: () => duration(this.calculateRunningTime(groupedStage)()),
          configurable: true,
        },
        runningTimeInMs: {
          get: this.calculateRunningTime(groupedStage),
          configurable: true,
        },
      });

      idToGroupIdMap[stage.refId] = groupedStage.refId;
      groupedStage.groupStages.push(stage);
      return groupedStages;
    }, [] as IExecutionStageSummary[]);

    stageSummaries.forEach((summary, index) => {
      // this shouldn't be necessary, but we had a few group stages slip in, so this handles it gracefully-ish
      if (summary.type === 'group' && !summary.groupStages) {
        summary.groupStages = [];
      }
      if (summary.type === 'group') {
        if (summary.groupStages.length === 1) {
          // If there's only one stage, get rid of the group.
          const onlyStage = summary.groupStages[0];
          summary = onlyStage;
          stageSummaries[index] = onlyStage;
          delete idToGroupIdMap[onlyStage.refId];
        } else if (summary.groupStages.length > 1) {
          const subComments: string[] = [];
          // Find the earliest startTime and latest endTime
          summary.groupStages.forEach((subStage) => {
            if (subStage.comments) {
              subComments.push(subStage.comments);
            }
          });

          // Assuming the last stage in the group has the "output" stages
          summary.after = summary.groupStages[summary.groupStages.length - 1].after;

          const currentStage = this.getCurrentStage(summary.groupStages);
          summary.activeStageType = currentStage.type;
          summary.status = currentStage.status;
          this.styleStage(summary, currentStage);

          // Set the group comment as a concatenation of all the stage summary comments
          summary.comments = subComments.join(', ');
        }
      }

      // Make sure the requisite ids that were pointing at stages within a group are now pointing at the group
      summary.requisiteStageRefIds = uniq(summary.requisiteStageRefIds.map((id) => idToGroupIdMap[id] || id));
    });

    stageSummaries.forEach((summary, index) => this.transformStageSummary(summary, index));
    execution.stageSummaries = stageSummaries;
    execution.currentStages = this.getCurrentStages(execution);
    this.addStageWidths(execution);
    this.addBuildInfo(execution);
    this.addDeploymentTargets(execution);
  }
}
