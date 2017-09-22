import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { ReactInjector } from 'core/reactShims';
import { DefaultClusterPodTitle } from './DefaultClusterPodTitle';
import { IClusterSubgroup } from './filter/clusterFilter.service';

export interface IClusterPodTitleProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
}

@autoBindMethods
export class ClusterPodTitleWrapper extends React.Component<IClusterPodTitleProps> {
  public render(): React.ReactElement<ClusterPodTitleWrapper> {
    const { overrideRegistry } = ReactInjector;
    const config = overrideRegistry.getComponent('clusterPodTitle');
    const Title = config || DefaultClusterPodTitle;

    return <Title {...this.props} />;
  }
}
