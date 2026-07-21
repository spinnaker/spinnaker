import * as React from 'react';

import type { IAccountDetails } from '../account';
import { AngularServices } from '../angular/services';
import type { Application } from '../application';
import type { ICloudProviderConfig } from '../cloudProvider';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { SETTINGS } from '../config/settings';
import { FirewallLabels } from './label/FirewallLabels';
import { Tooltip } from '../presentation';
import { noop } from '../utils';

const providerFilterFn = (_application: Application, _account: IAccountDetails, provider: ICloudProviderConfig) => {
  const sgConfig = provider.securityGroup;
  return (
    sgConfig &&
    (sgConfig.CreateSecurityGroupModal ||
      (sgConfig.createSecurityGroupTemplateUrl && sgConfig.createSecurityGroupController))
  );
};

const getProviderDefaults = (provider: string) => SETTINGS.providers[provider]?.defaults || {};

const getDefaultCredentials = (app: Application, provider: string) =>
  app.defaultCredentials?.[provider] || getProviderDefaults(provider).account;
const getDefaultRegion = (app: Application, provider: string) =>
  app.defaultRegions?.[provider] || getProviderDefaults(provider).region;

interface IAngularCreateSecurityGroupCommand {
  credentials: string | undefined;
  subnet: string;
  regions: Array<string | undefined>;
  vpcId: string | null;
  securityGroupIngress: unknown[];
}

const getAngularModalOptions = (provider: any, selectedProvider: string, app: Application) => ({
  templateUrl: provider.createSecurityGroupTemplateUrl,
  controller: `${provider.createSecurityGroupController} as ctrl`,
  windowClass: 'modal-z-index',
  size: 'lg',
  resolve: {
    securityGroup: (): IAngularCreateSecurityGroupCommand => {
      return {
        credentials: getDefaultCredentials(app, selectedProvider),
        subnet: 'none',
        regions: [getDefaultRegion(app, selectedProvider)],
        vpcId: null,
        securityGroupIngress: [],
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
  region: getDefaultRegion(app, selectedProvider),
});

export const CreateSecurityGroupButton = ({ app }: { app: Application }) => {
  const createSecurityGroup = (): void => {
    ProviderSelectionService.selectProvider(app, 'securityGroup', providerFilterFn).then((selectedProvider) => {
      const provider = CloudProviderRegistry.getValue(selectedProvider, 'securityGroup');

      if (provider.CreateSecurityGroupModal) {
        provider.CreateSecurityGroupModal.show(getReactModalOptions(selectedProvider, app));
      } else {
        // angular
        AngularServices.modalService.open(getAngularModalOptions(provider, selectedProvider, app)).result.catch(noop);
      }
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
