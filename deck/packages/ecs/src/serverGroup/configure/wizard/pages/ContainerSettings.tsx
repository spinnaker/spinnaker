import React from 'react';

import type { IEcsWizardPageProps } from './common';
import { Container } from '../container/Container';

export const ContainerSettings = ({ command, configureCommand, onFieldChange }: IEcsWizardPageProps) => (
  <div data-test-id="EcsServerGroupWizard.container">
    <Container command={command} configureCommand={configureCommand} notifyAngular={onFieldChange} />
  </div>
);
