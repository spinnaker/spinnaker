import { IComponentOptions, module } from 'angular';

class ImageSourceSelectorComponent implements IComponentOptions {
  public bindings: any = { command: '=', imageSources: '<', helpFieldKey: '@', idField: '@' };
  public controllerAs = 'ctrl';
  public template = `
    <render-if-feature feature="artifacts">
      <div class="form-group">
        <div class="col-md-3 sm-label-right">
          Image Source
          <help-field key="{{ ctrl.helpFieldKey }}"></help-field>
        </div>
        <div class="col-md-9">
          <div class="radio" ng-repeat="imageSource in ctrl.imageSources">
            <label>
              <input type="radio" ng-model="ctrl.command[ctrl.idField]" value="{{ imageSource }}">
              {{ imageSource | robotToHuman }}
            </label>
          </div>
        </div>
      </div>
    </render-if-feature>
  `;
}

export const IMAGE_SOURCE_SELECTOR_COMPONENT = 'spinnaker.core.artifacts.expected.image.selector';
module(IMAGE_SOURCE_SELECTOR_COMPONENT, [
]).component('imageSourceSelector', new ImageSourceSelectorComponent());
