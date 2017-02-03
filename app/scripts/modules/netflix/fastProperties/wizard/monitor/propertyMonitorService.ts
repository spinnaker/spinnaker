import {module, noop} from 'angular';
import {IPipeline} from 'core/domain/IPipeline';
import {PROPERTY_MONITOR_COMPONENT} from './propertyMonitor.component';

export class PropertyMonitorService {

  public buildMonitor(params: any): any {

    let monitor: any = {
        submitting: false,
        pipeline: null,
        error: false,
        errorMessage: null,
        title: params.title,
        applicationName: params.applicationName,
        onTaskComplete: params.onTaskComplete || noop,
        modalInstance: params.modalInstance,
        monitorInterval: params.monitorInterval || 1000,
        submitMethod: params.submitMethod,
      };

    monitor.onModalClose = noop;

    monitor.modalInstance.result.then(monitor.onModalClose, monitor.onModalClose);

    monitor.closeModal = function () {
      try {
        monitor.modalInstance.dismiss();
      } catch (e) {
        // modal was already closed
      }
    };

    monitor.startSubmit = function () {
      monitor.submitting = true;
      monitor.task = null;
      monitor.error = false;
      monitor.errorMessage = null;
    };

    monitor.setError = function (errorResponse: string): void {
      monitor.errorMessage = `There was an unknown server error. ${errorResponse}`;
      monitor.submitting = false;
      monitor.error = true;
    };

    monitor.handleTaskSuccess = function (pipeline: IPipeline): void {
      monitor.pipeline = pipeline;
    };

    monitor.submit = function (method: any): void {
      monitor.startSubmit();
      let submit = monitor.submitMethod || method;
      submit().then(monitor.handleTaskSuccess, monitor.setError);
    };

    monitor.callPreconfiguredSubmit = (submitParams: any) => {
      monitor.startSubmit();
      monitor.submitMethod(submitParams).then(monitor.handleTaskSuccess, monitor.setError);
    };

    return monitor;
  }
}

export const PROPERTY_MONITOR_SERVICE = 'spinnaker.netflix.fastProperties.monitor.service';

module(PROPERTY_MONITOR_SERVICE, [
 PROPERTY_MONITOR_COMPONENT
])
  .service('propertyMonitorService', PropertyMonitorService);
