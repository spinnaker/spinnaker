
import { ILogService, module } from 'angular';
import { chain, compact, debounce, find, flattenDeep, forOwn, get, groupBy, includes, uniq } from 'lodash';

import { Application } from 'core/application/application.model';
import { EXECUTION_FILTER_MODEL, ExecutionFilterModel } from 'core/delivery/filter/executionFilter.model';
import { IExecution, IExecutionGroup, IPipeline } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';

export class ExecutionFilterService {
  private lastApplication: Application = null;
  private isFilterable: (sortFilterModel: any[]) => boolean;

  // this gets called every time the URL changes, so we debounce it a tiny bit
  public updateExecutionGroups: (application: Application) => IExecutionGroup[];

  constructor(private executionFilterModel: ExecutionFilterModel,
              private timeBoundaries: any,
              private $log: ILogService,
              private filterModelService: any,
              private pipelineConfig: any) {
    'ngInject';
    this.isFilterable = filterModelService.isFilterable;

    this.updateExecutionGroups = debounce((application: Application) => {
      if (!application) {
        application = this.lastApplication;
        if (!this.lastApplication) {
          return null;
        }
      }
      const executions = application.executions.data || [];
      executions.forEach((execution: IExecution) => this.fixName(execution, application));
      const filtered: IExecution[] = this.filterExecutionsForDisplay(application.executions.data);

      const groups = this.groupExecutions(filtered, application);

      this.applyGroupsToModel(groups);
      this.executionFilterModel.asFilterModel.addTags();
      this.lastApplication = application;
      executionFilterModel.groupsUpdated.next();
      return groups;
    }, 25);
  }

  private pipelineNameFilter(execution: IExecution): boolean {
    if (this.isFilterable(this.executionFilterModel.asFilterModel.sortFilter.pipeline)) {
      const checkedPipelineNames = this.filterModelService.getCheckValues(this.executionFilterModel.asFilterModel.sortFilter.pipeline);
      return includes(checkedPipelineNames, execution.name);
    } else {
      return true;
    }
  }

  private getValuesAsString(object: any, blacklist: string[] = []): string {
    if (typeof object === 'string') {
      return object;
    }
    if (typeof object === 'number') {
      return '' + object;
    }
    if (object instanceof Array) {
      return object.map((val) => this.getValuesAsString(val, blacklist)).join(' ');
    }
    if (object instanceof Object) {
      return Object.keys(object).map((key) => {
        if (blacklist.includes(key)) {
          return '';
        }
        return this.getValuesAsString(object[key], blacklist);
      }).join(' ');
    }
    return '';
  }

  private addSearchText(execution: IExecution): void {
    if (execution.searchField) {
      return;
    }
    const searchText = [execution.name];
    searchText.push(execution.id);
    searchText.push(this.getValuesAsString(execution.appConfig));
    searchText.push(this.getValuesAsString(execution.trigger));
    execution.stages.forEach((stage) => searchText.push(this.getValuesAsString(stage.context, ['commits', 'jarDiffs', 'kato.tasks'])));
    execution.searchField = searchText.join(' ').toLowerCase();
  }

  private textFilter(execution: IExecution): boolean {
    const filter = this.executionFilterModel.asFilterModel.sortFilter.filter.toLowerCase();
    if (!filter) {
      return true;
    }
    this.addSearchText(execution);
    return execution.searchField.includes(filter);
  }

  private statusFilter(execution: IExecution): boolean {
    if (this.isFilterable(this.executionFilterModel.asFilterModel.sortFilter.status)) {
      const checkedStatus = this.filterModelService.getCheckValues(this.executionFilterModel.asFilterModel.sortFilter.status);
      return includes(checkedStatus, execution.status);
    } else {
      return true;
    }
  }

  public filterExecutionsForDisplay(executions: IExecution[]): IExecution[] {
    return chain(executions)
      .filter((e: IExecution) => this.textFilter(e))
      .filter((e: IExecution) => this.pipelineNameFilter(e))
      .filter((e: IExecution) => this.statusFilter(e))
      .value();
  }

