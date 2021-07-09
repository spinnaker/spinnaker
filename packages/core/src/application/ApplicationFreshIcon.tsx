import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { Overridable } from '../overrideRegistry/Overridable';

@Overridable('applicationIcon')
export class ApplicationFreshIcon extends React.Component<{}> {
  public render() {
    return <Illustration className="app-fresh-icon horizontal middle" name="appSynced" />;
  }
}
