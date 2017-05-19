import { IComponentOptions, module } from 'angular';

const scheduledActionComponent: IComponentOptions = {
  bindings: {
    action: '='
  },
  controller: () => {},
  template: `
    <dl class="horizontal-when-filters-collapsed" style="margin-bottom: 20px">
      <dt>Schedule</dt>
      <dd>
        {{$ctrl.action.recurrence}}
      </dd>
      <dt ng-if="action.minSize !== undefined">Min Size</dt>
      <dd ng-if="action.minSize !== undefined">
        {{$ctrl.action.minSize}}
      </dd>
      <dt ng-if="action.maxSize !== undefined">Max Size</dt>
      <dd ng-if="action.maxSize !== undefined">
        {{$ctrl.action.maxSize}}
      </dd>
      <dt ng-if="action.desiredCapacity !== undefined">Desired Size</dt>
      <dd ng-if="action.desiredCapacity !== undefined">
        {{$ctrl.action.desiredCapacity}}
      </dd>
    </dl>
  `
};

export const AWS_SCHEDULED_ACTION_COMPONENT = 'spinnaker.amazon.serverGroup.scheduledAction';
module(AWS_SCHEDULED_ACTION_COMPONENT, []).component('scheduledAction', scheduledActionComponent);
