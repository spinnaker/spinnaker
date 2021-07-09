import React from 'react';

import { IOverridableProps, Overridable } from '../overrideRegistry';

export interface IFunctionDetailsProps extends IOverridableProps {}

@Overridable('function.details')
export class FunctionDetails extends React.Component<IFunctionDetailsProps> {
  public render() {
    return <h3>Function Details</h3>;
  }
}
