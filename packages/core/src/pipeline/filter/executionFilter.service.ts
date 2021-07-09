import { chain, compact, forOwn, groupBy, includes, uniq } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { DateTime, Duration } from 'luxon';
import { $log } from 'ngimport';
import { Subject } from 'rxjs';

import { Application } from '../../application/application.model';
import { IExecution, IExecutionGroup, IPipeline, IPipelineTag } from '../../domain';
import { FilterModelService, ISortFilter } from '../../filterModel';
import { Registry } from '../../registry';
import { ExecutionState } from '../../state';

const boundaries = [
  {
    name: 'Today',
    after: () => DateTime.local().startOf('day').toMillis(),
  },
  {
    name: 'Yesterday',
    after: () =>
      DateTime.local()
        .startOf('day')
        .minus(Duration.fromObject({ days: 1 }))
        .toMillis(),
  },
  {
    name: 'This Week',
    after: () => DateTime.local().startOf('week').toMillis(),
  },
  {
    name: 'Last Week',
    after: () =>
      DateTime.local()
        .startOf('week')
        .minus(Duration.fromObject({ weeks: 1 }))
        .toMillis(),
  },
  {
    name: 'Last Month',
    after: () => DateTime.local().startOf('month').toMillis(),
  },
  {
    name: 'This Year',
    after: () => DateTime.local().startOf('year').toMillis(),
  },
  { name: 'Prior Years', after: () => 0 },
];

export class ExecutionFilterService {
  public static groupsUpdatedStream: Subject<IExecutionGroup[]> = new Subject<IExecutionGroup[]>();

  private static lastApplication: Application = null;
  private static isFilterable: (sortFilterModel: { [key: string]: boolean }) => boolean =
    FilterModelService.isFilterable;

  private static groupByTimeBoundary(executions: IExecution[]): { [boundaryName: string]: IExecution[] } {
    return groupBy(
      executions,
      (execution) =>
        boundaries.find(
          (boundary) =>
            // executions that were cancelled before ever starting will not have a startTime, just a buildTime
            (execution.startTime || execution.buildTime) > boundary.after(),
        ).name,
    );
  }

  @Debounce(25)
  public static updateExecutionGroups(application: Application = this.lastApplication): void {
    if (!application) {
      return null;
    }
    const executions = application.executions.data || [];
    executions.forEach((execution: IExecution) => this.fixName(execution, application));
    const filtered: IExecution[] = this.filterExecutionsForDisplay(application.executions.data, application);

    const groups = this.groupExecutions(filtered, application);
    this.applyGroupsToModel(groups);

    ExecutionState.filterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.groupsUpdatedStream.next(groups);
  }

  public static doesPipelineMatchCheckedTags(config: IPipeline, checkedTags: string[]): boolean {
    if (checkedTags.length === 0 || !config.tags || config.tags.length === 0) {
      return false;
    }
    const decoded: IPipelineTag[] = checkedTags
      .map((encoded) => encoded.split(':').map(decodeURIComponent))
      .map(([name, value]) => ({ name, value }));
    const grouped = groupBy(decoded, 'name');
    const groups = Object.keys(grouped);
    // We use .every() to logically AND the different tags
    return groups.every((group) => {
      const checkedValues = grouped[group].map((tag) => tag.value);
      const relevantValues = (config.tags || []).filter((tag) => tag.name === group).map((tag) => tag.value);
      return checkedValues.some((checkedValue) => relevantValues.includes(checkedValue));
    });
  }

  private static tagsFilter(execution: IExecution, application: Application): boolean {
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    if (this.isFilterable(sortFilter.tags)) {
      const checkedPipelines = application.pipelineConfigs.data.filter((config: IPipeline) =>
        this.doesPipelineMatchCheckedTags(config, FilterModelService.getCheckValues(sortFilter.tags)),
      );
      return includes(
        checkedPipelines.map((config: IPipeline) => config.id),
        execution.pipelineConfigId,
      );
    } else {
      return true;
    }
  }

  private static pipelineNameFilter(execution: IExecution): boolean {
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    if (this.isFilterable(sortFilter.pipeline)) {
      const checkedPipelineNames = FilterModelService.getCheckValues(sortFilter.pipeline);
      return includes(checkedPipelineNames, execution.name);
    } else {
      return true;
    }
  }

