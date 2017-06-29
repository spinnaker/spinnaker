import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { isEqual, zip } from 'lodash';

import { NgReact } from 'core/reactShims';
import { Application } from 'core/application/application.model';
import { ILoadBalancerGroup } from 'core/domain';
import { LoadBalancer } from './LoadBalancer';
import { Sticky } from 'core/utils/stickyHeader/Sticky';

import './loadBalancerPod.less';

export interface ILoadBalancerPodProps {
  grouping: ILoadBalancerGroup,
  application: Application,
  parentHeading: string,
  showServerGroups: boolean,
  showInstances: boolean
}

function equalForKeys<T>(keys: [keyof T], left: T, right: T): boolean {
  return keys.reduce((acc, key) => acc && left[key] === right[key], true);
}

@autoBindMethods
export class LoadBalancerPod extends React.Component<ILoadBalancerPodProps, void> {
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

  public shouldComponentUpdate(nextProps: ILoadBalancerPodProps) {
    const simplePropsDiffer = () => !equalForKeys(['application', 'parentHeading', 'showServerGroups', 'showInstances'], nextProps, this.props);

    const loadBalancerGroupsDiffer = (left: ILoadBalancerGroup, right: ILoadBalancerGroup) => {
      const simpleGroupingPropsDiffer = () => !equalForKeys(['heading', 'loadBalancer', 'searchField'], left, right);
      const serverGroupsDiffer = () => !isEqual((left.serverGroups || []).map(g => g.name), (right.serverGroups || []).map(g => g.name));
      const subgroupsDiffer = (): boolean => {
        const leftSG = left.subgroups || [];
        const rightSG = right.subgroups || [];
        return leftSG.length !== rightSG.length || zip(leftSG, rightSG).some(tuple => loadBalancerGroupsDiffer(tuple[0], tuple[1]));
      };

      return simpleGroupingPropsDiffer() || serverGroupsDiffer() || subgroupsDiffer();
    };

    return simplePropsDiffer() || loadBalancerGroupsDiffer(nextProps.grouping, this.props.grouping);
  }
}
