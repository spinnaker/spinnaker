import { IComponentOptions, IController, module } from 'angular';

class KubernetesManifestAnnotations implements IController {
  public manifest: any;
  private ignoreAnnotations = [ 'kubectl.kubernetes.io/last-applied-configuration' ];

  public isValidKey(k: string): boolean {
    return this.ignoreAnnotations.indexOf(k) === -1;
  }

  constructor() {
    'ngInject';
  }
}

class KubernetesManifestAnnotationsComponent implements IComponentOptions {
  public bindings: any = { manifest: '<' };
  public controller: any = KubernetesManifestAnnotations;
  public controllerAs = 'ctrl';
  public template = `
    <div class="vertical left">
      <div class="info" ng-repeat="(k, v) in ctrl.manifest.metadata.annotations" ng-if="ctrl.isValidKey(k)">
        <code>
          {{k}}: {{v}}
        </code>
      </div>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_ANNOTATIONS = 'spinnaker.kubernetes.v2.manifest.annotations';
module(KUBERNETES_MANIFEST_ANNOTATIONS, [])
  .component('kubernetesManifestAnnotations', new KubernetesManifestAnnotationsComponent());
