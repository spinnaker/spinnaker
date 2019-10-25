import * as React from 'react';
import { Overridable, IOverridableProps } from 'core/overrideRegistry';

export interface IFunctionDetailsProps extends IOverridableProps {}

@Overridable('function.details')
export class FunctionDetails extends React.Component<IFunctionDetailsProps> {
  public render() {
    return <h3>Function Details</h3>;
  }
}
