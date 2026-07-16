import React from 'react';

import type { IEcsWizardPageProps } from './common';
import { ServiceDiscovery } from '../serviceDiscovery/ServiceDiscovery';

export const ServiceDiscoverySettings = ({ command, configureCommand, onFieldChange }: IEcsWizardPageProps) => (
  <div data-test-id="EcsServerGroupWizard.serviceDiscovery">
    <ServiceDiscovery command={command} configureCommand={configureCommand} notifyAngular={onFieldChange} />
  </div>
);
