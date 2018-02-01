import { copy, IComponentOptions, IController, IRootScopeService, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { dump } from 'js-yaml';

class KubernetesShowManifestYaml implements IController {
  public manifest: any;
  public text: string;
  public linkName: string;
  private title: string;

  constructor(private $rootScope: IRootScopeService,
              private $uibModal: IModalService) {
    'ngInject';
    this.text = dump(copy(this.manifest));
    this.title = this.manifest.metadata.name;
  }

  public openYaml() {
    const scope = this.$rootScope.$new();
    scope.manifestTitle = this.title;
    scope.manifestData = this.text;
    this.$uibModal.open({
      templateUrl: require('./showManifestYaml.html'),
      scope: scope
    });
  }
}

class KubernetesShowManifestYamlComponent implements IComponentOptions {
  public bindings: any = { manifest: '<', linkName: '<' };
  public controller: any = KubernetesShowManifestYaml;
  public controllerAs = 'ctrl';
  public template = `
    <a href ng-click='ctrl.openYaml()'>{{ctrl.linkName}}</a>
  `;
}

export const KUBERNETES_SHOW_MANIFEST_YAML = 'spinnaker.kubernetes.v2.manifest.showYaml';
module(KUBERNETES_SHOW_MANIFEST_YAML, [])
  .component('kubernetesShowManifestYaml', new KubernetesShowManifestYamlComponent());
