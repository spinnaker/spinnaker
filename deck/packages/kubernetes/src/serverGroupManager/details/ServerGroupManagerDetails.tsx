import React from 'react';

import type { Application, IServerGroupManagerDetailsProps, IServerGroupManagerStateParams } from '@spinnaker/core';
import { CloudProviderLogo, Details, EntityNotifications, IfFeatureEnabled, ReactInjector } from '@spinnaker/core';

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

export function ServerGroupManagerDetails(props: IKubernetesServerGroupManagerDetailsProps) {
  const autoClose = () => {
    ReactInjector.$state.params.allowModalToStayOpen = true;
    ReactInjector.$state.go('^', null, { location: 'replace' });
  };
  const [serverGroupManager, manifest, loading] = useKubernetesServerGroupManagerDetails(props, autoClose);

  if (loading) return <Details loading={loading} />;

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
