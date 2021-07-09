import * as React from 'react';

import { CreateSecurityGroupButton } from './CreateSecurityGroupButton';
import { SecurityGroupPod } from './SecurityGroupPod';
import { Application } from '../application/application.model';
import { BannerContainer } from '../banner';
import { ProviderSelectionService } from '../cloudProvider/providerSelection/ProviderSelectionService';
import { SETTINGS } from '../config';
import { ISecurityGroupGroup } from '../domain';
import { ISortFilter } from '../filterModel';
import { FilterTags, IFilterTag } from '../filterModel/FilterTags';
import { FirewallLabels } from './label/FirewallLabels';
import { SecurityGroupState } from '../state';
import { noop } from '../utils';
import { Spinner } from '../widgets/spinners/Spinner';

const { useEffect, useState } = React;

export interface ISecurityGroupsProps {
  app: Application;
}

interface IFilterModel {
  groups: ISecurityGroupGroup[];
  tags: IFilterTag[];
}

const Groupings = ({ groups, app }: { groups: ISecurityGroupGroup[]; app: Application }) => (
  <div>
    {groups.map((group) => (
      <div key={group.heading} className="rollup">
        {group.subgroups &&
          group.subgroups.map((subgroup) => (
            <SecurityGroupPod
              key={subgroup.heading}
              grouping={subgroup}
              application={app}
              parentHeading={group.heading}
            />
          ))}
      </div>
    ))}
    {groups.length === 0 && (
      <div>
        <h4 className="text-center">No {FirewallLabels.get('firewalls')} match the filters you've selected.</h4>
      </div>
    )}
  </div>
);

const Filters = () => {
  const { showServerGroups, showLoadBalancers } = SecurityGroupState.filterModel.asFilterModel.sortFilter;
  const toggleParam = (event: any): void => {
    const { checked } = event.target;
    const name: keyof ISortFilter = event.target.name;
    (SecurityGroupState.filterModel.asFilterModel.sortFilter[name as keyof ISortFilter] as any) = !!checked;
    SecurityGroupState.filterModel.asFilterModel.applyParamsToUrl();
  };

  return (
    <div className="col-lg-8 col-md-10">
      <div className="form-inline clearfix filters">
        <div className="form-group">
          <label className="checkbox"> Show </label>
          <div className="checkbox">
            <label>
              <input type="checkbox" name="showServerGroups" checked={showServerGroups} onChange={toggleParam} /> Server
              Groups
            </label>
          </div>
          <div className="checkbox">
            <label>
              <input type="checkbox" name="showLoadBalancers" checked={showLoadBalancers} onChange={toggleParam} /> Load
              Balancers
            </label>
          </div>
        </div>
      </div>
    </div>
  );
};

export const SecurityGroups = ({ app }: ISecurityGroupsProps) => {
  const [filterModel, setFilterModel] = useState<IFilterModel>({ groups: [], tags: [] });
  const [initialized, setInitialized] = useState(false);
  const [buttonDisable, setButtonDisable] = useState(false);
  const groupsUpdated = () => {
    setFilterModel({
      groups: SecurityGroupState.filterModel.asFilterModel.groups,
      tags: SecurityGroupState.filterModel.asFilterModel.tags,
    });
  };

  const updateSecurityGroupGroups = () => {
    SecurityGroupState.filterModel.asFilterModel.applyParamsToUrl();
    // If we are using managed resources, wait until they are ready before updating security groups.
    // Otherwise, the managed resource fields will not be present on the security group groupings and we'll lose the
    // badges on the group headers.
    // If the managed resources endpoint fails, then it is fine to show the security groups without managed resource
    // fields.
    const waiter = SETTINGS.feature.managedResources ? app.managedResources.ready() : Promise.resolve();
    waiter.catch(noop).finally(() => {
      SecurityGroupState.filterService.updateSecurityGroups(app);
      if (!initialized) {
        setInitialized(true);
      }
    });
  };

  const clearFilters = () => {
    SecurityGroupState.filterService.clearFilters();
    updateSecurityGroupGroups();
  };

  useEffect(() => {
    const groupsUpdatedListener = SecurityGroupState.filterService.groupsUpdatedStream.subscribe(groupsUpdated);
    const dataSource = app.getDataSource('securityGroups');
    const securityGroupsRefreshUnsubscribe = dataSource.onRefresh(null, updateSecurityGroupGroups);
    dataSource.ready().then(() => {
      updateSecurityGroupGroups();
    });
    app.setActiveState(app.securityGroups);
    SecurityGroupState.filterModel.asFilterModel.activate();
    ProviderSelectionService.isDisabled(app).then((val) => {
      setButtonDisable(val);
    });
    return () => {
      groupsUpdatedListener.unsubscribe();
      securityGroupsRefreshUnsubscribe();
    };
  }, [app]);

  const groupings = initialized ? (
    <Groupings groups={filterModel.groups} app={app} />
  ) : (
    <div>
      <Spinner size="medium" />
    </div>
  );

  return (
    <div className="main-content">
      <div className="header row header-clusters">
        <Filters />
        <div className="col-lg-4 col-md-2">
          <div className="application-actions">{buttonDisable ? <div /> : <CreateSecurityGroupButton app={app} />}</div>
        </div>
        <FilterTags tags={filterModel.tags} tagCleared={updateSecurityGroupGroups} clearFilters={clearFilters} />
      </div>

      <div className="content">
        <BannerContainer app={app} />
        {groupings}
      </div>
    </div>
  );
};
