import {IHttpService, IPromise, IQService, ITimeoutService, module} from 'angular';
import {identity, pickBy} from 'lodash';

import {API_SERVICE, Api} from 'core/api/api.service';
import {Application} from 'core/application/application.model';
import {EXECUTION_FILTER_MODEL, ExecutionFilterModel} from 'core/delivery/filter/executionFilter.model';
import {IExecution} from 'core/domain/IExecution';
import {IExecutionStage} from 'core/domain/IExecutionStage';
import {SETTINGS} from 'core/config/settings';

export class ExecutionService {
  private activeStatuses: string[] = ['RUNNING', 'SUSPENDED', 'PAUSED', 'NOT_STARTED'];
  private runningLimit = 30;

  static get $inject(): string[] { return ['$http', '$q', '$timeout', 'API', 'executionFilterModel', 'executionsTransformer', 'pipelineConfig']; }

  constructor(private $http: IHttpService,
              private $q: IQService,
              private $timeout: ITimeoutService,
              private API: Api,
              private executionFilterModel: ExecutionFilterModel,
              private executionsTransformer: any,
              private pipelineConfig: any) {}

    public getRunningExecutions(applicationName: string): IPromise<IExecution[]> {
      return this.getFilteredExecutions(applicationName, {statuses: this.activeStatuses, limit: this.runningLimit});
    }

    private getFilteredExecutions(applicationName: string, {statuses = Object.keys(pickBy(this.executionFilterModel.sortFilter.status || {}, identity)), limit = this.executionFilterModel.sortFilter.count} = {}): IPromise<IExecution[]> {
      const statusString = statuses.map((status) => status.toUpperCase()).join(',') || null;
      return this.API.one('applications', applicationName).all('pipelines').getList({ limit: limit, statuses: statusString})
        .then((data: IExecution[]) => {
          if (data) {
            data.forEach((execution: IExecution) => this.cleanExecutionForDiffing(execution));
            return data;
          }
          return [];
        });
    }

    public getExecutions(applicationName: string): IPromise<IExecution[]> {
      return this.getFilteredExecutions(applicationName);
    }

    public getExecution(executionId: string): IPromise<IExecution> {
      return this.API.one('pipelines', executionId).get()
        .then((execution: IExecution) => {
          this.cleanExecutionForDiffing(execution);
          return execution;
        });
    }

    public transformExecution(application: Application, execution: IExecution): void {
      this.executionsTransformer.transformExecution(application, execution);
    }

    public transformExecutions(application: Application, executions: IExecution[]): void {
      if (!executions || !executions.length) {
        return;
      }
      executions.forEach((execution) => {
        const stringVal = JSON.stringify(execution, this.jsonReplacer);
        // do not transform if it hasn't changed
        const match = (application.executions.data || []).find((test: IExecution) => test.id === execution.id);
        if (!match || !match.stringVal || match.stringVal !== stringVal) {
          execution.stringVal = stringVal;
          this.executionsTransformer.transformExecution(application, execution);
        }
      });
    }

    private cleanExecutionForDiffing(execution: IExecution): void {
      (execution.stages || []).forEach((stage: IExecutionStage) => this.removeInstances(stage));
      if (execution.trigger && execution.trigger.parentExecution) {
        (execution.trigger.parentExecution.stages || []).forEach((stage: IExecutionStage) => this.removeInstances(stage));
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

    // remove these fields - they are not of interest when determining if the pipeline has changed
    private jsonReplacer(key: string, value: any): any {
      if (key === 'instances' || key === 'asg' || key === 'commits' || key === 'history' || key === '$$hashKey') {
        return undefined;
      }
      return value;
    }

    public waitUntilNewTriggeredPipelineAppears(application: Application, pipelineName: string, triggeredPipelineId: string): IPromise<any> {
      return this.getRunningExecutions(application.name).then((executions: IExecution[]) => {
        const match = executions.find((execution) => execution.id === triggeredPipelineId);
        const deferred = this.$q.defer();
        if (match) {
          application.executions.refresh().then(deferred.resolve);
          return deferred.promise;
        } else {
          return this.$timeout(function() {
            return this.waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId);
          }, 1000);
        }
      });
    }

    private waitUntilPipelineIsCancelled(application: Application, executionId: string): IPromise<any> {
      return this.waitUntilExecutionMatches(executionId, (execution: IExecution) => execution.status === 'CANCELED')
        .then(() => application.executions.refresh());
    }

    private waitUntilPipelineIsDeleted(application: Application, executionId: string): IPromise<any> {
      const deferred = this.$q.defer();
      this.getExecution(executionId).then(
        () => this.$timeout(() => this.waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve), 1000),
        () => deferred.resolve()
      );
      deferred.promise.then(() => application.executions.refresh());
      return deferred.promise;
    }

