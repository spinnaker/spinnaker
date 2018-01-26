import { IComponentOptions, IController, module } from 'angular';

import { IExpectedArtifact } from 'core/domain';
import { EXPECTED_ARTIFACT_SERVICE, ExpectedArtifactService } from './expectedArtifact.service';

class ExpectedArtifactMultiSelectorCtrl implements IController {
  public command: any;
  public idsField: string;
  public label: string;
  public expectedArtifacts: IExpectedArtifact[];
  public helpFieldKey: string;

  constructor (public expectedArtifactService: ExpectedArtifactService) {
    'ngInject';
  }
}

class ExpectedArtifactMultiSelectorComponent implements IComponentOptions {
  public bindings: any = { command: '=', expectedArtifacts: '<', artifactLabel: '@', idsField: '@', helpFieldKey: '@' };
  public controller: any = ExpectedArtifactMultiSelectorCtrl;
  public controllerAs = 'ctrl';
  public template = `
      <render-if-feature feature="artifacts">
        <ng-form name="artifacts">
          <div class="col-md-12">
            <div class="form-group">
              <label class="col-md-3 sm-label-right">{{ctrl.artifactLabel}} <help-field key="{{ctrl.helpFieldKey}}"/></label>
              <div class="col-md-9">
                <ui-select multiple
                           ng-model="ctrl.command[ctrl.idsField]"
                           class="form-control input-sm">
                  <ui-select-match>{{ $item | summarizeExpectedArtifact }}</ui-select-match>
                  <ui-select-choices repeat="expected.id as expected in ctrl.expectedArtifacts">
                    <span>{{ expected | summarizeExpectedArtifact }}</span>
                  </ui-select-choices>
                </ui-select>
              </div>
            </div>
          </div>
        </ng-form>
      </render-if-feature>
  `;
}

export const EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT = 'spinnaker.core.artifacts.expected.multiSelector';
module(EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT, [
  EXPECTED_ARTIFACT_SERVICE,
]).component('expectedArtifactMultiSelector', new ExpectedArtifactMultiSelectorComponent());
