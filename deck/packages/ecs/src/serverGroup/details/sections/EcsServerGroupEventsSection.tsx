import React from 'react';

import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { CollapsibleSection } from '@spinnaker/core';

import { EventsLink } from '../../events/EventsLink';

export function EcsServerGroupEventsSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="ECS Events">
      <EventsLink serverGroup={serverGroup} />
    </CollapsibleSection>
  );
}
