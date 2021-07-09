import React from 'react';

import { IOverridableProps, Overridable } from '../../overrideRegistry';
import { AngularJSAdapter } from '../../reactShims';

export interface IApplicationConfigDetailsProps extends IOverridableProps {}

@Overridable('applicationConfigView')
export class ApplicationConfig extends React.Component<IApplicationConfigDetailsProps> {
  public render() {
    const templateUrl = require('./applicationConfig.view.html');
    return (
      <AngularJSAdapter
        {...this.props}
        templateUrl={templateUrl}
        controller="ApplicationConfigController"
        controllerAs="config"
      />
    );
  }
}
