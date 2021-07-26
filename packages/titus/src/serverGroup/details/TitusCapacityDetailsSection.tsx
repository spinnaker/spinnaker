import React from 'react';

import {
  Application,
  confirmNotManaged,
  CurrentCapacity,
  DesiredCapacity,
  Overridable,
  ReactModal,
  showModal
} from '@spinnaker/core';
import { ITitusServerGroup } from '../../domain';
import { TitusProviderSettings } from '../../titus.settings';
import { TitusScalingActivitiesModal } from './scalingActivity/TitusScalingActivitiesModal';

import { ITitusResizeServerGroupModalProps, TitusResizeServerGroupModal } from './resize/TitusResizeServerGroupModal';

interface ICapacityDetailsSectionProps {
  app: Application;
  serverGroup: ITitusServerGroup;
}

@Overridable('titus.serverGroup.CapacityDetailsSection')
export class TitusCapacityDetailsSection extends React.Component<ICapacityDetailsSectionProps> {
  public render(): JSX.Element {
    const { serverGroup, app: application } = this.props;
    const { capacity } = serverGroup;
    const current = serverGroup.instances.length;
    const simpleMode = capacity.min === capacity.max;

    const resizeServerGroup = () =>
      confirmNotManaged(serverGroup, application).then((notManaged) => {
        notManaged &&
          ReactModal.show<ITitusResizeServerGroupModalProps>(TitusResizeServerGroupModal, { serverGroup, application });
      });
    
    const showScalingActivities = (TitusProviderSettings.scalingActivities || []).includes(application.name);
    return (
      <>
        <dl className="dl-horizontal dl-narrow">
          <DesiredCapacity capacity={capacity} simpleMode={simpleMode} />
          <CurrentCapacity currentCapacity={current} />
          {serverGroup.capacityGroup && (
            <>
              <dt>Cap. Group</dt>
              <dd>{serverGroup.capacityGroup}</dd>
            </>
          )}
        </dl>

        <div>
          <a className="clickable" onClick={resizeServerGroup}>
            Resize Server Group
          </a>
        </div>
        {showScalingActivities && <div>
          <a className="clickable" onClick={() => showModal(TitusScalingActivitiesModal, { serverGroup }, { maxWidth: '1000px' })}>
            View Scaling Activities
          </a>
        </div>}
      </>
    );
  }
}
