import React from 'react';

import { Application } from 'core/application/application.model';
import { ReactInjector } from 'core/reactShims';

import { DefaultClusterPodTitle } from './DefaultClusterPodTitle';
import { IClusterSubgroup } from './filter/ClusterFilterService';

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
