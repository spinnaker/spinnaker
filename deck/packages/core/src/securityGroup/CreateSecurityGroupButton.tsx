import * as React from 'react';

import type { IAccountDetails } from '../account';
import type { Application } from '../application';
import { useDeckRuntimeServices } from '../bootstrap/DeckRuntimeContext';
import type { ICloudProviderConfig } from '../cloudProvider';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { SETTINGS } from '../config/settings';
import { FirewallLabels } from './label/FirewallLabels';
import { Tooltip } from '../presentation';
import { noop } from '../utils';

const providerFilterFn = (_application: Application, _account: IAccountDetails, provider: ICloudProviderConfig) => {
  const sgConfig = provider.securityGroup;
  return Boolean(sgConfig && sgConfig.CreateSecurityGroupModal);
};

const getProviderDefaults = (provider: string) => SETTINGS.providers[provider]?.defaults || {};

const getDefaultCredentials = (app: Application, provider: string) =>
  app.defaultCredentials?.[provider] || getProviderDefaults(provider).account;
const getDefaultRegion = (app: Application, provider: string) =>
  app.defaultRegions?.[provider] || getProviderDefaults(provider).region;

const getReactModalOptions = (selectedProvider: string, app: Application) => ({
  credentials: getDefaultCredentials(app, selectedProvider),
  application: app,
  isNew: true,
  region: getDefaultRegion(app, selectedProvider),
});

export const CreateSecurityGroupButton = ({ app }: { app: Application }) => {
  const runtimeServices = useDeckRuntimeServices();

  const createSecurityGroup = (): void => {
    ProviderSelectionService.selectProvider(app, 'securityGroup', providerFilterFn).then((selectedProvider) => {
      const provider = CloudProviderRegistry.getValue(selectedProvider, 'securityGroup');

      provider.CreateSecurityGroupModal.show(getReactModalOptions(selectedProvider, app), runtimeServices);
    }, noop);
  };

  return (
    <div>
      <button className="btn btn-sm btn-default" onClick={createSecurityGroup}>
        <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
        <Tooltip value="Create Load Balancer">
          <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
        </Tooltip>
        <span className="visible-lg-inline"> Create {FirewallLabels.get('Firewall')}</span>
      </button>
    </div>
  );
};
