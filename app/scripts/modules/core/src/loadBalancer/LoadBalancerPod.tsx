import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { isEqual, zip } from 'lodash';

import { NgReact } from 'core/reactShims';
import { Application } from 'core/application/application.model';
import { ILoadBalancerGroup } from 'core/domain';
import { LoadBalancer } from './LoadBalancer';

import './loadBalancerPod.less';

export interface ILoadBalancerPodProps {
  grouping: ILoadBalancerGroup,
  application: Application,
  parentHeading: string,
  showServerGroups: boolean,
  showInstances: boolean
}


@BindAll()
export class LoadBalancerPod extends React.Component<ILoadBalancerPodProps> {
  public render(): React.ReactElement<LoadBalancerPod> {
    const { grouping, application, parentHeading, showServerGroups, showInstances } = this.props;
    const { AccountTag } = NgReact;
    const subgroups = grouping.subgroups.map((subgroup) => (
      <LoadBalancer
        key={subgroup.heading}
        application={application}
        loadBalancer={subgroup.loadBalancer}
        serverGroups={subgroup.serverGroups}
        showServerGroups={showServerGroups}
        showInstances={showInstances}
      />
    ));

    return (
      <div className="load-balancer-pod row rollup-entry sub-group">
        <div className="rollup-summary sticky-header">
          <div className="rollup-title-cell">
            <div className="heading-tag">
              <AccountTag account={parentHeading}/>
            </div>
            <div className="pod-center">
              <div>
                {grouping.heading}
              </div>
            </div>
          </div>
        </div>
        <div className="rollup-details">
          {subgroups}
        </div>
      </div>
    );
  }
}
