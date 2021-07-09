import * as React from 'react';

import { ScalingActivitiesModal } from './ScalingActivitiesModal';
import { IServerGroup } from '../../../domain';
import { showModal } from '../../../presentation';

export interface IViewScalingActivitiesLinkProps {
  serverGroup: IServerGroup;
}

export const ViewScalingActivitiesLink = ({ serverGroup }: IViewScalingActivitiesLinkProps) => (
  <a className="clickable" onClick={() => showModal(ScalingActivitiesModal, { serverGroup }, { maxWidth: '1000px' })}>
    View Scaling Activities
  </a>
);
