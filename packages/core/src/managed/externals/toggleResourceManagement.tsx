import { $q } from 'ngimport';
import React from 'react';

import { ManagedWriter } from '../ManagedWriter';
import type { Application } from '../../application';
import { ConfirmationModalService } from '../../confirmationModal';
import type { IManagedResource, IManagedResourceSummary } from '../../domain';
import { ManagedResourceStatus } from '../../domain';
import { ActuationWarning, MultiRegionWarning, ToggleResourceManagement } from '../resources/ToggleResourceManagement';

import './ManagedResourceStatusIndicator.less';

/***
 * If the resource is not managed, or management is paused, this will return an immediate promise with a true value.
 * If the resource is managed, an interstitial modal will prompt the user to pause resource management. The promise will
 * then resolve with true if the user paused resource management, or false if they chose not to pause management.
 * @param resource
 * @param application
 */
export const confirmNotManaged = (resource: IManagedResource, application: Application): PromiseLike<boolean> => {
  const { managedResourceSummary, isManaged } = resource;
  if (!isManaged || !managedResourceSummary || managedResourceSummary.isPaused) {
    return $q.when(true);
  }
  const submitMethod = () => {
    return ManagedWriter.pauseResourceManagement(managedResourceSummary.id).then(() =>
      application.managedResources.refresh(true),
    );
  };
  return ConfirmationModalService.confirm({
    header: `Pause Management?`,
    bodyContent: <BodyText resourceSummary={managedResourceSummary} />,
    account: managedResourceSummary.locations.account,
    buttonText: 'Pause management',
    submitMethod,
  }).then(
    () => true,
    () => false,
  );
};

export const toggleResourcePause = (
  resourceSummary: IManagedResourceSummary,
  application: Application,
  hidePopover?: () => void,
) => {
  hidePopover?.();
  const { id, isPaused } = resourceSummary;
  const toggle = () =>
    isPaused ? ManagedWriter.resumeResourceManagement(id) : ManagedWriter.pauseResourceManagement(id);

  const submitMethod = () => toggle().then(() => application.managedResources.refresh(true));

  return ConfirmationModalService.confirm({
    header: `Really ${isPaused ? 'resume' : 'pause'} resource management?`,
    bodyContent: (
      <ToggleResourceManagement
        isActuating={resourceSummary.status === ManagedResourceStatus.ACTUATING}
        isPaused={isPaused}
        regions={getRegions(resourceSummary)}
      />
    ),
    account: resourceSummary.locations.account,
    buttonText: `${isPaused ? 'Resume' : 'Pause'} management`,
    submitMethod,
  });
};

const getRegions = (resourceSummary: IManagedResourceSummary) =>
  resourceSummary.locations.regions.map((r) => r.name).sort();

const BodyText = ({ resourceSummary }: { resourceSummary: IManagedResourceSummary }) => {
  const { status } = resourceSummary;
  return (
    <>
      <p>
        🌈 <b>Spinnaker is managing this resource.</b>
      </p>
      <p>
        If you need to temporarily stop Spinnaker from managing this resource — for example, if something is wrong and
        manual intervention is required — you can pause management and resume it later.
      </p>
      {status === ManagedResourceStatus.ACTUATING && <ActuationWarning />}
      <MultiRegionWarning isPaused regions={getRegions(resourceSummary)} />
    </>
  );
};
