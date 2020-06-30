import React from 'react';

import { Overridable } from '../overrideRegistry/Overridable';

import { Icon } from '../presentation';

@Overridable('applicationIcon')
export class ApplicationFreshIcon extends React.Component<{}> {
  public render() {
    return <Icon name="spMenuAppInSync" size="small" appearance="light" />;
  }
}
