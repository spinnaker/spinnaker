import { UISref } from '@uirouter/react';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import { AccountTag } from '../../account';
import type { Application } from '../../application';
import { useDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import { CloudProviderLogo } from '../../cloudProvider';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import type { DirectProviderServiceDelegate } from '../../cloudProvider/providerService.delegate';
import { ConfirmationModalService } from '../../confirmationModal';
import type { IServerGroup } from '../../domain';
import { HealthCounts } from '../../healthCounts';
import { ClusterState } from '../../state';

import './multipleServerGroup.component.less';

export interface IMultipleServerGroupsDetailsProps {
  app: Application;
}

type ServerGroupAction = 'destroyServerGroup' | 'disableServerGroup' | 'enableServerGroup';

interface IConfirmationVerbs {
  futurePerfect: string;
  presentContinuous: string;
  simplePresent: string;
}

function getProvider(serverGroup: IServerGroup): string {
  return serverGroup.provider || serverGroup.type;
}

function isServerGroupDisabled(serverGroup: IServerGroup): boolean {
  return !!serverGroup.disabled;
}

function getDescriptor(serverGroups: IServerGroup[]): string {
  return `${serverGroups.length} server group${serverGroups.length !== 1 ? 's' : ''}`;
}

function getSelectedServerGroups(app: Application): IServerGroup[] {
  return ClusterState.multiselectModel.serverGroups.map((multiselectGroup) => {
    const group = ({ ...multiselectGroup } as unknown) as IServerGroup;
    const match = app.serverGroups.data.find(
      (check: IServerGroup) =>
        check.name === group.name && check.account === group.account && check.region === group.region,
    );

    if (match) {
      group.instanceCounts = { ...match.instanceCounts };
      group.disabled = match.isDisabled;
    }

    return group;
  });
}

function getMixinParams(
  providerServiceDelegate: DirectProviderServiceDelegate,
  submitMethodName: ServerGroupAction,
  serverGroup: IServerGroup,
): any {
  const provider = getProvider(serverGroup);
  const providerParamsMixin = providerServiceDelegate.hasDelegate(provider, 'serverGroup.paramsMixin')
    ? providerServiceDelegate.getDelegate<any>(provider, 'serverGroup.paramsMixin')
    : {};
  const mixinParamsFactory = providerParamsMixin[submitMethodName];

  return mixinParamsFactory === undefined ? {} : mixinParamsFactory(serverGroup);
}

export function MultipleServerGroupsDetails({ app }: IMultipleServerGroupsDetailsProps): JSX.Element {
  const { providerServiceDelegate, serverGroupWriter } = useDeckRuntimeServices();
  const [isDisabled, setIsDisabled] = React.useState(false);
  const [serverGroups, setServerGroups] = React.useState<IServerGroup[]>(() => getSelectedServerGroups(app));

  React.useEffect(() => {
    let active = true;
    const retrieveServerGroups = () => active && setServerGroups(getSelectedServerGroups(app));

    ProviderSelectionService.isDisabled(app).then((disabled) => active && setIsDisabled(disabled));
    const multiselectWatcher = ClusterState.multiselectModel.serverGroupsStream.subscribe(retrieveServerGroups);
    const unsubscribeRefresh = app.serverGroups.onRefresh(null, retrieveServerGroups);
    retrieveServerGroups();

    return () => {
      active = false;
      if (ClusterState.multiselectModel.serverGroups.length > 1) {
        ClusterState.multiselectModel.clearAllServerGroups();
      }
      multiselectWatcher.unsubscribe();
      unsubscribeRefresh();
    };
  }, [app]);

  const confirm = (submitMethodName: ServerGroupAction, verbs: IConfirmationVerbs, groups = serverGroups) => {
    const descriptor = getDescriptor(groups);
    const monitorInterval = groups.length * 1000;
    const taskMonitorConfigs = groups.map((serverGroup) => {
      const mixinParams = getMixinParams(providerServiceDelegate, submitMethodName, serverGroup);
      const writer = serverGroupWriter as any;

      return {
        application: app,
        monitorInterval,
        submitMethod: (params?: any) => writer[submitMethodName](serverGroup, app, { ...params, ...mixinParams }),
        title: serverGroup.name,
      };
    });

    ConfirmationModalService.confirm({
      askForReason: true,
      buttonText: `${verbs.simplePresent} ${descriptor}`,
      header: `Really ${verbs.simplePresent.toLowerCase()} ${descriptor}?`,
      multiTaskTitle: `${verbs.presentContinuous} ${descriptor}`,
      taskMonitorConfigs,
      textToVerify: `${groups.length}`,
      verificationLabel: `Verify the number of server groups (<span class="verification-text">${
        groups.length
      }</span>) to be ${verbs.futurePerfect.toLowerCase()}`,
    });
  };

  const destroyServerGroups = () =>
    confirm('destroyServerGroup', {
      futurePerfect: 'Destroyed',
      presentContinuous: 'Destroying',
      simplePresent: 'Destroy',
    });

  const disableServerGroups = () =>
    confirm(
      'disableServerGroup',
      {
        futurePerfect: 'Disabled',
        presentContinuous: 'Disabling',
        simplePresent: 'Disable',
      },
      serverGroups.filter((group) => !isServerGroupDisabled(group)),
    );

  const enableServerGroups = () =>
    confirm(
      'enableServerGroup',
      {
        futurePerfect: 'Enabled',
        presentContinuous: 'Enabling',
        simplePresent: 'Enable',
      },
      serverGroups.filter(isServerGroupDisabled),
    );

  const actionLink = (label: string, action: () => void) => (
    <MenuItem key={label} onClick={action}>
      {label}
    </MenuItem>
  );
  const canDisable = serverGroups.some((group) => !isServerGroupDisabled(group));
  const canEnable = serverGroups.some(isServerGroupDisabled);

  return (
    <div className="details-panel">
      <div className="header">
        <div className="close-button">
          <UISref to="^">
            <a className="btn btn-link">
              <span className="glyphicon glyphicon-remove" />
            </a>
          </UISref>
        </div>
        <div className="header-text">
          <span className="glyphicon glyphicon-th" />
          <h3>
            {serverGroups.length} Server Group{serverGroups.length !== 1 ? 's' : ''}
          </h3>
        </div>
        {!isDisabled && (
          <div>
            <div className="actions">
              <Dropdown className="dropdown" id="multiple-server-groups-actions-dropdown">
                <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Actions</Dropdown.Toggle>
                <Dropdown.Menu className="dropdown-menu">
                  {actionLink('Destroy', destroyServerGroups)}
                  {canDisable && actionLink('Disable', disableServerGroups)}
                  {canEnable && actionLink('Enable', enableServerGroups)}
                </Dropdown.Menu>
              </Dropdown>
            </div>
          </div>
        )}
      </div>
      <div className="content">
        {serverGroups.map((serverGroup) =>
          React.createElement(
            'multiple-server-group',
            { key: `${serverGroup.account}:${serverGroup.region}:${serverGroup.name}` },
            <h5 key="heading">
              <div className={`server-group-name ${isServerGroupDisabled(serverGroup) ? 'disabled' : ''}`}>
                <CloudProviderLogo provider={getProvider(serverGroup)} height="20px" width="20px" /> {serverGroup.name}
              </div>
            </h5>,
            <div className="server-group-details" key="details">
              <AccountTag account={serverGroup.account} /> {serverGroup.region}
              <HealthCounts container={serverGroup.instanceCounts} />
            </div>,
          ),
        )}
      </div>
    </div>
  );
}
