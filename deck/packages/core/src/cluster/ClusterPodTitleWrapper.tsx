import React from 'react';

import { DefaultClusterPodTitle } from './DefaultClusterPodTitle';
import type { Application } from '../application/application.model';
import type { IClusterSubgroup } from './filter/ClusterFilterService';
import { overrideRegistry } from '../overrideRegistry/override.registry';

export interface IClusterPodTitleProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
}

export class ClusterPodTitleWrapper extends React.Component<IClusterPodTitleProps> {
  public render(): React.ReactElement<ClusterPodTitleWrapper> {
    const config = overrideRegistry.getComponent('clusterPodTitle');
    const Title = config || DefaultClusterPodTitle;

    return <Title {...this.props} />;
  }
}
