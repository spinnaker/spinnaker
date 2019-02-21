import { module } from 'angular';

const appengineDynamicBranchLabelComponent: ng.IComponentOptions = {
  bindings: { trigger: '<' },
  template: `
    <span ng-if="$ctrl.trigger.type === 'git'">
      Resolved at runtime by <b>{{$ctrl.trigger.source}}</b> trigger: {{$ctrl.trigger.project}}/{{$ctrl.trigger.slug}}<span ng-if="$ctrl.trigger.branch">:{{$ctrl.trigger.branch}}</span>
    </span>
    <span ng-if="$ctrl.trigger.type === 'jenkins'">
      Resolved at runtime by <b>Jenkins</b> trigger: {{$ctrl.trigger.master}}/{{$ctrl.trigger.job}}
    </span>
  `,
};

export const APPENGINE_DYNAMIC_BRANCH_LABEL = 'spinnaker.appengine.dynamicBranchLabel.component';
module(APPENGINE_DYNAMIC_BRANCH_LABEL, []).component(
  'appengineDynamicBranchLabel',
  appengineDynamicBranchLabelComponent,
);
