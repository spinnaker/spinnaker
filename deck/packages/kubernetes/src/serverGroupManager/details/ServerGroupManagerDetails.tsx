import React from 'react';

import type {
  Application,
  IRouterInjectedProps,
  IServerGroupManagerDetailsProps,
  IServerGroupManagerStateParams,
} from '@spinnaker/core';
import { CloudProviderLogo, Details, EntityNotifications, IfFeatureEnabled, withRouter } from '@spinnaker/core';

import { ServerGroupManagerActions } from './ServerGroupManagerActions';
import {
  ServerGroupManagerAnnotationCustomSection,
  ServerGroupManagerArtifactsSection,
  ServerGroupManagerEventsSection,
  ServerGroupManagerInformationSection,
  ServerGroupManagerLabelsSection,
  ServerGroupManagerManifestConditionSection,
  ServerGroupManagerManifestStatusSection,
} from './sections';
import { useKubernetesServerGroupManagerDetails } from './useKubernetesServerGroupManagerDetails';

export interface IKubernetesServerGroupManagerDetailsProps extends IServerGroupManagerDetailsProps {
  app: Application;
  serverGroupManager: IServerGroupManagerStateParams;
}

export function closeServerGroupManagerDetails(stateService: IRouterInjectedProps['stateService']): void {
  stateService.params.allowModalToStayOpen = true;
  stateService.go('^', null, { location: 'replace' });
}

export function ServerGroupManagerDetailsComponent(
  props: IKubernetesServerGroupManagerDetailsProps & IRouterInjectedProps,
) {
  const autoClose = () => closeServerGroupManagerDetails(props.stateService);
  const [serverGroupManager, manifest, loading] = useKubernetesServerGroupManagerDetails(props, autoClose);

  if (loading || !serverGroupManager || !manifest) return <Details loading={true} />;

  return (
    <Details loading={loading}>
      <Details.Header
        icon={<CloudProviderLogo provider={props.serverGroupManager.provider} height="36px" width="36px" />}
        name={serverGroupManager.displayName}
        notifications={
          <IfFeatureEnabled
            feature="entityTags"
            render={
              <EntityNotifications
                entity={serverGroupManager}
                application={props.app}
                placement="bottom"
                hOffsetPercent="90%"
                entityType="serverGroupManager"
                pageLocation="details"
                onUpdate={() => props.app.serverGroupManagers.refresh()}
              />
            }
          />
        }
        actions={
          <ServerGroupManagerActions
            application={props.app}
            serverGroupManager={serverGroupManager}
            manifest={manifest}
          />
        }
      />
      <Details.Content loading={loading}>
        <ServerGroupManagerManifestStatusSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerInformationSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerManifestConditionSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerAnnotationCustomSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerEventsSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerLabelsSection serverGroupManager={serverGroupManager} manifest={manifest} />
        <ServerGroupManagerArtifactsSection serverGroupManager={serverGroupManager} manifest={manifest} />
      </Details.Content>
    </Details>
  );
}

export const ServerGroupManagerDetails = withRouter(ServerGroupManagerDetailsComponent);
