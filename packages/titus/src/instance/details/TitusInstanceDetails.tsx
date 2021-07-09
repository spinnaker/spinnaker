import * as React from 'react';

import { IAmazonHealth, InstanceStatus } from '@spinnaker/amazon';
import {
  AccountService,
  Action,
  Application,
  CollapsibleSection,
  ConsoleOutputLink,
  IAccountDetails,
  IInstanceDetailsProps,
  IMoniker,
  InstanceActions,
  InstanceDetailsHeader,
  InstanceDetailsPane,
  InstanceInsights,
  InstanceLinks,
  InstanceReader,
  IOverridableProps,
  overridableComponent,
  RecentHistoryService,
  SETTINGS,
  Spinner,
  useData,
} from '@spinnaker/core';

import { TitusInstanceDns } from './TitusInstanceDns';
import { TitusInstanceInformation } from './TitusInstanceInformation';
import { ITitusInstance, ITitusServerGroup, ITitusServerGroupView } from '../../domain';
import { TitusSecurityGroupsDetailsSection } from '../../serverGroup/details/TitusSecurityGroups';
import { buildTaskActions, extractHealthMetrics } from './titusInstanceDetailsUtils';

export interface ITitusInstanceDetailsProps extends IInstanceDetailsProps {
  instance: { instanceId: string };
}

export interface ITitusInstanceDetailsContentProps {
  account: IAccountDetails;
  actions: Action[];
  app: Application;
  baseIpAddress: string;
  environment: string;
  healthMetrics: IAmazonHealth[];
  instance: ITitusInstance;
  instanceId: string;
  instancePort: string;
  loading: boolean;
  moniker: IMoniker;
  serverGroup: ITitusServerGroup;
  region: string;
}

const TitusInstanceDetailsContent = ({
  account,
  actions,
  app,
  baseIpAddress,
  environment,
  healthMetrics,
  instance,
  instanceId,
  instancePort,
  loading,
  moniker,
  serverGroup,
  region,
}: ITitusInstanceDetailsContentProps) => {
  const defaultSshLink = `ssh -t ${account?.bastionHost} 'titus-ssh -region ${region} ${instanceId}'`;
  return (
    <div className="details-panel">
      <div className="header">
        <InstanceDetailsHeader
          healthState={instance.healthState}
          instanceId={instanceId}
          loading={loading}
          sshLink={defaultSshLink}
          standalone={app.isStandalone}
        />
        <div className="actions">
          <InstanceActions actions={actions} />
          {Boolean(instance?.insightActions?.length) && (
            <InstanceInsights analytics={true} insights={instance.insightActions} instance={instance} />
          )}
        </div>
      </div>
      <div className="content">
        <TitusInstanceInformation instance={instance} titusUiEndpoint={instance.titusUiEndpoint} />
        <InstanceStatus
          healthMetrics={healthMetrics}
          healthState={instance.healthState}
          metricTypes={['TargetGroup']}
          privateIpAddress={instance.privateIpAddress}
        />
        <TitusInstanceDns
          containerIp={instance.placement?.containerIp}
          host={instance.placement?.host}
          instancePort={instancePort}
          ipv6Address={instance.ipv6Address}
        />
        <div className="collapsible-section">
          <TitusSecurityGroupsDetailsSection app={app} serverGroup={serverGroup as ITitusServerGroupView} />
        </div>
        <CollapsibleSection heading="Console Output">
          <ConsoleOutputLink instance={instance} />
        </CollapsibleSection>
        <InstanceLinks
          address={baseIpAddress}
          application={app}
          instance={instance}
          environment={environment}
          moniker={moniker}
        />
      </div>
    </div>
  );
};

const OverridableTitusInstanceDetailsContent = overridableComponent<
  ITitusInstanceDetailsContentProps & IOverridableProps,
  typeof TitusInstanceDetailsContent
>(TitusInstanceDetailsContent, 'titus.instance.details.content');

export const TitusInstanceDetails = ({ app, environment, instance, moniker }: ITitusInstanceDetailsProps) => {
  const { instanceId } = instance;
  const serverGroup = app.serverGroups.data.find((sg: ITitusServerGroup) =>
    sg.instances.some((possibleMatch) => possibleMatch.id === instanceId),
  );
  const { account, region } = serverGroup;

  const retrieveInstance = () => {
    RecentHistoryService.addExtraDataToLatest('instances', {
      account,
      region,
      serverGroup: serverGroup.name,
    });

    return Promise.all([
      InstanceReader.getInstanceDetails(account, region, instanceId).then((instance) => ({
        ...instance,
        account,
        region,
      })),
      AccountService.getAccountDetails(account),
    ]);
  };

  const addTitusUiEndpoint = (result: [ITitusInstance, IAccountDetails]) => {
    const [instance, account] = result;
    const regionDetails = (account?.regions || []).find((r) => r.name === region);

    return [{ titusUiEndpoint: regionDetails?.endpoint, ...instance }, account];
  };

  const {
    result: [currentInstance, currentAccount],
    status,
  } = useData(
    () => {
      if (app.isStandalone) {
        return retrieveInstance().then((result) => addTitusUiEndpoint(result as [ITitusInstance, IAccountDetails]));
      } else {
        return app.serverGroups
          .ready()
          .then(retrieveInstance)
          .then((result: [ITitusInstance, IAccountDetails]) => addTitusUiEndpoint(result));
      }
    },
    [{} as ITitusInstance, {} as IAccountDetails],
    [instanceId, serverGroup],
  );

  const taskActions = buildTaskActions(currentInstance, app);
  const healthMetrics = extractHealthMetrics(
    currentInstance.health as IAmazonHealth[],
    app.loadBalancers.data,
    currentAccount.awsAccount,
  );

  const isLoading = status === 'PENDING';
  const instancePort = app.attributes?.instancePort || SETTINGS.defaultInstancePort || '80';
  const baseIpAddress = currentInstance.placement?.containerIp || currentInstance?.placement?.host;

  if (isLoading) {
    return (
      <InstanceDetailsPane>
        <div className="horizontal center middle">
          <Spinner size="small" />
        </div>
      </InstanceDetailsPane>
    );
  }

  if (!currentInstance?.id) {
    return (
      <div className="details-panel">
        <div className="header">
          <InstanceDetailsPane>
            <div className="header-text horizontal middle">
              <h3 className="horizontal middle space-between flex-1">{instance.instanceId}</h3>
            </div>
          </InstanceDetailsPane>
        </div>
        <div className="content">
          <div className="content-section">
            <div className="content-body text-center">
              <h3>Instance not found.</h3>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <OverridableTitusInstanceDetailsContent
      account={currentAccount}
      actions={taskActions}
      app={app}
      baseIpAddress={baseIpAddress}
      environment={environment}
      healthMetrics={healthMetrics}
      instance={currentInstance as ITitusInstance}
      instanceId={instance.instanceId}
      instancePort={instancePort}
      moniker={moniker}
      loading={isLoading}
      serverGroup={serverGroup}
      region={region}
    />
  );
};