  private static getValuesAsString(object: any, denylist: string[] = []): string {
    if (typeof object === 'string') {
      return object;
    }
    if (typeof object === 'number') {
      return '' + object;
    }
    if (object instanceof Array) {
      return object.map((val) => this.getValuesAsString(val, denylist)).join(' ');
    }
    if (object instanceof Object) {
      return Object.keys(object)
        .map((key) => {
          if (denylist.includes(key)) {
            return '';
          }
          return this.getValuesAsString(object[key], denylist);
        })
        .join(' ');
    }
    return '';
  }

  private static addSearchText(execution: IExecution): void {
    if (execution.searchField) {
      return;
    }
    const searchText = [execution.name];
    searchText.push(execution.id);
    searchText.push(this.getValuesAsString(execution.trigger, ['parentExecution']));

    execution.searchField = searchText.join(' ').toLowerCase();
  }

  private static textFilter(execution: IExecution): boolean {
    const filter = ExecutionState.filterModel.asFilterModel.sortFilter.filter.toLowerCase();
    if (!filter) {
      return true;
    }
    this.addSearchText(execution);
    return execution.searchField.includes(filter);
  }

  private static statusFilter(execution: IExecution): boolean {
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    if (this.isFilterable(sortFilter.status)) {
      const checkedStatus = FilterModelService.getCheckValues(sortFilter.status);
      return includes(checkedStatus, execution.status);
    } else {
      return true;
    }
  }

  private static awaitingJudgementFilter(execution: IExecution): boolean {
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    if (sortFilter.awaitingJudgement) {
      return execution.stages.some(({ type, status }) => type === 'manualJudgment' && status === 'RUNNING');
    } else {
      return true;
    }
  }

  public static filterExecutionsForDisplay(executions: IExecution[], application: Application): IExecution[] {
    return chain(executions)
      .filter((e: IExecution) => this.textFilter(e))
      .filter((e: IExecution) => this.tagsFilter(e, application))
      .filter((e: IExecution) => this.pipelineNameFilter(e))
      .filter((e: IExecution) => this.statusFilter(e))
      .filter((e: IExecution) => this.awaitingJudgementFilter(e))
      .value();
  }

  private static addEmptyPipelines(groups: IExecutionGroup[], application: Application): void {
    const configs = (application.pipelineConfigs.data || []).concat(application.strategyConfigs.data || []);
    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    const groupNames: { [key: string]: any } = {};
    groups.forEach((g) => (groupNames[g.heading] = true));
    let toAdd = [];
    if (
      !this.isFilterable(sortFilter.pipeline) &&
      !this.isFilterable(sortFilter.status) &&
      !sortFilter.filter &&
      !this.isFilterable(sortFilter.tags)
    ) {
      toAdd = configs.filter((config: any) => !groupNames[config.name]);
    } else {
      toAdd = configs.filter((config: any) => {
        const filterMatches = (sortFilter.filter || '').toLowerCase().includes(config.name.toLowerCase());
        const tagsMatch = this.doesPipelineMatchCheckedTags(config, FilterModelService.getCheckValues(sortFilter.tags));
        return !groupNames[config.name] && (sortFilter.pipeline[config.name] || filterMatches || tagsMatch);
      });
    }

    toAdd.forEach((config: any) => {
      groups.push({
        heading: config.name,
        config,
        executions: [],
        targetAccounts: this.extractAccounts(config),
        fromTemplate: (config && config.type === 'templatedPipeline') || false,
      });
    });
  }

  private static extractAccounts(config: IPipeline): string[] {
    if (!config) {
      return [];
    }
    const configAccounts: string[] = [];
    (config.stages || []).forEach((stage) => {
      const stageConfig = Registry.pipeline.getStageConfig(stage);
      if (stageConfig && stageConfig.configAccountExtractor) {
        configAccounts.push(...stageConfig.configAccountExtractor(stage));
      }
    });
    return uniq(compact(configAccounts)).filter((a) => !a.includes('${')); // exclude parameterized accounts
  }

  private static fixName(execution: IExecution, application: Application): void {
    const config: IPipeline = application.pipelineConfigs.data.find(
      (p: IPipeline) => p.id === execution.pipelineConfigId,
    );
    if (config) {
      execution.name = config.name;
    }
  }

