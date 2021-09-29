import React from 'react';

import type { IOverridableProps } from '../overrideRegistry';
import { Overridable } from '../overrideRegistry';

export interface IServerGroupManagerDetailsProps extends IOverridableProps {}

@Overridable('serverGroupManager.details')
export class ServerGroupManagerDetails extends React.Component<IServerGroupManagerDetailsProps> {
  public render() {
    return <h3>Server Group Manager Details</h3>;
  }
}
