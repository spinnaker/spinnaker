import React from 'react';

import { Overridable } from '../overrideRegistry/Overridable';

import { Illustration } from '../presentation';

@Overridable('applicationIcon')
export class ApplicationFreshIcon extends React.Component<{}> {
  public render() {
    return <Illustration className="app-fresh-icon horizontal middle" name="appSynced" />;
  }
}
