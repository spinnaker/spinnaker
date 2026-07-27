import type { Application } from '@spinnaker/core';

import type { IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IEcsWizardPageProps {
  application: Application;
  command: IEcsServerGroupCommand;
  configureCommand: (query?: string) => Promise<void>;
  onFieldChange: (field: string, value: any) => void;
}
