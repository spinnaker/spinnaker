import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { CollapsibleSection, NgReact, Overridable, ReactInjector, Application, IServerGroup } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

@Overridable('amazon.serverGroup.CapacityDetailsSection')
@BindAll()
export class CapacityDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public static resizeServerGroup(serverGroup: IServerGroup, application: Application): void {
    ReactInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate(
        'aws.resize.modal',
        require('../resize/resizeServerGroup.html'),
      ),
      controller: 'awsResizeServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => serverGroup,
        application: () => application,
      },
    });
  }

  public render(): JSX.Element {
    const { serverGroup, app } = this.props;
    const { ViewScalingActivitiesLink } = NgReact;
    const simple = serverGroup.asg.minSize === serverGroup.asg.maxSize;

    return (
      <CollapsibleSection heading="Capacity" defaultExpanded={true}>
        <dl className="dl-horizontal dl-flex">
          {simple && <dt>Min/Max</dt>}
          {simple && <dd>{serverGroup.asg.desiredCapacity}</dd>}

          {!simple && <dt>Min</dt>}
          {!simple && <dd>{serverGroup.asg.minSize}</dd>}
          {!simple && <dt>Desired</dt>}
          {!simple && <dd>{serverGroup.asg.desiredCapacity}</dd>}
          {!simple && <dt>Max</dt>}
          {!simple && <dd>{serverGroup.asg.maxSize}</dd>}

          <dt>Current</dt>
          <dd>{serverGroup.instances.length}</dd>
        </dl>

        <div>
          <a className="clickable" onClick={() => CapacityDetailsSection.resizeServerGroup(serverGroup, app)}>
            Resize Server Group
          </a>
        </div>

        <div>
          <ViewScalingActivitiesLink serverGroup={serverGroup} />
        </div>
      </CollapsibleSection>
    );
  }
}
