import { UISref } from '@uirouter/react';
import classNames from 'classnames';
import * as React from 'react';

import { Application } from '../application';
import { CloudProviderLogo } from '../cloudProvider';
import { ILoadBalancerUsage, ISecurityGroup, ISecurityGroupGroup, IServerGroupUsage } from '../domain';
import { EntityNotifications } from '../entityTag/notifications/EntityNotifications';
import { ManagedResourceStatusIndicator } from '../managed';
import { ReactInjector } from '../reactShims';
import { SecurityGroupState } from '../state';

import './securityGroup.less';

interface ISecurityGroupProps {
  application: Application;
  securityGroup: ISecurityGroup;
  parentGrouping: ISecurityGroupGroup;
  heading: string;
}

const Heading = ({ application, parentGrouping, securityGroup, heading }: ISecurityGroupProps) => (
  <h6 className="sticky-header-2 highlightable-header">
    <div className="clickable-header horizontal middle">
      <span className="glyphicon glyphicon-transfer sp-padding-xs-right" />
      <div className="flex-1">{(heading || '').toUpperCase()}</div>

      {!parentGrouping.isManaged && securityGroup.isManaged && (
        <ManagedResourceStatusIndicator
          shape="circle"
          resourceSummary={securityGroup.managedResourceSummary}
          application={application}
        />
      )}
      <EntityNotifications
        entity={securityGroup}
        application={application}
        placement="bottom"
        entityType="securityGroup"
        pageLocation="details"
        onUpdate={() => application.securityGroups.refresh()}
      />
    </div>
  </h6>
);

const getSecurityGroupDetailsParams = (securityGroup: ISecurityGroup, application: Application) => ({
  application: application.name,
  region: securityGroup.region,
  accountId: securityGroup.accountName,
  name: securityGroup.name,
  vpcId: securityGroup.vpcId,
  provider: securityGroup.provider,
});

const ServerGroup = ({ serverGroup }: { serverGroup: IServerGroupUsage }) => (
  <div className={`rollup-row ${serverGroup.isDisabled ? 'disabled' : ''}`} key={serverGroup.name}>
    <UISref
      to=".serverGroup"
      params={{
        region: serverGroup.region,
        accountId: serverGroup.account,
        serverGroup: serverGroup.name,
        provider: serverGroup.cloudProvider,
      }}
    >
      <a>
        <CloudProviderLogo provider={serverGroup.cloudProvider} height="16px" width="16px" />
        <span className="sp-padding-xs-left">{serverGroup.name}</span>
      </a>
    </UISref>
  </div>
);

const ServerGroups = ({ serverGroups }: { serverGroups: IServerGroupUsage[] }) => {
  if (!SecurityGroupState.filterModel.asFilterModel.sortFilter.showServerGroups) {
    return null;
  }
  return (
    <div className="rollup-details-section col-md-6">
      {serverGroups.length === 0 && <div className="small">No server groups</div>}
      {serverGroups.length > 0 && (
        <>
          {serverGroups.map((serverGroup) => (
            <ServerGroup key={serverGroup.name} serverGroup={serverGroup} />
          ))}
        </>
      )}
    </div>
  );
};

const LoadBalancer = ({
  loadBalancer,
  securityGroup,
}: {
  loadBalancer: ILoadBalancerUsage;
  securityGroup: ISecurityGroup;
}) => (
  <div className="rollup-row" key={loadBalancer.name}>
    <UISref
      to=".loadBalancerDetails"
      params={{
        region: securityGroup.region,
        accountId: securityGroup.account,
        name: loadBalancer.name,
        vpcId: securityGroup.vpcId,
        provider: securityGroup.provider,
      }}
    >
      <a>
        <i className="fa icon-sitemap sp-padding-xs-right" /> {loadBalancer.name}
      </a>
    </UISref>
  </div>
);

const LoadBalancers = ({ securityGroup }: { securityGroup: ISecurityGroup }) => {
  if (!SecurityGroupState.filterModel.asFilterModel.sortFilter.showLoadBalancers) {
    return null;
  }
  return (
    <div className="rollup-details-section col-md-6">
      {securityGroup.usages.loadBalancers.length === 0 && <div className="small">No load balancers</div>}
      {securityGroup.usages.loadBalancers.length > 0 && (
        <>
          {securityGroup.usages.loadBalancers.map((loadBalancer) => (
            <LoadBalancer key={loadBalancer.name} loadBalancer={loadBalancer} securityGroup={securityGroup} />
          ))}
        </>
      )}
    </div>
  );
};

export const SecurityGroup = (props: ISecurityGroupProps) => {
  const params = getSecurityGroupDetailsParams(props.securityGroup, props.application);
  // don't use <UISrefActive> - it picks up load balancer and server groups details!
  const active = ReactInjector.$state.includes('**.firewallDetails', params);
  return (
    <UISref to=".firewallDetails" params={params}>
      <div className={classNames('pod-subgroup clickable clickable-row clearfix', { active })}>
        <Heading {...props} />
        <div className="cluster-container">
          <ServerGroups serverGroups={props.securityGroup.usages.serverGroups} />
          <LoadBalancers securityGroup={props.securityGroup} />
        </div>
      </div>
    </UISref>
  );
};
