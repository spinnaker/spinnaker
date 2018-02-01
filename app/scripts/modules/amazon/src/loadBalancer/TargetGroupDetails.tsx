import * as React from 'react';
import { Overridable, IOverridableProps } from '@spinnaker/core';

export interface ITargetGroupDetailsProps extends IOverridableProps {
}

@Overridable('loadBalancer.targetGroupDetails')
export class TargetGroupDetails extends React.Component<ITargetGroupDetailsProps> {
  public render() {
    return (
      <h3>Target Group Details</h3>
    );
  }
}
