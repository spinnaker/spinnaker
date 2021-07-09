import { $q } from 'ngimport';
import React from 'react';

import { ManagedWriter } from './ManagedWriter';
import { Application } from '../application';
import { ConfirmationModalService } from '../confirmationModal';
import { IManagedResource, IManagedResourceSummary, ManagedResourceStatus } from '../domain';

import './ManagedResourceStatusIndicator.less';

interface IToggleConfiguration {
  pauseWarning?: JSX.Element;
}

const viewConfigurationByStatus: { [status in ManagedResourceStatus]?: IToggleConfiguration } = {
  ACTUATING: {
    pauseWarning: (
      <p>
        <div className="horizontal top sp-padding-m alert alert-warning">
          <i className="fa fa-exclamation-triangle sp-margin-m-right sp-margin-xs-top" />
          <span>
            Pausing management will not interrupt the action Spinnaker is currently performing to resolve the difference
            from desired state.
          </span>
        </div>
      </p>
    ),
  },
};

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
    bodyContent: <PopoverToggleBodyText resourceSummary={resourceSummary} />,
    account: resourceSummary.locations.account,
    buttonText: `${isPaused ? 'Resume' : 'Pause'} management`,
    submitMethod,
  });
};

const PopoverToggleBodyText = ({ resourceSummary }: { resourceSummary: IManagedResourceSummary }) => {
  const { isPaused, status } = resourceSummary;
  if (isPaused) {
    return (
      <>
        <p>Spinnaker will resume taking action to correct differences from the desired state.</p>
        <MultiRegionWarning resourceSummary={resourceSummary} />
      </>
    );
  } else {
    return (
      <>
        <p>While a resource is paused, Spinnaker will not take action to correct differences from the desired state.</p>
        {viewConfigurationByStatus[status]?.pauseWarning}
        <MultiRegionWarning resourceSummary={resourceSummary} />
      </>
    );
  }
};

const MultiRegionWarning = ({ resourceSummary }: { resourceSummary: IManagedResourceSummary }) => {
  const { isPaused, locations } = resourceSummary;
  const regions = locations.regions.map((r) => r.name).sort();
  if (regions.length < 2) {
    return null;
  }
  return (
    <div className="horizontal top sp-padding-m alert alert-warning">
      <i className="fa fa-exclamation-triangle sp-margin-m-right sp-margin-xs-top" />
      <span>
        {isPaused ? 'Resuming' : 'Pausing'} management of this resource will affect the following regions:{' '}
        <b>{regions.join(', ')}</b>.
      </span>
    </div>
  );
};

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
      {viewConfigurationByStatus[status]?.pauseWarning}
      <MultiRegionWarning resourceSummary={resourceSummary} />
    </>
  );
};
