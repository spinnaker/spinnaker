import { IComponentOptions, IController, module } from 'angular';
import { Application } from '../../application/application.model';
import { IServerGroup } from '../../domain';

class ServerGroupRunningTasksCtrl implements IController {
  public serverGroup: IServerGroup;
  public application: Application;
}

const serverGroupRunningTasksComponent: IComponentOptions = {
  bindings: {
    serverGroup: '<',
    application: '<',
  },
  controller: ServerGroupRunningTasksCtrl,
  template: `
    <collapsible-section heading="Running Tasks" expanded="true" body-class="details-running-tasks" ng-if="$ctrl.serverGroup.runningTasks.length > 0 || $ctrl.serverGroup.runningExecutions.length > 0">
      <div class="container-fluid no-padding" ng-repeat="task in $ctrl.serverGroup.runningTasks | orderBy:'-startTime'">
        <div class="row">
          <div class="col-md-12">
            <strong>
              {{task.name}}
            </strong>
          </div>
        </div>
        <div class="row" ng-repeat="step in task.steps | displayableTasks">
          <div class="col-md-7 col-md-offset-0">
            <span class="small"><status-glyph item="step"></status-glyph></span> {{step.name | robotToHuman }}
            <platform-health-override-message ng-if="step.name === 'waitForUpInstances'"
                                              step="step" task="task"
                                              application="$ctrl.application"></platform-health-override-message>
          </div>
          <div class="col-md-4 text-right">
            {{step.runningTimeInMs | duration }}
          </div>
        </div>
      </div>

      <div class="container-fluid no-padding" ng-repeat="execution in $ctrl.serverGroup.runningExecutions">
        <div class="row">
          <div class="col-md-12">
            <strong>
              Pipeline: {{execution.name}}
            </strong>
          </div>
        </div>
        <div class="row" ng-repeat="stage in execution.stages">
          <div class="col-md-7 col-md-offset-0">
            <span class="small"><status-glyph item="stage"></status-glyph></span> {{stage.name | robotToHuman }}
          </div>
          <div class="col-md-4 text-right">
            {{stage.runningTimeInMs | duration }}
          </div>
        </div>
      </div>
    </collapsible-section>
  `,
};

export const RUNNING_TASKS_DETAILS_COMPONENT = 'spinnaker.core.serverGroup.details.runningTasks.component';
module(RUNNING_TASKS_DETAILS_COMPONENT, []).component(
  'serverGroupRunningTasksDetails',
  serverGroupRunningTasksComponent,
);
