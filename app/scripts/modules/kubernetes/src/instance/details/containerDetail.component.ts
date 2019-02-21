import { module, IComponentOptions } from 'angular';

const kubernetesInstanceContainerDetail: IComponentOptions = {
  bindings: {
    container: '<',
    initContainer: '<',
    containerStatus: '<',
  },
  template: `
    <div class="horizontal-when-filters-collapsed">
      <dt>Name</dt>
      <dd>{{$ctrl.container.name}}</dd>
      <dt>Image</dt>
      <dd>{{$ctrl.container.image}}</dd>
      <dt ng-if="$ctrl.initContainer">Init</dt>
      <dd ng-if="$ctrl.initContainer">True<dd>
      <div ng-if="$ctrl.container.resources">
        <div ng-if="$ctrl.container.resources.limits">
          <div ng-if="$ctrl.container.resources.limits.cpu">
            <dt>CPU Limit</dt>
            <dd>{{$ctrl.container.resources.limits.cpu}}</dd>
          </div>
          <div ng-if="$ctrl.container.resources.limits.memory">
            <dt>Memory Limit</dt>
            <dd>{{$ctrl.container.resources.limits.memory}}</dd>
          </div>
        </div>
        <div ng-if="$ctrl.container.resources.requests">
          <div ng-if="$ctrl.container.resources.requests.cpu">
            <dt>CPU Request</dt>
            <dd>{{$ctrl.container.resources.requests.cpu}}</dd>
          </div>
          <div ng-if="$ctrl.container.resources.requests.memory">
            <dt>Memory Request</dt>
            <dd>{{$ctrl.container.resources.requests.memory}}</dd>
          </div>
        </div>
      </div>
      <dt>Image Pull Policy</dt>
      <dd>{{$ctrl.container.imagePullPolicy}}</dd>
      <dt>Termination Message Path</dt>
      <dd>{{$ctrl.container.terminationMessagePath}}
        <copy-to-clipboard
            class="copy-to-clipboard copy-to-clipboard-sm"
            text="$ctrl.container.terminationMessagePath"
            tool-tip="'Copy to clipboard'">
        </copy-to-clipboard>
      </dd>
      <dl ng-repeat="volumeMount in $ctrl.container.volumeMounts" class="dl-horizontal dl-flex">
      </dl>
      <dt>Ready</dt>
      <dd>{{$ctrl.containerStatus.ready}}</dd>
      <dt>Restart Count</dt>
      <dd>{{$ctrl.containerStatus.restartCount}}</dd>
      <div ng-if="$ctrl.containerStatus.state.running">
        <dt>Running</dt>
        <dd>
          Started At: {{$ctrl.containerStatus.state.running.startedAt}}
        </dd>
      </div>
      <div ng-if="$ctrl.containerStatus.state.waiting">
        <dt>Waiting</dt>
        <dd>
        <p>Reason: {{$ctrl.containerStatus.state.waiting.reason}}</p>
        <p>Message: {{$ctrl.containerStatus.state.waiting.message}}</p>
        </dd>
      </div>
      <div ng-if="$ctrl.containerStatus.state.terminated">
        <dt>Terminated</dt>
        <dd>
        <p>Started At: {{$ctrl.containerStatus.state.terminated.startedAt}}</p>
        <p>Finished At: {{$ctrl.containerStatus.state.terminated.finishedAt}}</p>
        <p>Message: {{$ctrl.containerStatus.state.terminated.message}}</p>
        <p>Reason: {{$ctrl.containerStatus.state.terminated.reason}}</p>
        <p>Exit Code: {{$ctrl.containerStatus.state.terminated.exitCode}}</p>
        <p>Signal: {{$ctrl.containerStatus.state.terminated.signal}}</p>
        </dd>
      </div>
    </div>
  `,
};

export const KUBERNETES_INSTANCE_CONTAINER_DETAIL = 'spinnaker.kubernetes.instance.container.detail';
module(KUBERNETES_INSTANCE_CONTAINER_DETAIL, []).component(
  'kubernetesInstanceContainerDetail',
  kubernetesInstanceContainerDetail,
);
