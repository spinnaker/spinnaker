import { chain, Dictionary, forOwn, groupBy, intersection, sortBy, values } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { Subject } from 'rxjs';

import { Application } from '../../application/application.model';
import { IFunction, IFunctionGroup } from '../../domain';
import { FilterModelService } from '../../filterModel';
import { FunctionState } from '../../state';

export class FunctionFilterService {
  public groupsUpdatedStream: Subject<IFunctionGroup[]> = new Subject<IFunctionGroup[]>();

  private lastApplication: Application;

  constructor() {}

  private addSearchFields(functionDef: IFunction): void {
    if (!functionDef.searchField) {
      functionDef.searchField = [functionDef.functionName, functionDef.region.toLowerCase(), functionDef.account].join(
        ' ',
      );
    }
  }

  private checkSearchTextFilter(functionDef: IFunction): boolean {
    const filter = FunctionState.filterModel.asFilterModel.sortFilter.filter;
    if (!filter) {
      return true;
    }

    if (filter.includes('vpc:')) {
      const [, vpcName] = /vpc:([\w-]*)/.exec(filter);
      return functionDef.vpcName.toLowerCase() === vpcName.toLowerCase();
    }
    this.addSearchFields(functionDef);
    return filter.split(' ').every((testWord: string) => {
      return functionDef.searchField.includes(testWord);
    });
  }

  public filterFunctionsForDisplay(functions: IFunction[]): IFunction[] {
    return chain(functions)
      .filter((fn) => this.checkSearchTextFilter(fn))
      .filter((fn) => FilterModelService.checkAccountFilters(FunctionState.filterModel.asFilterModel)(fn))
      .filter((fn) => FilterModelService.checkRegionFilters(FunctionState.filterModel.asFilterModel)(fn))
      .filter((fn) => FilterModelService.checkProviderFilters(FunctionState.filterModel.asFilterModel)(fn))
      .value();
  }

  public sortGroupsByHeading(): void {
    // sort groups in place so Angular doesn't try to update the world
    FunctionState.filterModel.asFilterModel.groups.sort((a, b) => {
      return a.heading.localeCompare(b.heading);
    });
  }

  public clearFilters(): void {
    FunctionState.filterModel.asFilterModel.clearFilters();
    FunctionState.filterModel.asFilterModel.applyParamsToUrl();
  }

  public getFunctionGroups(groupedByAccount: Dictionary<IFunction[]>): IFunctionGroup[] {
    const groups: IFunctionGroup[] = [];
    forOwn(groupedByAccount, (group, account) => {
      const groupedByRegion = values(groupBy(group, 'region'));
      const namesByRegion = groupedByRegion.map((g) => g.map((fn) => fn.functionName));
      /** gather functions with same name but different region */
      const functionNames =
        namesByRegion.length > 1
          ? intersection(...namesByRegion).reduce<{ [key: string]: boolean }>((acc, name) => {
              acc[name] = true;
              return acc;
            }, {})
          : {};
      /* Group by functionName:region */
      const subGroupings = groupBy(group, (fn) => `${fn.functionName}:${fn.region}`);
      const subGroups: IFunctionGroup[] = [];

      forOwn(subGroupings, (subGroup, nameAndRegion) => {
        const [name, region] = nameAndRegion.split(':');
        const subSubGroups: IFunctionGroup[] = [];

        subGroup.forEach((functionDef) => {
          subSubGroups.push({
            heading: functionDef.region,
            functionDef,
          });
        });

        /* In case function with same name exists in a different region, heading = name(region)*/
        const heading = `${name}${functionNames[name] && region ? ` (${region})` : ''}`;
        subGroups.push({
          heading,
          subgroups: sortBy(subSubGroups, 'heading'),
        });
      });
      groups.push({ heading: account, subgroups: sortBy(subGroups, 'heading') });
    });
    return groups;
  }

  @Debounce(25)
  public updateFunctionGroups(application: Application): void {
    if (!application) {
      application = this.lastApplication;
      if (!this.lastApplication) {
        return null;
      }
    }

    const functions = this.filterFunctionsForDisplay(application.functions.data);
    const grouped = groupBy(functions, 'account');
    const groups = this.getFunctionGroups(grouped);

    FunctionState.filterModel.asFilterModel.groups = groups;
    this.sortGroupsByHeading();
    FunctionState.filterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.groupsUpdatedStream.next(groups);
  }
}
