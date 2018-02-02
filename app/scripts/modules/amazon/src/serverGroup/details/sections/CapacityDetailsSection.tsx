import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { CollapsibleSection, NgReact, ReactInjector } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

@BindAll()
export class CapacityDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  private resizeServerGroup(): void {
    ReactInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate('aws.resize.modal', require('../resize/resizeServerGroup.html')),
      controller: 'awsResizeServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => this.props.serverGroup,
        application: () => this.props.app
      }
    });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { ViewScalingActivitiesLink } = NgReact;

    return (
      <CollapsibleSection heading="Capacity" defaultExpanded={true}>
        {serverGroup.asg.minSize === serverGroup.asg.maxSize && (
          <dl className="dl-horizontal dl-flex">
            <dt>Min/Max</dt>
            <dd>{serverGroup.asg.desiredCapacity}</dd>
            <dt>Current</dt>
            <dd>{serverGroup.instances.length}</dd>
          </dl>
        )}
        {serverGroup.asg.minSize !== serverGroup.asg.maxSize && (
          <dl className="dl-horizontal dl-flex">
            <dt>Min</dt>
            <dd>{serverGroup.asg.minSize}</dd>
            <dt>Desired</dt>
            <dd>{serverGroup.asg.desiredCapacity}</dd>
            <dt>Max</dt>
            <dd>{serverGroup.asg.maxSize}</dd>
            <dt>Current</dt>
            <dd>{serverGroup.instances.length}</dd>
          </dl>
        )}
        <div>
          <a className="clickable" onClick={this.resizeServerGroup}>Resize Server Group</a>
        </div>
        <div>
          <ViewScalingActivitiesLink serverGroup={serverGroup}/>
        </div>
      </CollapsibleSection>
    )
  }
}
