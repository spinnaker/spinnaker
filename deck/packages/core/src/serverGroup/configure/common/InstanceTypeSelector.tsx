import React from 'react';

import { AngularJSAdapter } from '../../../reactShims';
import type { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface IInstanceTypeSelectorProps {
  command: IServerGroupCommand;
  onTypeChanged: (type: string) => void;
}

export function InstanceTypeSelector(props: IInstanceTypeSelectorProps) {
  return (
    <AngularJSAdapter
      template={`
        <v2-instance-type-selector
          command="props.command"
          on-type-changed="props.onTypeChanged">
        </v2-instance-type-selector>
      `}
      locals={props}
    />
  );
}
