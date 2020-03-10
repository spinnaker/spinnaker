import React from 'react';

import { Application } from './application.model';

import { Overridable, IOverridableProps } from '../overrideRegistry/Overridable';

export interface IApplicationIconProps extends IOverridableProps {
  app: Application;
}

@Overridable('applicationIcon')
export class ApplicationIcon extends React.Component<IApplicationIconProps> {
  public render() {
    return <i className="application-header-icon far fa-window-maximize" />;
  }
}
