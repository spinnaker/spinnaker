import {
  IAttributes,
  ICompileService,
  IComponentOptions,
  IController,
  IControllerService,
  IRootElementService,
  IRootScopeService,
  IScope,
  module,
} from 'angular';
import { IArtifact, IArtifactKindConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { AccountService, ArtifactIconService, IArtifactAccount } from 'core';

class ArtifactCtrl implements IController {
  public artifact: IArtifact;
  public options: IArtifactKindConfig[];
  public description: string;
  private isDefault: boolean;
  private isMatch: boolean;
  public selectedLabel: string;
  public selectedIcon: string;
  private artifactAccounts?: IArtifactAccount[];

  constructor(
    private $attrs: IAttributes,
    private $controller: IControllerService,
    private $compile: ICompileService,
    private $element: IRootElementService,
    private $rootScope: IRootScopeService,
    private $scope: IScope,
  ) {
    'ngInject';
    if (this.$attrs.$attr.hasOwnProperty('isDefault')) {
      this.isDefault = true;
    }

    if (this.$attrs.$attr.hasOwnProperty('isMatch')) {
      this.isMatch = true;
    }
    this.options = Registry.pipeline.getArtifactKinds();
  }

  private renderArtifactConfigTemplate(config: any) {
    const { controller: ctrl, template } = config;
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

  public $onInit(): void {
    // Explicitly watch the artifact's kind so that external changes to it are correctly
    // reflected in the ui-select and artifact's editable form.
    this.$scope.$watch(() => this.artifact.kind, () => this.loadArtifactKind());
    this.loadArtifactKind();
    AccountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
    });
  }

  public getOptions(): IArtifactKindConfig[] {
    let options = this.options.filter(o => o.isDefault === this.isDefault || o.isMatch === this.isMatch);
    if (this.artifactAccounts) {
      options = options.filter(o => {
        const isCustomArtifact = o.type == null;
        const isPublic = !!o.isPubliclyAccessible;
        const hasCredential = this.artifactAccounts.find(a => a.types.includes(o.type));
        return isCustomArtifact || isPublic || hasCredential;
      });
    }
    return options.sort((a, b) => a.label.localeCompare(b.label));
  }

  public loadArtifactKind(): void {
    const { kind } = this.artifact;
    if (!kind) {
      return;
    }
    const artifactKindConfig = this.options.filter(function(config) {
      return config.key === kind;
    });

    if (artifactKindConfig.length) {
      const config = artifactKindConfig[0];
      this.description = config.description;
      this.renderArtifactConfigTemplate(config);
      this.selectedLabel = config.label;
      this.selectedIcon = ArtifactIconService.getPath(config.type);
    }
  }

  public artifactIconPath(artifact: IArtifact) {
    return ArtifactIconService.getPath(artifact.type);
  }
}

class ArtifactComponent implements IComponentOptions {
  public bindings: any = { artifact: '=' };
  public controller: any = ArtifactCtrl;
  public controllerAs = 'ctrl';
  public template = `
<div class="form-group">
  <div class="col-md-4 col-md-offset-1">
    <ui-select class="form-control input-sm"
               required
               ng-model="ctrl.artifact.kind">
      <ui-select-match>
        <img width="20" height="20" ng-if="ctrl.selectedIcon" ng-src="{{ ctrl.selectedIcon }}" />
        {{ ctrl.selectedLabel }}
      </ui-select-match>
      <ui-select-choices repeat="option.key as option in ctrl.getOptions() | filter: { label: $select.search }">
        <img width="20" height="20" ng-if="ctrl.artifactIconPath(option)" ng-src="{{ ctrl.artifactIconPath(option) }}" />
        <span>{{ option.label }}</span>
      </ui-select-choices>
    </ui-select>
  </div>
  <div class="col-md-6">
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
module(ARTIFACT, []).component('artifact', new ArtifactComponent());
