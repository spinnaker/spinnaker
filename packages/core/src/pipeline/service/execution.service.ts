import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { StateService } from '@uirouter/core';
import { IQService, ITimeoutService, module } from 'angular';
import { get, identity, pickBy, uniq } from 'lodash';

import { ExecutionsTransformer } from './ExecutionsTransformer';
import { REST } from '../../api/ApiService';
import { Application } from '../../application/application.model';
import { ApplicationDataSource } from '../../application/service/applicationDataSource';
import { PipelineConfigService } from '../config/services/PipelineConfigService';
import { SETTINGS } from '../../config/settings';
import { IExecution, IExecutionStage, IExecutionStageSummary } from '../../domain';
import { IPipeline } from '../../domain/IPipeline';
import { ExecutionFilterService } from '../filter/executionFilter.service';
import { FilterModelService, ISortFilter } from '../../filterModel';
import { ReactInjector } from '../../reactShims';
import { ExecutionState } from '../../state';
import { JsonUtils } from '../../utils';
import { DebugWindow } from '../../utils/consoleDebug';
import { IRetryablePromise, retryablePromise } from '../../utils/retryablePromise';

export class ExecutionService {
  public get activeStatuses(): string[] {
    return ['RUNNING', 'SUSPENDED', 'PAUSED', 'NOT_STARTED'];
  }
  private runningLimit = SETTINGS.maxRunningExecutionsToRetrieve || 30;

  private ignoredStringValFields = [
    'asg',
    'commits',
    'history',
    'hydrator',
    'hydrated',
    'instances',
    'requisiteIds',
    'requisiteStageRefIds',
    '$$hashKey',
  ];

  constructor(private $q: IQService, private $state: StateService, private $timeout: ITimeoutService) {}

  public getRunningExecutions(applicationName: string): PromiseLike<IExecution[]> {
    return this.getFilteredExecutions(applicationName, this.activeStatuses, this.runningLimit, null, true);
  }

  private getFilteredExecutions(
    applicationName: string,
    statuses: string[],
    limit: number,
    pipelineConfigIds: string[] = null,
    expand = false,
  ): PromiseLike<IExecution[]> {
    const statusString = statuses.map((status) => status.toUpperCase()).join(',') || null;
    const call = pipelineConfigIds
      ? REST('/executions').query({ limit, pipelineConfigIds, statuses }).get()
      : REST('/applications')
          .path(applicationName, 'pipelines')
          .query({ limit, statuses: statusString, pipelineConfigIds, expand })
          .get();

    return call.then((data: IExecution[]) => {
      if (data) {
        data.forEach((execution: IExecution) => {
          execution.hydrated = expand;
          return this.cleanExecutionForDiffing(execution);
        });
        return data;
      }
      return [];
    });
  }

  /**
   * Returns a filtered list of executions for the given application
   * @param {string} applicationName the name of the application
   * @param {Application} application: if supplied, and pipeline parameters are present on the filter model, the
   * application will be used to correlate and filter the retrieved executions to only include those pipelines
   * @param {boolean} expand: if true, the resulting executions will include fully hydrated context, outputs, and tasks
   * fields
   * @return {<IExecution[]>}
   */
  public getExecutions(
    applicationName: string,
    application: Application = null,
    expand = false,
  ): PromiseLike<IExecution[]> {
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    const tags = FilterModelService.getCheckValues(sortFilter.tags);
    const pipelines = Object.keys(sortFilter.pipeline);
    const statuses = Object.keys(pickBy(sortFilter.status || {}, identity));
    const limit = sortFilter.count;
    if (application && (pipelines.length || tags.length)) {
      return this.getConfigIdsFromFilterModel(application).then((pipelineConfigIds) => {
        return this.getFilteredExecutions(application.name, statuses, limit, pipelineConfigIds, expand);
      });
    }
    return this.getFilteredExecutions(applicationName, statuses, limit, null, expand);
  }

