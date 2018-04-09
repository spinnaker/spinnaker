import { module } from 'angular';
import { FilterModelService } from 'core/filterModel/filter.model.service';
import { chain, find, forOwn, groupBy, map, sortBy } from 'lodash';
import { Subject } from 'rxjs';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application/application.model';
import { ISecurityGroup, ISecurityGroupGroup } from 'core/domain';
import { SECURITY_GROUP_FILTER_MODEL, SecurityGroupFilterModel } from './securityGroupFilter.model';
import { FILTER_MODEL_SERVICE } from 'core/filterModel';

@BindAll()
export class SecurityGroupFilterService {
  public groupsUpdatedStream: Subject<ISecurityGroupGroup[]> = new Subject<ISecurityGroupGroup[]>();

  private lastApplication: Application;

  constructor(
    private securityGroupFilterModel: SecurityGroupFilterModel,
    private filterModelService: FilterModelService,
  ) {
    'ngInject';
  }

  private addSearchFields(securityGroup: ISecurityGroup): void {
    if (!securityGroup.searchField) {
      securityGroup.searchField = [
        securityGroup.name,
        securityGroup.id,
        securityGroup.accountName,
        securityGroup.region,
        securityGroup.vpcName,
        securityGroup.vpcId,
        map(securityGroup.usages.serverGroups, 'name').join(' '),
        map(securityGroup.usages.loadBalancers, 'name').join(' '),
      ].join(' ');
    }
  }

  private checkSearchTextFilter(securityGroup: ISecurityGroup): boolean {
    const filter = this.securityGroupFilterModel.asFilterModel.sortFilter.filter;
    if (!filter) {
      return true;
    }

    if (filter.includes('vpc:')) {
      const [, vpcName] = /vpc:([\w-]*)/.exec(filter);
      return securityGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
    }

    this.addSearchFields(securityGroup);
    return filter.split(' ').every((testWord: string) => {
      return securityGroup.searchField.includes(testWord);
    });
  }

  public filterSecurityGroupsForDisplay(securityGroups: ISecurityGroup[]): ISecurityGroup[] {
    const service = this.filterModelService;
    const model = this.securityGroupFilterModel.asFilterModel;
    return chain(securityGroups)
      .filter(sg => this.checkSearchTextFilter(sg))
      .filter(sg => service.checkAccountFilters(model)(sg))
      .filter(sg => service.checkRegionFilters(model)(sg))
      .filter(sg => service.checkStackFilters(model)(sg))
      .filter(sg => service.checkDetailFilters(model)(sg))
      .filter(sg => service.checkProviderFilters(model)(sg))
      .value();
  }

  public sortGroupsByHeading(groups: ISecurityGroupGroup[]): void {
    const currentGroups: ISecurityGroupGroup[] = this.securityGroupFilterModel.asFilterModel.groups;
    this.diffSubgroups(currentGroups, groups);

    // sort groups in place so Angular doesn't try to update the world
    currentGroups.sort((a: ISecurityGroupGroup, b: ISecurityGroupGroup) => {
      if (a.heading < b.heading) {
        return -1;
      }
      if (a.heading > b.heading) {
        return 1;
      }
      return 0;
    });
  }

  private diffSubgroups(oldGroups: ISecurityGroupGroup[], newGroups: ISecurityGroupGroup[]): void {
    const groupsToRemove: number[] = [];

    oldGroups.forEach((oldGroup, idx) => {
      const newGroup = (newGroups || []).find(g => g.heading === oldGroup.heading && g.vpcName === oldGroup.vpcName);
      if (!newGroup) {
        groupsToRemove.push(idx);
      } else {
        if (newGroup.securityGroup) {
          oldGroup.securityGroup = newGroup.securityGroup;
        }
        if (newGroup.subgroups) {
          this.diffSubgroups(oldGroup.subgroups, newGroup.subgroups);
        }
      }
    });
    groupsToRemove.reverse().forEach(idx => {
      oldGroups.splice(idx, 1);
    });
    newGroups.forEach(newGroup => {
      const match = find(oldGroups, { heading: newGroup.heading });
      if (!match) {
        oldGroups.push(newGroup);
      }
    });
  }

  public updateSecurityGroups(application: Application): void {
    if (!application) {
      application = this.lastApplication;
      if (!this.lastApplication) {
        return null;
      }
    }

    const groups: ISecurityGroupGroup[] = [];

    const securityGroups = this.filterSecurityGroupsForDisplay(application.securityGroups.data);
    const grouped = groupBy(securityGroups, 'account');

    forOwn(grouped, (group, key) => {
      const subGroupings = groupBy(group, 'name'),
        subGroups: ISecurityGroupGroup[] = [];

      forOwn(subGroupings, (subGroup, subKey) => {
        const subSubGroups: ISecurityGroupGroup[] = [];
        subGroup.forEach(securityGroup => {
          const heading = securityGroup.vpcName
            ? `${securityGroup.region} (${securityGroup.vpcName})`
            : securityGroup.region;
          subSubGroups.push({
            heading: heading,
            vpcName: securityGroup.vpcName,
            securityGroup: securityGroup,
          });
        });
        subGroups.push({
          heading: subKey,
          subgroups: sortBy(subSubGroups, ['heading', 'vpcName']),
        });
      });

      groups.push({ heading: key, subgroups: sortBy(subGroups, 'heading') });
    });

    this.sortGroupsByHeading(groups);
    this.securityGroupFilterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.groupsUpdatedStream.next(groups);
  }
}

export const SECURITY_GROUP_FILTER_SERVICE = 'spinnaker.core.securityGroup.filter.service';
module(SECURITY_GROUP_FILTER_SERVICE, [SECURITY_GROUP_FILTER_MODEL, FILTER_MODEL_SERVICE]).service(
  'securityGroupFilterService',
  SecurityGroupFilterService,
);