  private addEmptyPipelines(groups: IExecutionGroup[], application: Application): void {
    const configs = application.pipelineConfigs.data || [];
    if (!this.isFilterable(this.executionFilterModel.asFilterModel.sortFilter.pipeline) &&
      !this.isFilterable(this.executionFilterModel.asFilterModel.sortFilter.status) &&
      !this.executionFilterModel.asFilterModel.sortFilter.filter) {
      configs
        .filter((config: any) => !groups[config.name])
        .forEach((config: any) => groups.push({heading: config.name, config: config, executions: [], targetAccounts: this.extractAccounts(config)}));
    } else {
      configs
        .filter((config: any) => !groups[config.name] && this.executionFilterModel.asFilterModel.sortFilter.pipeline[config.name])
        .forEach((config: any) => {
          groups.push({heading: config.name, config: config, executions: [], targetAccounts: this.extractAccounts(config)});
        });
    }
  }

  private extractAccounts(config: IPipeline): string[] {
    const configAccounts: string[] = [];
    (config.stages || []).forEach(stage => {
      const stageConfig = this.pipelineConfig.getStageConfig(stage);
      if (stageConfig && stageConfig.configAccountExtractor) {
        configAccounts.push(...stageConfig.configAccountExtractor(stage));
      }
    });
    return uniq(compact(flattenDeep(configAccounts))).filter(a => !a.includes('${')); // exclude parameterized accounts
  }

  private fixName(execution: IExecution, application: Application): void {
    const config: IPipeline = find<{id: string}, IPipeline>(application.pipelineConfigs.data, { id: execution.pipelineConfigId });
    if (config) {
      execution.name = config.name;
    }
  }

  private groupExecutions(filteredExecutions: IExecution[], application: Application): IExecutionGroup[] {
    const groups: IExecutionGroup[] = [];
    // limit based on sortFilter.count
    let executions: IExecution[] = [];
    forOwn(groupBy(filteredExecutions, 'name'), (groupedExecutions) => {
      executions = executions.concat(groupedExecutions.sort((a, b) => this.executionSorter(a, b)).slice(0, this.executionFilterModel.asFilterModel.sortFilter.count));
    });

    if (this.executionFilterModel.asFilterModel.sortFilter.groupBy === 'name') {
      const executionGroups = groupBy(executions, 'name');
      forOwn(executionGroups, (groupExecutions, key) => {
        const matchId = (config: IPipeline) => config.id === groupExecutions[0].pipelineConfigId;
        const config = application.pipelineConfigs.data.find(matchId) || get(application, 'strategyConfigs.data', []).find(matchId);
        groupExecutions.sort((a, b) => this.executionSorter(a, b));
        groups.push({
          heading: key,
          config: config || null,
          executions: groupExecutions,
          runningExecutions: groupExecutions.filter((execution: IExecution) => execution.isActive),
        });
      });
      this.addEmptyPipelines(groups, application);
    }

    if (this.executionFilterModel.asFilterModel.sortFilter.groupBy === 'timeBoundary') {
      const grouped = this.timeBoundaries.groupByTimeBoundary(executions);
      forOwn(grouped, (groupExecutions: IExecution[], key) => {
        groupExecutions.sort((a, b) => this.executionSorter(a, b));
        groups.push({
          heading: key,
          config: null,
          executions: groupExecutions,
          runningExecutions: groupExecutions.filter((execution: IExecution) => execution.isActive),
        });
      });
    }

    if (this.executionFilterModel.asFilterModel.sortFilter.groupBy === 'none') {
      executions.sort((a, b) => this.executionSorter(a, b));
      groups.push({
        heading: '',
        executions: executions,
        runningExecutions: [],
      });
    }

    return groups;
  }

