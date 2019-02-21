import { module } from 'angular';

const kubernetesLifecycleHookDetails: ng.IComponentOptions = {
  bindings: { handler: '<' },
  template: `
    <dt>Type</dt>
    <dd>{{$ctrl.handler.type === 'EXEC' ? 'exec' : 'httpGet'}}</dd>
    <dt ng-if="$ctrl.handler.execAction.commands">Command</dt>
    <dd ng-if="$ctrl.handler.execAction.commands">{{$ctrl.handler.execAction.commands.join(' ')}}</dd>
    <dt ng-if="$ctrl.handler.httpGetAction.path">Path</dt>
    <dd ng-if="$ctrl.handler.httpGetAction.path">{{$ctrl.handler.httpGetAction.path}}</dd>
    <dt ng-if="$ctrl.handler.httpGetAction.port">Port</dt>
    <dd ng-if="$ctrl.handler.httpGetAction.port">{{$ctrl.handler.httpGetAction.port}}</dd>
    <dt ng-if="$ctrl.handler.httpGetAction.host">Host</dt>
    <dd ng-if="$ctrl.handler.httpGetAction.host">{{$ctrl.handler.httpGetAction.host}}</dd>
    <dt ng-if="$ctrl.handler.httpGetAction.uriScheme">Scheme</dt>
    <dd ng-if="$ctrl.handler.httpGetAction.uriScheme">{{$ctrl.handler.httpGetAction.uriScheme}}</dd>
    <dt ng-if="$ctrl.handler.httpGetAction.httpHeaders.length">HTTP Headers</dt>
    <dd ng-if="$ctrl.handler.httpGetAction.httpHeaders.length">
      <div ng-repeat="header in $ctrl.handler.httpGetAction.httpHeaders">{{header.name}}: <i>{{header.value}}</i></div>
    </dd>
  `
};

export const KUBERNETES_LIFECYCLE_HOOK_DETAILS = 'spinnaker.kubernetes.lifecycleHookDetails.component';
module(KUBERNETES_LIFECYCLE_HOOK_DETAILS, []).component(
  'kubernetesLifecycleHookDetails',
  kubernetesLifecycleHookDetails,
);
