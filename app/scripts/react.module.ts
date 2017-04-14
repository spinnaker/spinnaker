import {module} from 'angular';
import 'ngimport';
import * as ReactGA from 'react-ga';

import {SETTINGS} from 'core/config/settings';

// angular services to make importable
import {StateServiceInject} from 'core/state.service';

// react component wrappers around angular components
import {AccountLabelColorInject} from 'core/account/AccountLabelColor';
import {ButtonBusyIndicatorInject} from 'core/forms/buttonBusyIndicator/ButtonBusyIndicator';
import {CopyToClipboardInject} from 'core/utils/clipboard/CopyToClipboard';
import {ExecutionDetailsInject} from 'core/delivery/details/ExecutionDetails';
import {ExecutionStatusInject} from 'core/delivery/status/ExecutionStatus';
import {PipelineGraphInject} from 'core/pipeline/config/graph/PipelineGraph';

// Initialize React Google Analytics
if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

export const REACT_MODULE = 'spinnaker.react';
module(REACT_MODULE, [
  'bcherny/ngimport',
]).run(function ($injector: any) {
  // Make angular services importable
  StateServiceInject($injector);

  // Convert angular components to react
  AccountLabelColorInject($injector);
  ButtonBusyIndicatorInject($injector);
  CopyToClipboardInject($injector);
  ExecutionDetailsInject($injector);
  ExecutionStatusInject($injector);
  PipelineGraphInject($injector);
});