  private static groupExecutions(filteredExecutions: IExecution[], application: Application): IExecutionGroup[] {
    const groups: IExecutionGroup[] = [];
    let executions: IExecution[] = [];
    forOwn(groupBy(filteredExecutions, 'name'), (groupedExecutions) => {
      executions = executions.concat(groupedExecutions.sort((a, b) => this.executionSorter(a, b)));
    });

    executions.forEach((execution: IExecution) => {
      const config: IPipeline = application.pipelineConfigs.data.find(
        (p: IPipeline) => p.id === execution.pipelineConfigId,
      );
      if (config != null && config.type === 'templatedPipeline') {
        execution.fromTemplate = true;
      }
    });

    const sortFilter: ISortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;

    if (sortFilter.groupBy === 'name') {
      const executionGroups = groupBy(executions, 'name');
      forOwn(executionGroups, (groupExecutions, key) => {
        const matchId = (pipelineConfig: IPipeline) => pipelineConfig.id === groupExecutions[0].pipelineConfigId;
        const config = application.pipelineConfigs.data.concat(application.strategyConfigs?.data ?? []).find(matchId);
        groupExecutions.sort((a, b) => this.executionSorter(a, b));
        groups.push({
          heading: key,
          config: config || null,
          executions: groupExecutions,
          runningExecutions: groupExecutions.filter((execution: IExecution) => execution.isActive),
          targetAccounts: this.extractAccounts(config),
          fromTemplate: (config && config.type === 'templatedPipeline') || false,
        });
      });
      this.addEmptyPipelines(groups, application);
    }

    if (sortFilter.groupBy === 'timeBoundary') {
      const grouped = this.groupByTimeBoundary(executions);
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

    if (sortFilter.groupBy === 'none') {
      executions.sort((a, b) => this.executionSorter(a, b));
      groups.push({
        heading: '',
        executions,
        runningExecutions: [],
      });
    }

    return groups;
  }

  private static diffExecutionGroups(oldGroups: IExecutionGroup[], newGroups: IExecutionGroup[]): IExecutionGroup[] {
    const diffedGroups: IExecutionGroup[] = [];
    newGroups.forEach((newGroup) => {
      const oldGroup = oldGroups.find((g) => g.heading === newGroup.heading);
      if (!oldGroup) {
        diffedGroups.push(newGroup);
      } else {
        if (this.executionsAreDifferent(oldGroup, newGroup)) {
          diffedGroups.push(newGroup);
        } else {
          diffedGroups.push(oldGroup);
        }
      }
    });
    oldGroups.forEach((group) => group.executions.sort((a, b) => this.executionSorter(a, b)));
    return diffedGroups;
  }

  private static executionsAreDifferent(oldGroup: IExecutionGroup, newGroup: IExecutionGroup): boolean {
    let changeDetected = false;
    oldGroup.executions.forEach((execution) => {
      const newExecution = newGroup.executions.find((g) => g.id === execution.id);
      if (!newExecution) {
        changeDetected = true;
        $log.debug('execution no longer found, removing:', execution.id);
      } else {
        if (execution.stringVal !== newExecution.stringVal) {
          changeDetected = true;
          $log.debug('change detected, updating execution:', execution.id);
        }
      }
    });
    newGroup.executions.forEach((execution) => {
      const oldExecution = oldGroup.executions.find((g) => g.id === execution.id);
      if (!oldExecution) {
        changeDetected = true;
        $log.debug('new execution found, adding', execution.id);
        oldGroup.executions.push(execution);
      }
    });
    return changeDetected;
  }

  private static applyGroupsToModel(groups: IExecutionGroup[]): void {
    const filterModel = ExecutionState.filterModel.asFilterModel;
    filterModel.groups = this.diffExecutionGroups(
      filterModel.groups,
      groups,
    ).sort((a: IExecutionGroup, b: IExecutionGroup) => this.executionGroupSorter(a, b));
  }

  public static executionGroupSorter(a: IExecutionGroup, b: IExecutionGroup): number {
    if (ExecutionState.filterModel.asFilterModel.sortFilter.groupBy === 'timeBoundary') {
      return (
        (b.executions[0].startTime || b.executions[0].buildTime) -
        (a.executions[0].startTime || a.executions[0].buildTime)
      );
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

  private static executionSorter(a: IExecution, b: IExecution): number {
    if (a.isActive && b.isActive) {
      return b.startTime - a.startTime;
    }
    if (a.isActive) {
      return -1;
    }
    if (b.isActive) {
      return 1;
    }
    if (!a.endTime && !b.endTime) {
      return b.buildTime - a.buildTime;
    }
    if (!a.endTime) {
      return -1;
    }
    if (!b.endTime) {
      return 1;
    }
    return b.startTime - a.startTime;
  }

  public static clearFilters(): void {
    ExecutionState.filterModel.asFilterModel.clearFilters();
    ExecutionState.filterModel.asFilterModel.applyParamsToUrl();
  }
}
