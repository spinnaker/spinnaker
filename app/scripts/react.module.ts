import {module} from 'angular';
import 'ngimport';
import * as ReactGA from 'react-ga';

import {SETTINGS} from './modules/core/config/settings';

// angular services to make importable
import {CANCEL_MODAL_SERVICE, CancelModalServiceInject} from './modules/core/cancelModal/cancelModal.service';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalServiceInject} from './modules/core/confirmationModal/confirmationModal.service';
import {EXECUTION_FILTER_MODEL, ExecutionFilterModelInject} from './modules/core/delivery/filter/executionFilter.model';
import {EXECUTION_SERVICE, ExecutionServiceInject} from './modules/core/delivery/service/execution.service';
import {SCHEDULER_FACTORY, SchedulerFactoryInject} from './modules/core/scheduler/scheduler.factory';
import {StateServiceInject} from './modules/core/state.service';

// react component wrappers around angular components
import {ButtonBusyIndicatorInject} from './modules/core/forms/buttonBusyIndicator/ButtonBusyIndicator';

// Initialize React Google Analytics
if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

export const REACT_MODULE = 'spinnaker.react';
module(REACT_MODULE, [
  'bcherny/ngimport',
  CANCEL_MODAL_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  EXECUTION_FILTER_MODEL,
  EXECUTION_SERVICE,
  SCHEDULER_FACTORY
]).run(function ($injector: any) {
  // Make angular services importable
  CancelModalServiceInject($injector);
  ConfirmationModalServiceInject($injector);
  ExecutionFilterModelInject($injector);
  ExecutionServiceInject($injector);
  SchedulerFactoryInject($injector);
  StateServiceInject($injector);

  // Convert angular components to react
  ButtonBusyIndicatorInject($injector);
});
