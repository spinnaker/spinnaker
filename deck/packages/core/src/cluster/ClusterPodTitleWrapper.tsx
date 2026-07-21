import React from 'react';

import { DefaultClusterPodTitle } from './DefaultClusterPodTitle';
import { AngularServices } from '../angular/services';
import type { Application } from '../application/application.model';
import type { IClusterSubgroup } from './filter/ClusterFilterService';

export interface IClusterPodTitleProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
}

export class ClusterPodTitleWrapper extends React.Component<IClusterPodTitleProps> {
  public render(): React.ReactElement<ClusterPodTitleWrapper> {
    const { overrideRegistry } = AngularServices;
    const config = overrideRegistry.getComponent('clusterPodTitle');
    const Title = config || DefaultClusterPodTitle;

    return <Title {...this.props} />;
  }
}
