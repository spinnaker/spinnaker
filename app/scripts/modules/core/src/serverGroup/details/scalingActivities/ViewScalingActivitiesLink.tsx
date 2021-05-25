import * as React from 'react';
import { IServerGroup } from 'core/domain';
import { showModal } from 'core/presentation';
import { ScalingActivitiesModal } from './ScalingActivitiesModal';

export interface IViewScalingActivitiesLinkProps {
  serverGroup: IServerGroup;
}

export const ViewScalingActivitiesLink = ({ serverGroup }: IViewScalingActivitiesLinkProps) => (
  <a className="clickable" onClick={() => showModal(ScalingActivitiesModal, { serverGroup }, { maxWidth: '1000px' })}>
    View Scaling Activities
  </a>
);
