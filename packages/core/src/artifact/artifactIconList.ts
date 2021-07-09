import { IComponentOptions, IController, module } from 'angular';
import { ArtifactIconService } from './ArtifactIconService';

class ArtifactIconListController implements IController {
  public artifacts: any[];

  public iconPath(type: string): string {
    return ArtifactIconService.getPath(type);
  }
}

const artifactIconListComponent: IComponentOptions = {
  bindings: { artifacts: '<' },
  controller: ArtifactIconListController,
  controllerAs: 'ctrl',
  template: `
    <div class="artifact-list-item" ng-repeat="artifact in ctrl.artifacts" title="{{ artifact.type }}">
      <img
        class="artifact-list-item-icon"
        ng-if="ctrl.iconPath(artifact.type)"
        ng-src="{{ ctrl.iconPath(artifact.type) }}"
        width="20"
        height="20"
      /><span class="artifact-list-item-name">{{ artifact.name }}<span ng-if="artifact.version"> - {{ artifact.version }}</span></span>
    </div>
  `,
};

export const ARTIFACT_ICON_LIST = 'spinnaker.core.artifact.iconList';
module(ARTIFACT_ICON_LIST, []).component('artifactIconList', artifactIconListComponent);