  public getExecution(executionId: string): PromiseLike<IExecution> {
    return REST('/pipelines')
      .path(executionId)
      .get()
      .then((execution: IExecution) => {
        const { application, name } = execution;
        execution.hydrated = true;
        this.cleanExecutionForDiffing(execution);
        if (application && name) {
          return REST('/applications')
            .path(application, 'pipelineConfigs', name)
            .get()
            .then((pipelineConfig: IPipeline) => {
              execution.pipelineConfig = pipelineConfig;
              return execution;
            })
            .catch(() => execution);
        }
        return execution;
      });
  }

  public transformExecutions(application: Application, executions: IExecution[], currentData: IExecution[] = []): void {
    if (!executions || !executions.length) {
      return;
    }
    executions.forEach((execution) => {
      const stringVal = this.stringifyExecution(execution);
      // do not transform if it hasn't changed
      const match = currentData.find((test: IExecution) => test.id === execution.id);
      if (!match || !match.stringVal || match.stringVal !== stringVal) {
        execution.stringVal = stringVal;
        ExecutionsTransformer.transformExecution(application, execution);
      }
    });
  }

  private getConfigIdsFromFilterModel(application: Application): PromiseLike<string[]> {
    const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    const tags = FilterModelService.getCheckValues(sortFilter.tags);
    const pipelines = Object.keys(sortFilter.pipeline);
    application.pipelineConfigs.activate();
    return application.pipelineConfigs.ready().then(() => {
      const data = application.pipelineConfigs.data.concat(application.strategyConfigs.data);
      const configIdsFromCheckedPipelines = pipelines
        .map((p) => {
          const match = data.find((c: IPipeline) => c.name === p);
          return match ? match.id : null;
        })
        .filter((id) => !!id);
      const configIdsFromCheckedTags = data
        .filter((p: IPipeline) => ExecutionFilterService.doesPipelineMatchCheckedTags(p, tags))
        .map((p: IPipeline) => p.id);
      return configIdsFromCheckedPipelines.concat(uniq(configIdsFromCheckedTags));
    });
  }

  private cleanExecutionForDiffing(execution: IExecution): void {
    (execution.stages || []).forEach((stage: IExecutionStage) => this.removeInstances(stage));
  }

  public toggleDetails(execution: IExecution, stageIndex: number, subIndex: number): void {
    const standalone = this.$state.current.name.endsWith('.executionDetails.execution');

    if (
      execution.id === this.$state.params.executionId &&
      this.$state.current.name.includes('.execution') &&
      stageIndex === undefined
    ) {
      this.$state.go('^');
      return;
    }

    const index = stageIndex || 0;
    let stageSummary = get<IExecutionStageSummary>(execution, ['stageSummaries', index]);
    if (stageSummary && stageSummary.type === 'group') {
      if (subIndex === undefined) {
        // Disallow clicking on a group itself
        return;
      }
      stageSummary = get<IExecutionStageSummary>(stageSummary, ['groupStages', subIndex]);
    }
    stageSummary = stageSummary || ({ firstActiveStage: 0 } as IExecutionStageSummary);

    const params = {
      executionId: execution.id,
      stage: index,
      subStage: subIndex,
      step: stageSummary.firstActiveStage,
    } as any;

    // Can't show details of a grouped stage
    if (subIndex === undefined && stageSummary.type === 'group') {
      params.stage = null;
      params.step = null;
      return;
    }

    if (this.$state.includes('**.execution', params)) {
      if (!standalone) {
        this.$state.go('^');
      }
    } else {
      if (this.$state.current.name.endsWith('.execution') || standalone) {
        this.$state.go('.', params);
      } else {
        this.$state.go('.execution', params);
      }
    }
  }

  // these fields are never displayed in the UI, so don't retain references to them, as they consume a lot of memory
  // on very large deployments
  private removeInstances(stage: IExecutionStage): void {
    if (stage.context) {
      delete stage.context.instances;
      delete stage.context.asg;
      if (stage.context.targetReferences) {
        stage.context.targetReferences.forEach((targetReference: any) => {
          delete targetReference.instances;
          delete targetReference.asg;
        });
      }
    }
  }

