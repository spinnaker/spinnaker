import { UISref } from '@uirouter/react';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import { AccountTag } from '../../account';
import type { Application } from '../../application';
import { useDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import { CloudProviderLogo } from '../../cloudProvider';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import { ConfirmationModalService } from '../../confirmationModal';
import type { IHealth, IInstance, IServerGroup } from '../../domain';
import type { IMultiInstanceGroup } from '../instance.write.service';
import { InstanceWriter } from '../instance.write.service';
import { CollapsibleSection } from '../../presentation';
import { ClusterState } from '../../state';

import './multipleInstanceServerGroup.directive.less';

export interface IMultipleInstancesDetailsProps {
  app: Application;
}

interface IConfirmationVerbs {
  futurePerfect: string;
  presentContinuous: string;
  simplePresent: string;
}

function getServerGroup(app: Application, group: IMultiInstanceGroup): IServerGroup {
  return app.serverGroups.data.find(
    (serverGroup: IServerGroup) =>
      serverGroup.name === group.serverGroup &&
      serverGroup.account === group.account &&
      serverGroup.region === group.region,
  );
}

function makeInstanceModel(app: Application, group: IMultiInstanceGroup, instanceId: string): IInstance {
  const serverGroup = getServerGroup(app, group);
  const instance = serverGroup?.instances?.find((check: IInstance) => check.id === instanceId) || ({} as IInstance);

  return {
    availabilityZone: instance.availabilityZone,
    health: instance.health || [],
    healthState: instance.healthState,
    id: instanceId,
    name: instance.name,
  } as IInstance;
}

function getSelectedGroups(app: Application): IMultiInstanceGroup[] {
  return ClusterState.multiselectModel.instanceGroups
    .filter((group) => group.instanceIds.length)
    .map((group) => {
      const parentServerGroup = getServerGroup(app, group);

      return {
        account: group.account,
        cloudProvider: group.cloudProvider,
        instanceIds: group.instanceIds,
        instances: group.instanceIds.map((instanceId: string) => makeInstanceModel(app, group, instanceId)),
        loadBalancers: parentServerGroup ? parentServerGroup.loadBalancers || [] : [],
        region: group.region,
        serverGroup: group.serverGroup,
      };
    });
}

function getDiscoveryState(instance: IInstance): string {
  return (instance.health || []).find((health: IHealth) => health.type === 'Discovery')?.state;
}

export function MultipleInstancesDetails({ app }: IMultipleInstancesDetailsProps): JSX.Element {
  const { providerServiceDelegate } = useDeckRuntimeServices();
  const [isDisabled, setIsDisabled] = React.useState(false);
  const [selectedGroups, setSelectedGroups] = React.useState<IMultiInstanceGroup[]>(() => getSelectedGroups(app));

  React.useEffect(() => {
    let active = true;
    const retrieveInstances = () => active && setSelectedGroups(getSelectedGroups(app));

    ProviderSelectionService.isDisabled(app).then((disabled) => active && setIsDisabled(disabled));
    const multiselectWatcher = ClusterState.multiselectModel.instancesStream.subscribe(retrieveInstances);
    const unsubscribeRefresh = app.serverGroups.onRefresh(null, retrieveInstances);
    retrieveInstances();

    return () => {
      active = false;
      ClusterState.multiselectModel.deselectAllInstances();
      multiselectWatcher.unsubscribe();
      unsubscribeRefresh();
    };
  }, [app]);

  const instancesCount = selectedGroups.reduce((acc, group) => acc + group.instanceIds.length, 0);
  const descriptor = `${instancesCount} instance${instancesCount > 1 ? 's' : ''}`;

  const confirm = (submitMethod: () => PromiseLike<any>, verbs: IConfirmationVerbs, body?: string) => {
    ConfirmationModalService.confirm({
      body,
      buttonText: `${verbs.simplePresent} ${descriptor}`,
      header: `Really ${verbs.simplePresent.toLowerCase()} ${descriptor}?`,
      submitMethod,
      taskMonitorConfig: {
        application: app,
        title: `${verbs.presentContinuous} ${descriptor}`,
      },
      textToVerify: `${instancesCount}`,
      verificationLabel: `Verify the number of instances (<span class="verification-text">${instancesCount}</span>) to be ${verbs.futurePerfect.toLowerCase()}`,
    });
  };

  const allDiscoveryHealthsMatch = (state: string) =>
    selectedGroups.every((group) => group.instances.every((instance) => getDiscoveryState(instance) === state));

  const getAllLoadBalancers = () => {
    if (!selectedGroups.length) {
      return [];
    }

    const base = (selectedGroups[0].loadBalancers || []).slice().sort().join(' ');
    if (selectedGroups.every((group) => (group.loadBalancers || []).slice().sort().join(' ') === base)) {
      return selectedGroups[0].loadBalancers || [];
    }

    return [];
  };

  const canRegisterWithLoadBalancers = () =>
    getAllLoadBalancers().length !== 0 &&
    selectedGroups.every((group) =>
      group.instances.every((instance) => (instance.health || []).every((health) => health.type !== 'LoadBalancer')),
    );

  const canDeregisterFromLoadBalancers = () => {
    const allLoadBalancers = getAllLoadBalancers().slice().sort().join(' ');

    return selectedGroups.every((group) =>
      group.instances.every((instance) =>
        (instance.health || []).some((health: IHealth) => {
          const healthLoadBalancers = (health.loadBalancers || [])
            .map((lb) => lb.name)
            .sort()
            .join(' ');

          return health.type === 'LoadBalancer' && allLoadBalancers === healthLoadBalancers;
        }),
      ),
    );
  };

  const confirmLoadBalancerAction = (
    submitMethod: () => PromiseLike<any>,
    verbs: IConfirmationVerbs,
    action: 'registered' | 'deregistered',
  ) => {
    const allLoadBalancers = getAllLoadBalancers().slice().sort();

    confirm(
      submitMethod,
      verbs,
      `<p>Instances will be ${action} ${
        action === 'registered' ? 'with' : 'from'
      } the following load balancers: <b>${allLoadBalancers.join(', ')}</b></p>`,
    );
  };

  const registerWithDiscovery = () =>
    confirm(() => InstanceWriter.enableInstancesInDiscovery(selectedGroups, app, providerServiceDelegate), {
      futurePerfect: 'Registered',
      presentContinuous: 'Registering',
      simplePresent: 'Register',
    });

  const deregisterWithDiscovery = () =>
    confirm(() => InstanceWriter.disableInstancesInDiscovery(selectedGroups, app, providerServiceDelegate), {
      futurePerfect: 'Deregistered',
      presentContinuous: 'Deregistering',
      simplePresent: 'Deregister',
    });

  const registerWithLoadBalancers = () => {
    const allLoadBalancers = getAllLoadBalancers().slice().sort();

    confirmLoadBalancerAction(
      () =>
        InstanceWriter.registerInstancesWithLoadBalancer(
          selectedGroups,
          app,
          allLoadBalancers,
          providerServiceDelegate,
        ),
      {
        futurePerfect: 'Registered',
        presentContinuous: 'Registering',
        simplePresent: 'Register',
      },
      'registered',
    );
  };

  const deregisterFromLoadBalancers = () => {
    const allLoadBalancers = getAllLoadBalancers().slice().sort();

    confirmLoadBalancerAction(
      () =>
        InstanceWriter.deregisterInstancesFromLoadBalancer(
          selectedGroups,
          app,
          allLoadBalancers,
          providerServiceDelegate,
        ),
      {
        futurePerfect: 'Deregistered',
        presentContinuous: 'Deregistering',
        simplePresent: 'Deregister',
      },
      'deregistered',
    );
  };

  const rebootInstances = () =>
    confirm(() => InstanceWriter.rebootInstances(selectedGroups, app, providerServiceDelegate), {
      futurePerfect: 'Rebooted',
      presentContinuous: 'Rebooting',
      simplePresent: 'Reboot',
    });

  const terminateInstances = () =>
    confirm(() => InstanceWriter.terminateInstances(selectedGroups, app, providerServiceDelegate), {
      futurePerfect: 'Terminated',
      presentContinuous: 'Terminating',
      simplePresent: 'Terminate',
    });

  const terminateInstancesAndShrinkServerGroups = () =>
    confirm(
      () => InstanceWriter.terminateInstancesAndShrinkServerGroups(selectedGroups, app, providerServiceDelegate),
      {
        futurePerfect: 'Terminated',
        presentContinuous: 'Terminating',
        simplePresent: 'Terminate',
      },
    );

  const actionLink = (label: string, action: () => void) => (
    <MenuItem key={label} onClick={action}>
      {label}
    </MenuItem>
  );

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
          <span className="glyphicon glyphicon-hdd" />
          <h3>
            {instancesCount} Instance{instancesCount !== 1 ? 's' : ''}
          </h3>
        </div>
        {!isDisabled && (
          <div>
            <div className="actions">
              <Dropdown className="dropdown" id="multiple-instances-actions-dropdown">
                <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Actions</Dropdown.Toggle>
                <Dropdown.Menu className="dropdown-menu">
                  {allDiscoveryHealthsMatch('OutOfService') && actionLink('Enable in Discovery', registerWithDiscovery)}
                  {(allDiscoveryHealthsMatch('Up') || allDiscoveryHealthsMatch('Down')) &&
                    actionLink('Disable in Discovery', deregisterWithDiscovery)}
                  {canRegisterWithLoadBalancers() &&
                    actionLink('Register with Load Balancer', registerWithLoadBalancers)}
                  {canDeregisterFromLoadBalancers() &&
                    actionLink('Deregister from Load Balancer', deregisterFromLoadBalancers)}
                  <MenuItem divider={true} />
                  {actionLink('Reboot', rebootInstances)}
                  {actionLink('Terminate', terminateInstances)}
                  {selectedGroups.every((group) => group.cloudProvider === 'aws') &&
                    actionLink('Terminate and Shrink Server Groups', terminateInstancesAndShrinkServerGroups)}
                </Dropdown.Menu>
              </Dropdown>
            </div>
          </div>
        )}
      </div>
      <div className="content">
        <CollapsibleSection heading="Server Groups" defaultExpanded={true}>
          {selectedGroups.map((group) => (
            <div
              className="multiple-instance-server-group"
              key={`${group.account}:${group.region}:${group.serverGroup}`}
            >
              <h5>
                <div className="server-group-name">
                  <CloudProviderLogo provider={group.cloudProvider} height="16px" width="16px" /> {group.serverGroup}
                </div>
              </h5>
              <div className="server-group-details">
                <AccountTag account={group.account} /> {group.region}
              </div>
              <div className="multiple-instance-list">
                {group.instances.length} instance{group.instances.length !== 1 ? 's' : ''}
                <ul>
                  {group.instances.map((instance) => (
                    <li key={instance.id}>
                      <span className={`glyphicon glyphicon-${instance.healthState}-triangle`} />{' '}
                      {instance.name || instance.id}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </CollapsibleSection>
      </div>
    </div>
  );
}