    public cancelExecution(application: Application, executionId: string, force: boolean, reason: string): IPromise<any> {
      const deferred = this.$q.defer();
      this.$http({
        method: 'PUT',
        url: [
          SETTINGS.gateUrl,
          'applications',
          application.name,
          'pipelines',
          executionId,
          'cancel',
        ].join('/'),
        params: {
          force: force,
          reason: reason
        }
      }).then(
        () => this.waitUntilPipelineIsCancelled(application, executionId).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
      );
      return deferred.promise;
    }

    public pauseExecution(application: Application, executionId: string): IPromise<any> {
      const deferred = this.$q.defer();
      const matcher = (execution: IExecution) => {
        return execution.status === 'PAUSED';
      };

      this.$http({
        method: 'PUT',
        url: [
          SETTINGS.gateUrl,
          'pipelines',
          executionId,
          'pause',
        ].join('/')
      }).then(
        () => this.waitUntilExecutionMatches(executionId, matcher).then(() => application.executions.refresh()).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
    );
      return deferred.promise;
    }

    public resumeExecution(application: Application, executionId: string): IPromise<any> {
      const deferred = this.$q.defer();
      const matcher = (execution: IExecution) => {
        return execution.status === 'RUNNING';
      };

      this.$http({
        method: 'PUT',
        url: [
          SETTINGS.gateUrl,
          'pipelines',
          executionId,
          'resume',
        ].join('/')
      }).then(
        () => this.waitUntilExecutionMatches(executionId, matcher).then(() => application.executions.refresh()).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
    );
      return deferred.promise;
    }

