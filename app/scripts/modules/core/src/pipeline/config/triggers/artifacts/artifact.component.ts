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
import { AccountService, IArtifactAccount } from 'core/account';
import { ArtifactIconService, ExpectedArtifactService } from 'core/artifact';
import { isEqual } from 'lodash';

class ArtifactCtrl implements IController {
  public artifact: IArtifact;
  public options: IArtifactKindConfig[];
  public kindConfig: IArtifactKindConfig;
  private isDefault: boolean;
  private artifactAccounts?: IArtifactAccount[];

  public static $inject = ['$attrs', '$controller', '$compile', '$element', '$rootScope', '$scope'];
  constructor(
    private $attrs: IAttributes,
    private $controller: IControllerService,
    private $compile: ICompileService,
    private $element: IRootElementService,
    private $rootScope: IRootScopeService,
    private $scope: IScope,
  ) {
    this.isDefault = this.$attrs.$attr.hasOwnProperty('isDefault');
    if (this.isDefault) {
      this.options = Registry.pipeline.getDefaultArtifactKinds();
    } else {
      this.options = Registry.pipeline.getMatchArtifactKinds();
    }
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
    this.loadArtifactKind();
    this.$scope.$watch(() => this.artifact.type, () => this.loadArtifactKind());
    AccountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
    });
  }

  public getOptions(): IArtifactKindConfig[] {
    let options = this.options;
    if (this.artifactAccounts) {
      options = options.filter(o => {
        const isCustomArtifact = o.customKind;
        const isPublic = !!o.isPubliclyAccessible;
        const hasCredential = this.artifactAccounts.find(a => a.types.includes(o.type));
        return isCustomArtifact || isPublic || hasCredential;
      });
    }
    return options.sort((a, b) => a.label.localeCompare(b.label));
  }

  private loadArtifactKind(): void {
    const newKindConfig = ExpectedArtifactService.getKindConfig(this.artifact, this.isDefault);
    if (!isEqual(this.kindConfig, newKindConfig)) {
      this.kindConfig = newKindConfig;
      this.renderArtifactConfigTemplate(this.kindConfig);
    }
  }

  public onKindChange(artifactKind: IArtifactKindConfig): void {
    // kind is deprecated; remove it from artifacts as they are updated
    delete this.artifact.kind;
    this.artifact.customKind = artifactKind.customKind;
    this.renderArtifactConfigTemplate(artifactKind);
  }

  public artifactIconPath(kindConfig: IArtifactKindConfig) {
    return ArtifactIconService.getPath(kindConfig.type);
  }
}

class ArtifactComponent implements IComponentOptions {
  public bindings: any = { artifact: '=' };
  public controller: any = ArtifactCtrl;
  public controllerAs = 'ctrl';
  public template = `
<div class="form-group">
  <label class="col-md-2 sm-label-right">
      Kind
  </label>
  <div class="col-md-3">
    <ui-select class="form-control input-sm"
               required
               on-select="ctrl.onKindChange($item)"
               ng-model="ctrl.kindConfig">
      <ui-select-match>
        <img width="20" height="20" ng-if="ctrl.artifactIconPath(ctrl.kindConfig)" ng-src="{{ ctrl.artifactIconPath(ctrl.kindConfig) }}" />
        {{ ctrl.kindConfig.label }}
      </ui-select-match>
      <ui-select-choices repeat="option in ctrl.getOptions() | filter: { label: $select.search }">
        <img width="20" height="20" ng-if="ctrl.artifactIconPath(option)" ng-src="{{ ctrl.artifactIconPath(option) }}" />
        <span>{{ option.label }}</span>
      </ui-select-choices>
    </ui-select>
  </div>
  <div class="col-md-6">
    {{ctrl.kindConfig.description}}
  </div>
</div>
<div class="form-group">
  <div class="artifact-body"></div>
</div>
`;
}

export const ARTIFACT = 'spinnaker.core.pipeline.config.trigger.artifacts.artifact';
module(ARTIFACT, []).component('artifact', new ArtifactComponent());
