import {module} from 'angular';

class PropertyStatusComponent implements ng.IComponentOptions {
  public template = `
        <div style="display: flex; align-items: center; justify-content: center; height:100%">
          <p ng-if="$ctrl.monitor.pipeline && !$ctrl.monitor.error" style="font-size: 20px">
              The Property Pipeline has been started. You can

              <a ng-if="$ctrl.applicationName !== 'spinnakerfp'" ui-sref="home.applications.application.pipelines.executionDetails.execution({application: $ctrl.applicationName, executionId: $ctrl.monitor.pipeline.ref.split('/')[2]})">
              monitor this pipeline here.</a>

              <a ng-if="$ctrl.applicationName === 'spinnakerfp'" ui-sref="home.data.executions.execution({application: $ctrl.applicationName, executionId: $ctrl.monitor.pipeline.ref.split('/')[2]})">
              monitor this pipeline here.</a>
          </p>
        </div>
        `;
  public bindings: any = {
    monitor: '=',
    applicationName: '='
  };
}

export const PROPERTY_STATUS_COMPONENT = 'spinnaker.netflix.fastProperty.monitor.propertyStatus.component';

module(PROPERTY_STATUS_COMPONENT, [])
  .component('propertyMonitorStatus', new PropertyStatusComponent());
