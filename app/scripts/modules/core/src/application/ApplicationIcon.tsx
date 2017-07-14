import * as React from 'react';

import { Application } from 'core/application';

export interface IApplicationIconProps {
  app: Application;
}

export class ApplicationIcon extends React.Component<IApplicationIconProps> {
  public render() {
    return <i className="fa fa-window-maximize"/>;
  }
}
