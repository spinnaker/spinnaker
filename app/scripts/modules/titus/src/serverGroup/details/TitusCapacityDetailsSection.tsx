import * as React from 'react';

import { ReactModal, Application, Overridable } from '@spinnaker/core';
import { ITitusServerGroup } from 'titus/domain';
import { ITitusResizeServerGroupModalProps, TitusResizeServerGroupModal } from './resize/TitusResizeServerGroupModal';

interface ICapacityDetailsSectionProps {
  app: Application;
  serverGroup: ITitusServerGroup;
}

export const TitusSimpleMinMaxDesired = ({ serverGroup }: ICapacityDetailsSectionProps) => (
  <>
    <dt>Min/Max</dt>
    <dd>{serverGroup.capacity.desired}</dd>
    <dt>Current</dt>
    <dd>{serverGroup.instances.length}</dd>
  </>
);

export const TitusAdvancedMinMaxDesired = ({ serverGroup }: ICapacityDetailsSectionProps) => (
  <>
    <dt>Min</dt>
    <dd>{serverGroup.capacity.min}</dd>
    <dt>Desired</dt>
    <dd>{serverGroup.capacity.desired}</dd>
    <dt>Max</dt>
    <dd>{serverGroup.capacity.max}</dd>
    <dt>Current</dt>
    <dd>{serverGroup.instances.length}</dd>
  </>
);

export const TitusCapacityGroup = ({ serverGroup }: ICapacityDetailsSectionProps) => (
  <>
    <dt>Cap. Group</dt>
    <dd>{serverGroup.capacityGroup}</dd>
  </>
);

@Overridable('titus.serverGroup.CapacityDetailsSection')
export class TitusCapacityDetailsSection extends React.Component<ICapacityDetailsSectionProps> {
  public render(): JSX.Element {
    const { serverGroup, app: application } = this.props;
    const isSimpleMode = serverGroup.capacity.min === serverGroup.capacity.max;
    const resizeServerGroup = () =>
      ReactModal.show<ITitusResizeServerGroupModalProps>(TitusResizeServerGroupModal, { serverGroup, application });

    return (
      <>
        <dl className="dl-horizontal dl-flex">
          {isSimpleMode ? <TitusSimpleMinMaxDesired {...this.props} /> : <TitusAdvancedMinMaxDesired {...this.props} />}
          {serverGroup.capacityGroup && <TitusCapacityGroup {...this.props} />}
        </dl>

        <div>
          <a className="clickable" onClick={resizeServerGroup}>
            Resize Server Group
          </a>
        </div>
      </>
    );
  }
}
