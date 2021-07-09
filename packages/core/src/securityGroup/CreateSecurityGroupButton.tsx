import * as React from 'react';

import { IAccountDetails } from '../account';
import { Application } from '../application';
import { CloudProviderRegistry, ICloudProviderConfig, ProviderSelectionService } from '../cloudProvider';
import { SETTINGS } from '../config/settings';
import { FirewallLabels } from './label/FirewallLabels';
import { Tooltip } from '../presentation';
import { ModalInjector } from '../reactShims';

const providerFilterFn = (_application: Application, _account: IAccountDetails, provider: ICloudProviderConfig) => {
  const sgConfig = provider.securityGroup;
  return (
    sgConfig &&
    (sgConfig.CreateSecurityGroupModal ||
      (sgConfig.createSecurityGroupTemplateUrl && sgConfig.createSecurityGroupController))
  );
};

const getDefaultCredentials = (app: Application, provider: string) =>
  app.defaultCredentials[provider] || SETTINGS.providers[provider].defaults.account;
const getDefaultRegion = (app: Application, provider: string) =>
  app.defaultRegions[provider] || SETTINGS.providers[provider].defaults.region;

const getAngularModalOptions = (provider: any, selectedProvider: string, app: Application) => ({
  templateUrl: provider.createSecurityGroupTemplateUrl,
  controller: `${provider.createSecurityGroupController} as ctrl`,
  windowClass: 'modal-z-index',
  size: 'lg',
  resolve: {
    securityGroup: () => {
      return {
        credentials: getDefaultCredentials(app, selectedProvider),
        subnet: 'none',
        regions: [getDefaultRegion(app, selectedProvider)],
      };
    },
    application: () => {
      return app;
    },
  },
});

const getReactModalOptions = (selectedProvider: string, app: Application) => ({
  credentials: getDefaultCredentials(app, selectedProvider),
  application: app,
  isNew: true,
});

export const CreateSecurityGroupButton = ({ app }: { app: Application }) => {
  const createSecurityGroup = (): void => {
    ProviderSelectionService.selectProvider(app, 'securityGroup', providerFilterFn).then((selectedProvider) => {
      const provider = CloudProviderRegistry.getValue(selectedProvider, 'securityGroup');

      if (provider.CreateSecurityGroupModal) {
        provider.CreateSecurityGroupModal.show(getReactModalOptions(selectedProvider, app));
      } else {
        // angular
        ModalInjector.modalService.open(getAngularModalOptions(provider, selectedProvider, app)).result.catch(() => {});
      }
    });
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
