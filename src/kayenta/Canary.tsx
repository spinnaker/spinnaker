import * as React from 'react';

import { Application } from '@spinnaker/core';

export interface ICanaryProps {
  application: Application;
}

export class Canary extends React.Component<ICanaryProps, null> {

  public render() {
    return (
      <pre>
        {JSON.stringify(this.props.application.getDataSource('canaryConfigs').data, null, 2)}
      </pre>
    );
  }
}
