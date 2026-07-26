import React from 'react';

import type { IEcsWizardPageProps } from './common';
import { EcsNetworking } from '../networking/Networking';

export const NetworkingSettings = ({ command, configureCommand, onFieldChange }: IEcsWizardPageProps) => (
  <div data-test-id="EcsServerGroupWizard.networking">
    <EcsNetworking command={command} configureCommand={configureCommand} notifyAngular={onFieldChange} />
  </div>
);
