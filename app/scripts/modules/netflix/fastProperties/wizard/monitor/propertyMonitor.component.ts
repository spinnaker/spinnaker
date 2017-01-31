import {MODAL_CLOSE_COMPONENT} from 'core/modal/buttons/modalClose.component';
import {AUTO_SCROLL_DIRECTIVE} from 'core/presentation/autoScroll/autoScroll.directive';
import {PROPERTY_STATUS_COMPONENT} from './propertyStatus.component';

import {module} from 'angular';

class PropertyMonitorComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./propertyMonitor.html');
  public bindings: any = {
    propertyMonitor: '=monitor'
  };
}

export const PROPERTY_MONITOR_COMPONENT = 'spinnaker.netflix.fastProperty.monitor.component';

module(PROPERTY_MONITOR_COMPONENT, [
  AUTO_SCROLL_DIRECTIVE,
  require('core/modal/modalOverlay.directive.js'),
  MODAL_CLOSE_COMPONENT,
  PROPERTY_STATUS_COMPONENT,
])
  .component('propertyMonitor', new PropertyMonitorComponent());
