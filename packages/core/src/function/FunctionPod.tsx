import React from 'react';

import Function from './Function';
import { AccountTag } from '../account';
import { Application } from '../application/application.model';
import { IFunctionGroup } from '../domain';

export interface IFunctionPodProps {
  grouping: IFunctionGroup;
  application: Application;
  parentHeading: string;
}

export class FunctionPod extends React.Component<IFunctionPodProps> {
  public render(): React.ReactElement<FunctionPod> {
    const { grouping, application, parentHeading } = this.props;
    const subgroups = grouping.subgroups.map((subgroup) => (
      <Function key={subgroup.functionDef.functionName} application={application} functionDef={subgroup.functionDef} />
    ));

    return (
      <div className="load-balancer-pod row rollup-entry sub-group">
        <div className="rollup-summary sticky-header">
          <div className="rollup-title-cell">
            <div className="heading-tag">
              <AccountTag account={parentHeading} />
            </div>
            <div className="pod-center horizontal space-between center flex-1">
              <div>{grouping.heading}</div>
            </div>
          </div>
        </div>
        <div className="rollup-details">{subgroups}</div>
      </div>
    );
  }
}
