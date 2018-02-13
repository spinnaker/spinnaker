import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { AccountTag } from 'core/account';
import { EntityNotifications } from 'core/entityTag/notifications/EntityNotifications';
import { HealthCounts } from 'core/healthCounts';
import { IClusterPodTitleProps } from './ClusterPodTitleWrapper';

@BindAll()
export class DefaultClusterPodTitle extends React.Component<IClusterPodTitleProps> {

  public render(): React.ReactElement<DefaultClusterPodTitle> {
    const { grouping, application, parentHeading } = this.props;

    return (
      <div className="rollup-title-cell">
        <div className="heading-tag">
          <AccountTag account={parentHeading} />
        </div>

        <div className="pod-center horizontal space-between center flex-1">
          <div>
            <span className="glyphicon glyphicon-th"/>
            {' ' + grouping.heading}
          </div>

          <EntityNotifications
            entity={grouping}
            application={application}
            placement="top"
            hOffsetPercent="90%"
            entityType="cluster"
            pageLocation="pod"
            className="inverse"
            onUpdate={application.serverGroups.refresh}
          />
        </div>

        <HealthCounts container={grouping.cluster.instanceCounts}/>

      </div>
    )
  }
}
