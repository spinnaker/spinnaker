import React from 'react';

import type { IServerGroup } from '@spinnaker/core';
import { ReactModal } from '@spinnaker/core';

import { EcsServerGroupEventsModal } from './EcsServerGroupEventsModal';

export interface IViewScalingActivitiesLinkProps {
  serverGroup: IServerGroup;
}

export function EventsLink({ serverGroup }: IViewScalingActivitiesLinkProps) {
  const showEvents = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    ReactModal.show(EcsServerGroupEventsModal, { serverGroup });
  };

  return (
    <a href="#" onClick={showEvents}>
      View Events
    </a>
  );
}
