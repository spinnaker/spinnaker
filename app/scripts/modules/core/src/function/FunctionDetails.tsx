import { IOverridableProps, Overridable } from 'core/overrideRegistry';
import React from 'react';

export interface IFunctionDetailsProps extends IOverridableProps {}

@Overridable('function.details')
export class FunctionDetails extends React.Component<IFunctionDetailsProps> {
  public render() {
    return <h3>Function Details</h3>;
  }
}
