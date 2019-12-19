import { IManagedResourceSummary, ManagedResourceStatus } from 'core/domain';
import { Application } from 'core/application';

import { ManagedWriter } from './ManagedWriter';

import './ManagedResourceStatusIndicator.less';
import { ReactInjector } from 'core/reactShims';

interface IToggleConfiguration {
  popoverPauseWarning?: string;
}

const viewConfigurationByStatus: { [status in ManagedResourceStatus]?: IToggleConfiguration } = {
  ACTUATING: {
    popoverPauseWarning: `<p>
          <div class="horizontal top sp-padding-m alert alert-warning">
            <i class="fa fa-exclamation-triangle sp-margin-m-right sp-margin-xs-top"></i>
            <span>Pausing management will not interrupt the action Spinnaker is currently performing to resolve the
            drift in configuration.</span>
          </div>
        </p>`,
  },
};

export const toggleResourcePause = (
  resourceSummary: IManagedResourceSummary,
  application: Application,
  hidePopover: () => void,
) => {
  hidePopover();
  const { id, isPaused } = resourceSummary;
  const toggle = () =>
    isPaused ? ManagedWriter.resumeResourceManagement(id) : ManagedWriter.pauseResourceManagement(id);

  const submitMethod = () => toggle().then(() => application.managedResources.refresh(true));

  return ReactInjector.confirmationModalService.confirm({
    header: `Really ${isPaused ? 'resume' : 'pause'} resource management?`,
    body: getPopoverToggleBodyText(resourceSummary),
    account: resourceSummary.locations.account,
    buttonText: `${isPaused ? 'Resume' : 'Pause'} management`,
    submitMethod,
  });
};

const getPopoverToggleBodyText = (resourceSummary: IManagedResourceSummary) => {
  const { isPaused, locations, status } = resourceSummary;
  const regions = locations.regions.map(r => r.name).sort();
  let body = '';
  if (!isPaused) {
    body += `
        <p>
          While a resource is paused, Spinnaker will not take action to resolve drift from the declarative configuration.
        </p>`;
    body += viewConfigurationByStatus[status]?.popoverPauseWarning ?? '';
  } else {
    body += `
        <p>
          Spinnaker will resume taking action to resolve drift from the declarative configuration.
        </p>
      `;
  }
  if (regions.length > 1) {
    body += `
        <p>
          <div class="horizontal top sp-padding-m alert alert-warning">
            <i class="fa fa-exclamation-triangle sp-margin-m-right sp-margin-xs-top"></i>
            <span>${
              isPaused ? 'Resuming' : 'Pausing'
            } management of this resource will affect the following regions: <b>${regions.join(', ')}</b>.
            </span>
          </div>
        </p>`;
  }
  return body;
};