    public deleteExecution(application: Application, executionId: string): IPromise<any> {
      const deferred = this.$q.defer();
      this.$http({
        method: 'DELETE',
        url: [
          SETTINGS.gateUrl,
          'pipelines',
          executionId,
        ].join('/')
      }).then(
        () => this.waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.data.message : null)
      );
      return deferred.promise;
    }

    public waitUntilExecutionMatches(executionId: string, closure: (execution: IExecution) => boolean): IPromise<IExecution> {
      return this.getExecution(executionId).then(
        (execution) => {
          if (closure(execution)) {
            return execution;
          }
          return this.$timeout(() => this.waitUntilExecutionMatches(executionId, closure), 1000);
        }
      );
    }

    public getSectionCacheKey(groupBy: string, application: string, heading: string): string {
      return ['pipeline', groupBy, application, heading].join('#');
    }

    public getProjectExecutions(project: string, limit = 1): IPromise<IExecution[]> {
      return this.API.one('projects', project).all('pipelines').getList({ limit: limit })
        .then((executions: IExecution[]) => {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach((execution) => this.executionsTransformer.transformExecution({}, execution));
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
        // remove any that have dropped off, update any that have changed
        const toRemove: number[] = [];
        existingData.forEach((execution: IExecution, idx: number) => {
          const match = executions.find((test) => test.id === execution.id);
          if (!match) {
            toRemove.push(idx);
          } else {
            if (execution.stringVal && match.stringVal && execution.stringVal !== match.stringVal) {
              if (execution.status !== match.status) {
                application.executions.data[idx] = match;
              } else {
                this.synchronizeExecution(execution, match);
              }
            }
          }
        });

        toRemove.reverse().forEach((idx) => existingData.splice(idx, 1));

        // add any new ones
        executions.forEach((execution) => {
          if (!existingData.filter((test: IExecution) => test.id === execution.id).length) {
            existingData.push(execution);
          }
        });
        return existingData;
      } else {
        return executions;
      }
    }

    public synchronizeExecution(current: IExecution, updated: IExecution): void {
      (updated.stageSummaries || []).forEach((updatedSummary, idx) => {
        const currentSummary = current.stageSummaries[idx];
        if (!currentSummary) {
          current.stageSummaries.push(updatedSummary);
        } else {
          // if the stage was not already completed, update it in place if it has changed to save Angular
          // from removing, then re-rendering every DOM node
          if (!updatedSummary.isComplete || !current.isComplete) {
            if (JSON.stringify(current, this.jsonReplacer) !== JSON.stringify(updatedSummary, this.jsonReplacer)) {
              Object.assign(currentSummary, updatedSummary);
            }
          }
        }
      });
      current.stringVal = updated.stringVal;
      current.graphStatusHash = this.calculateGraphStatusHash(current);
    }

    private calculateGraphStatusHash(execution: IExecution): string {
      return (execution.stageSummaries || []).map(stage => {
        const stageConfig = this.pipelineConfig.getStageConfig(stage);
        if (stageConfig && stageConfig.extraLabelLines) {
          return [stageConfig.extraLabelLines(stage), stage.status].join('-');
        }
        return stage.status;
      }).join(':');
    }

    public updateExecution(application: Application, updatedExecution: IExecution): void {
      if (application.executions.data && application.executions.data.length) {
        application.executions.data.forEach((currentExecution: IExecution, idx: number) => {
          if (updatedExecution.id === currentExecution.id) {
            updatedExecution.stringVal = JSON.stringify(updatedExecution, this.jsonReplacer);
            if (updatedExecution.status !== currentExecution.status) {
              this.transformExecution(application, updatedExecution);
              application.executions.data[idx] = updatedExecution;
              application.executions.dataUpdated();
            } else {
              if (currentExecution.stringVal !== updatedExecution.stringVal) {
                this.transformExecution(application, updatedExecution);
                this.synchronizeExecution(currentExecution, updatedExecution);
                application.executions.dataUpdated();
              }
            }
          }
        });
      }
    }

    public getLastExecutionForApplicationByConfigId(appName: string, configId: string): IPromise<IExecution> {
      return this.getFilteredExecutions(appName, {})
        .then((executions: IExecution[]) => {
          return executions.filter((execution) => {
            return execution.pipelineConfigId === configId;
          });
        })
        .then((executionsByConfigId) => {
          return executionsByConfigId[0];
        });
    }

    public patchExecution(executionId: string, stageId: string, data: any): IPromise<any> {
      const targetUrl = [SETTINGS.gateUrl, 'pipelines', executionId, 'stages', stageId].join('/');
      const request = {
        method: 'PATCH',
        url: targetUrl,
        data: data,
        timeout: SETTINGS.pollSchedule * 2 + 5000
      };
      return this.$http(request).then(resp => resp.data);
    }
}

export let executionService: ExecutionService = undefined;
export const EXECUTION_SERVICE = 'spinnaker.core.delivery.executions.service';
module(EXECUTION_SERVICE, [
  EXECUTION_FILTER_MODEL,
  require('./executions.transformer.service.js'),
  require('core/pipeline/config/pipelineConfigProvider.js'),
  API_SERVICE
]).factory('executionService', ($http: IHttpService, $q: IQService, $timeout: ITimeoutService, API: Api, executionFilterModel: any, executionsTransformer: any, pipelineConfig: any) =>
                                new ExecutionService($http, $q, $timeout, API, executionFilterModel, executionsTransformer, pipelineConfig))
  .run(($injector: any) => executionService = <ExecutionService>$injector.get('executionService'));
