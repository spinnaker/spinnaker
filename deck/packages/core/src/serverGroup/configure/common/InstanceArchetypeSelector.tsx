import React from 'react';

import { AngularJSAdapter } from '../../../reactShims';
import type { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface IInstanceArchetypeSelectorProps {
  command: IServerGroupCommand;
  onProfileChanged: (profile: string) => void;
  onTypeChanged: (type: string) => void;
}

export function InstanceArchetypeSelector(props: IInstanceArchetypeSelectorProps) {
  return (
    <AngularJSAdapter
      template={`
        <v2-instance-archetype-selector
          command="props.command"
          on-profile-changed="props.onProfileChanged"
          on-type-changed="props.onTypeChanged">
        </v2-instance-archetype-selector>
      `}
      locals={props}
    />
  );
}
