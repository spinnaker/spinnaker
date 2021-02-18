import { IOverridableProps, Overridable } from 'core/overrideRegistry';
import React from 'react';

export interface IServerGroupManagerDetailsProps extends IOverridableProps {}

@Overridable('serverGroupManager.details')
export class ServerGroupManagerDetails extends React.Component<IServerGroupManagerDetailsProps> {
  public render() {
    return <h3>Server Group Manager Details</h3>;
  }
}
