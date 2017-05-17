import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { NgReact } from 'core/reactShims';
import { Application } from 'core/application/application.model';
import { ILoadBalancerGroup } from 'core/domain';
import { LoadBalancer } from './LoadBalancer';
import { Sticky } from 'core/utils/stickyHeader/Sticky';

import './loadBalancerPod.less';

interface IProps {
  grouping: ILoadBalancerGroup,
  application: Application,
  parentHeading: string,
  showServerGroups: boolean,
  showInstances: boolean
}

interface IState {
}

@autoBindMethods
export class LoadBalancerPod extends React.Component<IProps, IState> {
  public render(): React.ReactElement<LoadBalancerPod> {
    const { grouping, application, parentHeading, showServerGroups, showInstances } = this.props;
    const { AccountTag } = NgReact;
    const subgroups = grouping.subgroups.map((subgroup) => (
      <div key={subgroup.heading} className="pod-subgroup">
        <LoadBalancer
          application={application}
          loadBalancer={subgroup.loadBalancer}
          serverGroups={subgroup.serverGroups}
          showServerGroups={showServerGroups}
          showInstances={showInstances}
        />
      </div>
    ));

    return (
      <div className="load-balancer-pod row rollup-entry sub-group">
        <Sticky>
          <div className="rollup-summary">
            <div className="rollup-title-cell">
              <div className="heading-tag">
                <AccountTag account={parentHeading}/>
              </div>
              <div className="pod-center">
                <div>
                  <span className="icon icon-elb"/> {grouping.heading}
                </div>
              </div>
            </div>
          </div>
        </Sticky>
        <div className="rollup-details">
          {subgroups}
        </div>
      </div>
    );
  }
}
