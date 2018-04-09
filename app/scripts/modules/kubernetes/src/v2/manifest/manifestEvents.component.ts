import { IComponentOptions, IController, module } from 'angular';

class KubernetesManifestEvents implements IController {
  public manifest: any;

  public pillStyle(e: any): string {
    if (e.type === 'Warning') {
      return 'alert';
    } else if (e.type === 'Normal') {
      return 'success';
    } else {
      return '';
    }
  }

  constructor() {
    'ngInject';
  }
}

class KubernetesManifestEventsComponent implements IComponentOptions {
  public bindings: any = { manifest: '<' };
  public controller: any = KubernetesManifestEvents;
  public controllerAs = 'ctrl';
  public template = `
    <div ng-if="ctrl.manifest && !ctrl.manifest.events">No events found - Kubernetes does not store events for long.</div>
    <div class="info" ng-repeat="e in ctrl.manifest.events">
      <div class="horizontal">
        <div ng-if="e.count" class="pill {{ctrl.pillStyle(e)}}">{{e.count}} × <b>{{e.reason}}</b></div>
      </div>
      <div ng-if="e.firstTimestamp || e.lastTimestamp">
        <div ng-if="e.firstTimestamp === e.lastTimestamp">
          <i>{{e.firstTimestamp}}</i>
        </div>
        <div ng-if="e.firstTimestamp !== e.lastTimestamp">
          <div>
            First Occurrence: <i>{{e.firstTimestamp}}</i>
          </div>
          <div>
            Last Occurrence: <i>{{e.lastTimestamp}}</i>
          </div>
        </div>
      </div>
      <div>{{e.message}}<div>
      <br ng-if="!$last">
    </div>
  `;
}

export const KUBERNETES_MANIFEST_EVENTS = 'spinnaker.kubernetes.v2.manifest.events';
module(KUBERNETES_MANIFEST_EVENTS, []).component('kubernetesManifestEvents', new KubernetesManifestEventsComponent());