  public startAndMonitorPipeline(
    app: Application,
    pipeline: string,
    trigger: any,
  ): PromiseLike<IRetryablePromise<void>> {
    const { executionService } = ReactInjector;
    return PipelineConfigService.triggerPipeline(app.name, pipeline, trigger).then((triggerResult) =>
      executionService.waitUntilTriggeredPipelineAppears(app, triggerResult),
    );
  }

  public waitUntilTriggeredPipelineAppears(
    application: Application,
    triggeredPipelineId: string,
  ): IRetryablePromise<any> {
    const closure = () => this.getExecution(triggeredPipelineId).then(() => application.executions.refresh());
    return retryablePromise(closure, 1000, 10);
  }

  private waitUntilPipelineIsCancelled(application: Application, executionId: string): PromiseLike<any> {
    return this.waitUntilExecutionMatches(
      executionId,
      (execution: IExecution) => execution.status === 'CANCELED',
    ).then(() => application.executions.refresh());
  }

  private waitUntilPipelineIsDeleted(application: Application, executionId: string): PromiseLike<any> {
    const deferred = this.$q.defer();
    this.getExecution(executionId).then(
      () => this.$timeout(() => this.waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve), 1000),
      () => deferred.resolve(),
    );
    deferred.promise.then(() => application.executions.refresh());
    return deferred.promise;
  }

  public cancelExecution(
    application: Application,
    executionId: string,
    force?: boolean,
    reason?: string,
  ): PromiseLike<any> {
    return REST('/pipelines')
      .path(executionId, 'cancel')
      .query({ force, reason })
      .put()
      .then(() => this.waitUntilPipelineIsCancelled(application, executionId))
      .catch((exception) => {
        throw exception && exception.data ? exception.message : null;
      });
  }

  public pauseExecution(application: Application, executionId: string): PromiseLike<any> {
    return REST('/pipelines')
      .path(executionId, 'pause')
      .put()
      .then(() => this.waitUntilExecutionMatches(executionId, (execution) => execution.status === 'PAUSED'))
      .then(() => application.executions.refresh())
      .catch((exception) => {
        throw exception && exception.data ? exception.message : null;
      });
  }

  public resumeExecution(application: Application, executionId: string): PromiseLike<any> {
    return REST('/pipelines')
      .path(executionId, 'resume')
      .put()
      .then(() => this.waitUntilExecutionMatches(executionId, (execution) => execution.status === 'RUNNING'))
      .then(() => application.executions.refresh())
      .catch((exception) => {
        throw exception && exception.data ? exception.message : null;
      });
  }

  public deleteExecution(application: Application, executionId: string): PromiseLike<any> {
    const promiseLike = REST('/pipelines')
      .path(executionId)
      .delete()
      .then(() => this.waitUntilPipelineIsDeleted(application, executionId))
      .then(() => application.executions.refresh())
      .catch((exception) => {
        throw exception && exception.data ? exception.message : null;
      });
    return promiseLike;
  }

  public waitUntilExecutionMatches(
    executionId: string,
    matchPredicate: (execution: IExecution) => boolean,
  ): PromiseLike<IExecution> {
    return this.getExecution(executionId).then((execution) => {
      if (matchPredicate(execution)) {
        return execution;
      }
      return this.$timeout(() => this.waitUntilExecutionMatches(executionId, matchPredicate), 1000);
    });
  }

  public getSectionCacheKey(groupBy: string, application: string, heading: string): string {
    return ['pipeline', groupBy, application, heading].join('#');
  }

  public getProjectExecutions(project: string, limit = 1): PromiseLike<IExecution[]> {
    return REST('/projects')
      .path(project, 'pipelines')
      .query({ limit })
      .get()
      .then((executions: IExecution[]) => {
        if (!executions || !executions.length) {
          return [];
        }
        executions.forEach((execution) => ExecutionsTransformer.transformExecution({} as Application, execution));
        return executions.sort((a, b) => b.startTime - (a.startTime || Date.now()));
      });
  }

  public addExecutionsToApplication(application: Application, executions: IExecution[] = []): IExecution[] {
    // only add executions if we actually got some executions back
    // this will fail if there was just one execution and someone just deleted it
    // but that is much less likely at this point than orca falling over under load,
    // resulting in an empty list of executions coming back
    if (application.executions.data && application.executions.data.length && executions.length) {
      const existingData = application.executions.data;
      const runningData = application.runningExecutions.data;
      // remove any that have dropped off, update any that have changed
      const toRemove: number[] = [];
      existingData.forEach((execution: IExecution, idx: number) => {
        const match = executions.find((test) => test.id === execution.id);
        const runningMatch = runningData.find((t: IExecution) => t.id === execution.id);
        if (match) {
          if (execution.stringVal && match.stringVal && execution.stringVal !== match.stringVal) {
            if (execution.status !== match.status) {
              application.executions.data[idx] = match;
            } else {
              // don't dehydrate!
              if (execution.hydrated === match.hydrated) {
                application.executions.data[idx] = match;
              }
            }
          }
        }
        // if it's from the running executions, leave it alone
        if (!match && !runningMatch) {
          toRemove.push(idx);
        }
      });

      toRemove.reverse().forEach((idx) => existingData.splice(idx, 1));

      // add any new ones
      executions.forEach((execution) => {
        if (!existingData.filter((test: IExecution) => test.id === execution.id).length) {
          existingData.push(execution);
        }
      });
      return [...existingData];
    } else {
      return executions;
    }
  }

  // adds running execution data to the execution data source
  public mergeRunningExecutionsIntoExecutions(application: Application): void {
    let updated = false;
    application.runningExecutions.data.forEach((re: IExecution) => {
      const match = application.executions.data.findIndex((e: IExecution) => e.id === re.id);
      if (match !== -1) {
        const oldKey = application.executions.data[match].stringVal;
        if (re.stringVal !== oldKey) {
          updated = true;
          application.executions.data[match] = re;
        }
      } else {
        updated = true;
        application.executions.data.push(re);
      }
    });
    application.executions.data.forEach((execution: IExecution) => {
      if (execution.isActive && application.runningExecutions.data.every((e: IExecution) => e.id !== execution.id)) {
        this.getExecution(execution.id).then((updatedExecution) => {
          this.updateExecution(application, updatedExecution);
        });
      }
    });
    if (updated && !application.executions.reloadingForFilters) {
      application.executions.dataUpdated();
    }
  }

  // remove any running execution data if the execution is completed
  public removeCompletedExecutionsFromRunningData(application: Application): void {
    const data = application.executions.data;
    const runningData = application.runningExecutions.data;
    data.forEach((e: IExecution) => {
      const match = runningData.findIndex((re: IExecution) => e.id === re.id);
      if (match !== -1 && !e.isActive) {
        runningData.splice(match, 1);
      }
    });
    application.runningExecutions.dataUpdated();
  }

  public updateExecution(
    application: Application,
    updatedExecution: IExecution,
    dataSource: ApplicationDataSource<IExecution[]> = application.executions,
  ): void {
    if (dataSource.data && dataSource.data.length) {
      dataSource.data.forEach((currentExecution, idx) => {
        if (updatedExecution.id === currentExecution.id) {
          updatedExecution.stringVal = this.stringifyExecution(updatedExecution);
          if (
            updatedExecution.status !== currentExecution.status ||
            currentExecution.stringVal !== updatedExecution.stringVal
          ) {
            ExecutionsTransformer.transformExecution(application, updatedExecution);
            dataSource.data[idx] = updatedExecution;
            dataSource.dataUpdated();
          }
        }
      });
    }
  }

  /**
   * Fetches a fully hydrated execution, then assigns all its values to the supplied execution.
   * If the execution is already hydrated, the operation does not re-fetch the execution.
   *
   * If this method is called multiple times, only the first call performs the fetch;
   * subsequent calls will return the promise produced by the first call.
   *
   * This is a mutating operation - it fills the context, outputs, and tasks on the stages of the unhydrated execution.
   * @param application the application owning the execution; needed because the stupid
   *   transformExecution requires it.
   * @param unhydrated the execution to hydrate (which may already be hydrated)
   * @return a Promise, which resolves with the execution itself.
   */
  public hydrate(application: Application, unhydrated: IExecution): Promise<IExecution> {
    if (unhydrated.hydrator) {
      return unhydrated.hydrator;
    }
    if (unhydrated.hydrated) {
      return Promise.resolve(unhydrated);
    }
    const executionHydrator = this.getExecution(unhydrated.id).then((hydrated) => {
      ExecutionsTransformer.transformExecution(application, hydrated);
      unhydrated.stages.forEach((s, i) => {
        // stages *should* be in the same order, so getting the hydrated one by index should be fine.
        // worth verifying, though, and, if not, find the stage by id (which makes this an O(n^2) operation instead of O(n))
        const hydratedStage =
          hydrated.stages.length === unhydrated.stages.length && hydrated.stages[i].id === s.id
            ? hydrated.stages[i]
            : hydrated.stages.find((s2) => s.id === s2.id);
        if (hydratedStage) {
          s.context = hydratedStage.context;
          s.outputs = hydratedStage.outputs;
          s.tasks = hydratedStage.tasks;
        }
      });
      unhydrated.hydrated = true;
      unhydrated.graphStatusHash = hydrated.graphStatusHash;
      unhydrated.stageSummaries = hydrated.stageSummaries;
      return unhydrated;
    });
    unhydrated.hydrator = Promise.resolve(executionHydrator);
    return unhydrated.hydrator;
  }

  public getLastExecutionForApplicationByConfigId(appName: string, configId: string): PromiseLike<IExecution> {
    return this.getFilteredExecutions(appName, [], 1)
      .then((executions: IExecution[]) => {
        return executions.filter((execution) => {
          return execution.pipelineConfigId === configId;
        });
      })
      .then((executionsByConfigId) => {
        return executionsByConfigId[0];
      });
  }

  /**
   * Returns a list of recent executions for the supplied set of IDs, optionally filtered by status
   * @param {string[]} pipelineConfigIds the pipeline config IDs
   * @param {{limit?: number; statuses?: string; transform?: boolean; application?: Application}} options:
   *  transform: if true - and the application option is set, the execution transformer will run on each result (default: false)
   *  application: if transform is true, the application to use when transforming the executions (default: null)
   *  limit: the number of executions per config ID to retrieve (default: whatever Gate sets)
   *  statuses: an optional set of execution statuses (default: all)
   * @return {PromiseLike<IExecution[]>}
   */
  public getExecutionsForConfigIds(
    pipelineConfigIds: string[],
    options: { limit?: number; statuses?: string; transform?: boolean; application?: Application } = {},
  ): PromiseLike<IExecution[]> {
    const { limit, statuses, transform, application } = options;
    return REST('/executions')
      .query({ limit, pipelineConfigIds: (pipelineConfigIds || []).join(','), statuses })
      .get()
      .then((data: IExecution[]) => {
        if (data) {
          if (transform && application) {
            data.forEach((execution: IExecution) => ExecutionsTransformer.transformExecution(application, execution));
          }
          return data.sort((a, b) => (b.buildTime || 0) - (a.buildTime || 0));
        }
        return [];
      })
      .catch(() => [] as IExecution[]);
  }

  public patchExecution(executionId: string, stageId: string, data: any): PromiseLike<any> {
    return REST('/pipelines').path(executionId, 'stages', stageId).patch(data);
  }

  private stringifyExecution(execution: IExecution): string {
    const transient = { ...execution };
    transient.stages = transient.stages.filter((s) => s.status !== 'SUCCEEDED' && s.status !== 'NOT_STARTED');
    return this.stringify(transient);
  }

  private stringify(object: IExecution | IExecutionStageSummary): string {
    return JsonUtils.makeSortedStringFromAngularObject({ ...object }, this.ignoredStringValFields);
  }
}

export const EXECUTION_SERVICE = 'spinnaker.core.pipeline.executions.service';
module(EXECUTION_SERVICE, [UIROUTER_ANGULARJS]).factory('executionService', [
  '$q',
  '$state',
  '$timeout',
  ($q: IQService, $state: StateService, $timeout: ITimeoutService) => new ExecutionService($q, $state, $timeout),
]);

DebugWindow.addInjectable('executionService');
