import { chain, find, forOwn, groupBy, map, sortBy } from 'lodash';
import { Subject } from 'rxjs';

import { Application } from '../../application/application.model';
import { ISecurityGroup, ISecurityGroupGroup } from '../../domain';
import { FilterModelService } from '../../filterModel';
import { SecurityGroupState } from '../../state';

export class SecurityGroupFilterService {
  public groupsUpdatedStream: Subject<ISecurityGroupGroup[]> = new Subject<ISecurityGroupGroup[]>();

  private lastApplication: Application;

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
    const filter = SecurityGroupState.filterModel.asFilterModel.sortFilter.filter;
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

  public clearFilters(): void {
    SecurityGroupState.filterModel.asFilterModel.clearFilters();
    SecurityGroupState.filterModel.asFilterModel.applyParamsToUrl();
  }

  public filterSecurityGroupsForDisplay(securityGroups: ISecurityGroup[]): ISecurityGroup[] {
    const service = FilterModelService;
    const model = SecurityGroupState.filterModel.asFilterModel;
    return chain(securityGroups)
      .filter((sg) => this.checkSearchTextFilter(sg))
      .filter((sg) => service.checkAccountFilters(model)(sg))
      .filter((sg) => service.checkRegionFilters(model)(sg))
      .filter((sg) => service.checkStackFilters(model)(sg))
      .filter((sg) => service.checkDetailFilters(model)(sg))
      .filter((sg) => service.checkProviderFilters(model)(sg))
      .value();
  }

  public sortGroupsByHeading(groups: ISecurityGroupGroup[]): void {
    const currentGroups: ISecurityGroupGroup[] = SecurityGroupState.filterModel.asFilterModel.groups;
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
      const newGroup = (newGroups || []).find((g) => g.heading === oldGroup.heading && g.vpcName === oldGroup.vpcName);
      if (!newGroup) {
        groupsToRemove.push(idx);
      } else {
        if (newGroup.securityGroup) {
          oldGroup.securityGroup = newGroup.securityGroup;
        }
        if (newGroup.subgroups) {
          this.diffSubgroups(oldGroup.subgroups, newGroup.subgroups);
        }
        if (oldGroup.hasOwnProperty('isManaged') || newGroup.hasOwnProperty('isManaged')) {
          oldGroup.isManaged = newGroup.isManaged;
          oldGroup.managedResourceSummary = newGroup.managedResourceSummary;
        }
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
      const subGroupings = groupBy(group, 'name');
      const subGroups: ISecurityGroupGroup[] = [];

      forOwn(subGroupings, (subGroup, subKey) => {
        const subSubGroups: ISecurityGroupGroup[] = [];
        subGroup.forEach((securityGroup) => {
          const heading = securityGroup.vpcName
            ? `${securityGroup.region} (${securityGroup.vpcName})`
            : securityGroup.region;
          subSubGroups.push({
            heading,
            vpcName: securityGroup.vpcName,
            securityGroup,
            isManaged: !!securityGroup.isManaged,
            managedResourceSummary: securityGroup.managedResourceSummary,
          });
        });

        const allRegionsManaged = subSubGroups.every(({ isManaged }) => isManaged);
        subGroups.push({
          heading: subKey,
          subgroups: sortBy(subSubGroups, ['heading', 'vpcName']),
          isManaged: allRegionsManaged,
          managedResourceSummary: allRegionsManaged ? subSubGroups[0].managedResourceSummary : undefined,
        });
      });

      groups.push({ heading: key, subgroups: sortBy(subGroups, 'heading') });
    });

    this.sortGroupsByHeading(groups);
    SecurityGroupState.filterModel.asFilterModel.addTags();
    this.lastApplication = application;
    this.groupsUpdatedStream.next(groups);
  }
}
