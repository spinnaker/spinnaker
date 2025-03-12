import React from 'react';

interface IToggleResourceManagementProps {
  isPaused: boolean;
  isActuating?: boolean;
  regions: string[];
}

export const ActuationWarning = () => (
  <p>
    <div className="horizontal top sp-padding-m alert alert-warning">
      <i className="fa fa-exclamation-triangle sp-margin-m-right sp-margin-xs-top" />
      <span>
        Pausing management will not interrupt the action Spinnaker is currently performing to resolve the difference
        from desired state.
      </span>
    </div>
  </p>
);

export const ToggleResourceManagement = ({ isPaused, isActuating, regions }: IToggleResourceManagementProps) => {
  if (isPaused) {
    return (
      <>
        <p>Spinnaker will resume taking action to correct differences from the desired state.</p>
        <MultiRegionWarning isPaused regions={regions} />
      </>
    );
  } else {
    return (
      <>
        <p>While a resource is paused, Spinnaker will not take action to correct differences from the desired state.</p>
        {isActuating && <ActuationWarning />}
        <MultiRegionWarning isPaused regions={regions} />
      </>
    );
  }
};

export const MultiRegionWarning = ({ isPaused, regions }: Omit<IToggleResourceManagementProps, 'isActuating'>) => {
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