  private diffExecutionGroups(oldGroups: IExecutionGroup[], newGroups: IExecutionGroup[]): void {
    const groupsToRemove: number[] = [];

    oldGroups.forEach((oldGroup, idx) => {
      const newGroup = find(newGroups, { heading: oldGroup.heading });
      if (!newGroup) {
        groupsToRemove.push(idx);
      } else {
        oldGroup.runningExecutions = newGroup.runningExecutions;
        oldGroup.config = newGroup.config;
        this.diffExecutions(oldGroup, newGroup);
      }
    });
    groupsToRemove.reverse().forEach((idx) => {
      oldGroups.splice(idx, 1);
    });
    newGroups.forEach((newGroup) => {
      const match = find(oldGroups, { heading: newGroup.heading });
      if (!match) {
        oldGroups.push(newGroup);
      }
    });
    oldGroups.forEach((group) => group.executions.sort((a, b) => this.executionSorter(a, b)));
  }

  private diffExecutions(oldGroup: IExecutionGroup, newGroup: IExecutionGroup): void {
    const toRemove: number[] = [];
    oldGroup.executions.forEach((execution, idx) => {
      const newExecution = find(newGroup.executions, { id: execution.id });
      if (!newExecution) {
        this.$log.debug('execution no longer found, removing:', execution.id);
        toRemove.push(idx);
      } else {
        if (execution.stringVal !== newExecution.stringVal) {
          this.$log.debug('change detected, updating execution:', execution.id);
          oldGroup.executions[idx] = newExecution;
        }
      }
    });
    toRemove.reverse().forEach((idx) => {
      oldGroup.executions.splice(idx, 1);
    });
    newGroup.executions.forEach((execution) => {
      const oldExecution = find(oldGroup.executions, { id: execution.id });
      if (!oldExecution) {
        this.$log.debug('new execution found, adding', execution.id);
        oldGroup.executions.push(execution);
      }
    });
  }

  private applyGroupsToModel(groups: IExecutionGroup[]): void {
    this.diffExecutionGroups(this.executionFilterModel.asFilterModel.groups, groups);

    // sort groups in place so Angular doesn't try to update the world
    this.executionFilterModel.asFilterModel.groups.sort((a: IExecutionGroup, b: IExecutionGroup) => this.executionGroupSorter(a, b));
  }

  public executionGroupSorter(a: IExecutionGroup, b: IExecutionGroup): number {
    if (this.executionFilterModel.asFilterModel.sortFilter.groupBy === 'timeBoundary') {
      return b.executions[0].startTime - a.executions[0].startTime;
    }
    if (a.config && b.config) {
      if (a.config.strategy === b.config.strategy) {
        return a.config.index - b.config.index;
      }
      return a.config.strategy ? 1 : -1;
    }
    if (a.config) {
      return -1;
    }
    if (b.config) {
      return 1;
    }
    if (a.heading < b.heading) {
      return -1;
    }
    if (a.heading > b.heading) {
      return 1;
    }
    return 0;
  }

  private executionSorter(a: IExecution, b: IExecution): number {
    if (a.isActive && b.isActive) {
      return b.startTime - a.startTime;
    }
    if (a.isActive) {
      return -1;
    }
    if (b.isActive) {
      return 1;
    }
    return b.endTime - a.endTime;
  }

  public clearFilters(): void {
    this.executionFilterModel.asFilterModel.clearFilters();
    this.executionFilterModel.asFilterModel.applyParamsToUrl();
  }
}

export const EXECUTION_FILTER_SERVICE = 'spinnaker.core.delivery.filter.executionFilter.service';
module (EXECUTION_FILTER_SERVICE, [
  EXECUTION_FILTER_MODEL,
  require('core/filterModel/filter.model.service'),
  require('core/orchestratedItem/timeBoundaries.service'),
  PIPELINE_CONFIG_PROVIDER
]).factory('executionFilterService', (executionFilterModel: ExecutionFilterModel, timeBoundaries: any, $log: ILogService, filterModelService: any, pipelineConfig: any) =>
                                      new ExecutionFilterService(executionFilterModel, timeBoundaries, $log, filterModelService, pipelineConfig));
