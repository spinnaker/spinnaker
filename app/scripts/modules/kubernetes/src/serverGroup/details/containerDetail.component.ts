import { module, IComponentOptions } from 'angular';

const kubernetesServerGroupContainerDetail: IComponentOptions = {
  bindings: {
    container: '<',
    initContainer: '<',
  },
  template: `
    <dl class="dl-horizontal dl-flex">
      <dt>Name</dt>
      <dd>{{$ctrl.container.name}}</dd>
      <dt>Registry</dt>
      <dd>{{$ctrl.container.imageDescription.registry}}</dd>
      <dt>Image</dt>
      <dd>{{$ctrl.container.imageDescription.repository}}</dd>
      <dt>Tag</dt>
      <dd>{{$ctrl.container.imageDescription.tag}}</dd>
      <dt ng-if="$ctrl.initContainer">Init</dt>
      <dd ng-if="$ctrl.initContainer">True</dd>
      <div ng-if="$ctrl.container.limits">
        <b>Limits</b>
        <div ng-if="$ctrl.container.limits.cpu">
          <dt>CPU Limit</dt>
          <dd>{{$ctrl.container.limits.cpu}}</dd>
        </div>
        <div ng-if="container.limits.memory">
          <dt>Memory Limit</dt>
          <dd>{{$ctrl.container.limits.memory}}</dd>
        </div>
      </div>
      <div ng-if="$ctrl.container.requests">
        <b>Requests</b>
        <div ng-if="$ctrl.container.requests.cpu">
          <dt>CPU Request</dt>
          <dd>{{$ctrl.container.requests.cpu}}</dd>
        </div>
        <div ng-if="$ctrl.container.requests.memory">
          <dt>Memory Request</dt>
          <dd>{{$ctrl.container.requests.memory}}</dd>
        </div>
      </div>
      <div ng-if="$ctrl.container.readinessProbe">
        <b>Readiness Probe</b>
        <dt>Success Threshold</dt>
        <dd>{{$ctrl.container.readinessProbe.successThreshold}}</dd>
        <dt>Failure Threshold</dt>
        <dd>{{$ctrl.container.readinessProbe.failureThreshold}}</dd>
        <dt>Period</dt>
        <dd>{{$ctrl.container.readinessProbe.periodSeconds}}</dd>
        <dt>Initial Delay</dt>
        <dd>{{$ctrl.container.readinessProbe.initialDelaySeconds}}</dd>
        <dt>Timeout</dt>
        <dd>{{$ctrl.container.readinessProbe.timeoutSeconds}}</dd>
      </div>
      <div ng-if="$ctrl.container.livenessProbe">
        <b>Liveness Probe</b>
        <dt>Success Threshold</dt>
        <dd>{{$ctrl.container.livenessProbe.successThreshold}}</dd>
        <dt>Failure Threshold</dt>
        <dd>{{$ctrl.container.livenessProbe.failureThreshold}}</dd>
        <dt>Period</dt>
        <dd>{{$ctrl.container.livenessProbe.periodSeconds}}</dd>
        <dt>Initial Delay</dt>
        <dd>{{$ctrl.container.livenessProbe.initialDelaySeconds}}</dd>
        <dt>Timeout</dt>
        <dd>{{$ctrl.container.livenessProbe.timeoutSeconds}}</dd>
      </div>
      <div ng-if="$ctrl.container.lifecycle.postStart">
        <b style="border-bottom: 1px solid #c4c4c4; margin-top: 3px;">PostStart Hook</b><br>
        <kubernetes-lifecycle-hook-details handler="$ctrl.container.lifecycle.postStart"></kubernetes-lifecycle-hook-details>
      </div>
      <div ng-if="$ctrl.container.lifecycle.preStop">
        <b style="border-bottom: 1px solid #c4c4c4; margin-top: 3px;">PreStop Hook</b><br>
        <kubernetes-lifecycle-hook-details handler="$ctrl.container.lifecycle.preStop"></kubernetes-lifecycle-hook-details>
      </div>
    </dl>
  `,
};

export const KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL = 'spinnaker.kubernetes.serverGroupContainerDetail.component';
module(KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL, []).component(
  'kubernetesServerGroupContainerDetail',
  kubernetesServerGroupContainerDetail,
);
