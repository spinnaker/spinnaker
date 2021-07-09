import React from 'react';

import { DefaultClusterPodTitle } from './DefaultClusterPodTitle';
import { Application } from '../application/application.model';
import { IClusterSubgroup } from './filter/ClusterFilterService';
import { ReactInjector } from '../reactShims';

export interface IClusterPodTitleProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
}

export class ClusterPodTitleWrapper extends React.Component<IClusterPodTitleProps> {
  public render(): React.ReactElement<ClusterPodTitleWrapper> {
    const { overrideRegistry } = ReactInjector;
    const config = overrideRegistry.getComponent('clusterPodTitle');
    const Title = config || DefaultClusterPodTitle;

    return <Title {...this.props} />;
  }
}
