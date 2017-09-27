import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
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


@autoBindMethods
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

  public shouldComponentUpdate(nextProps: ILoadBalancerPodProps) {
    const simpleProps: [keyof ILoadBalancerPodProps] = ['application', 'parentHeading', 'showServerGroups', 'showInstances'];
    const simplePropsDiffer = () => simpleProps.some(key => this.props[key] !== nextProps[key]);

    const loadBalancerGroupsDiffer = (left: ILoadBalancerGroup, right: ILoadBalancerGroup) => {
      const loadBalancerGroupProps: [keyof ILoadBalancerGroup] = ['heading', 'loadBalancer', 'searchField']
      const simpleGroupingPropsDiffer = () => loadBalancerGroupProps.some(key => left[key] !== right[key]);
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
