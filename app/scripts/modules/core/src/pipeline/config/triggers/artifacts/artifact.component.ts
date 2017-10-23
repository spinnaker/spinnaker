import {
  ICompileService,
  IComponentOptions,
  IController,
  IControllerService,
  IRootElementService,
  IRootScopeService,
  module
} from 'angular';
import { IArtifact, IArtifactKindConfig } from 'core/domain';
import { PipelineConfigProvider } from 'core/pipeline';

class ArtifactCtrl implements IController {
  public artifact: IArtifact;
  public options: IArtifactKindConfig[];
  public description: string;

  constructor(private pipelineConfig: PipelineConfigProvider,
              private $controller: IControllerService,
              private $compile: ICompileService,
              private $element: IRootElementService,
              private $rootScope: IRootScopeService) {
    'ngInject';
    this.options = this.pipelineConfig.getArtifactKinds();
    this.loadArtifactKind();
  }

  public loadArtifactKind(): void  {
    const kind = this.artifact.kind;
    if (!kind) {
      return;
    }
    const artifactKindConfig = this.options.filter(function(config) {
      return config.key === kind;
    });

    if (artifactKindConfig.length) {
      const config = artifactKindConfig[0];
      const template: Element = config.template as any;
      this.description = config.description;

      const ctrl = config.controller;
      const controller = this.$controller(ctrl, { artifact: this.artifact });
      const scope = this.$rootScope.$new();
      const controllerAs = config.controllerAs;
      if (controllerAs) {
        scope[config.controllerAs] = controller;
      } else {
        scope['ctrl'] = controller;
      }

      const templateBody = this.$compile(template)(scope) as any;
      this.$element.find('.artifact-body').html(templateBody);
    }
  }
}

class ArtifactComponent implements IComponentOptions {
  public bindings: any = { artifact: '=' };
  public controller: any = ArtifactCtrl;
  public controllerAs = 'ctrl';
  public template = `
<div class="form-group">
  <div class="col-md-2 col-md-offset-1">
    <select class="input-sm"
            required
            ng-change="ctrl.loadArtifactKind()"
            ng-options="option.key as option.label for option in ctrl.options"
            ng-model="ctrl.artifact.kind">
      <option style="display:none" value="">Select a kind</option>
    </select>
  </div>
  <div class="col-md-9">
    {{ctrl.description}}
  </div>
</div>
<hr>
<div class="form-group">
  <div class="col-md-12">
    <div class="artifact-body"></div>
  </div>
</div>
`;
}

export const ARTIFACT = 'spinnaker.core.pipeline.config.trigger.artifacts.artifact';
module(ARTIFACT, [])
  .component('artifact', new ArtifactComponent());
