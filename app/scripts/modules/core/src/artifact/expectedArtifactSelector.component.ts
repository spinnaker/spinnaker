import { IComponentOptions, IController, module } from 'angular';

import { IExpectedArtifact } from 'core/domain';
import { IAccount } from 'core/account';
import { EXPECTED_ARTIFACT_SERVICE, ExpectedArtifactService } from './expectedArtifact.service';

class ExpectedArtifactSelectorCtrl implements IController {
  public command: any;
  public id: any;
  public account: any;
  public accounts: IAccount[];
  public expectedArtifacts: IExpectedArtifact[];
  public helpFieldKey: string;

  constructor(public expectedArtifactService: ExpectedArtifactService) {
    'ngInject';
  }
}

class ExpectedArtifactSelectorComponent implements IComponentOptions {
  public bindings: any = {
    command: '=',
    expectedArtifacts: '<',
    id: '=',
    account: '=',
    accounts: '<',
    helpFieldKey: '@',
  };
  public controller: any = ExpectedArtifactSelectorCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <render-if-feature feature="artifacts">
        <ng-form name="artifact">
          <div class="form-group">
            <label class="col-md-3 sm-label-right">Expected Artifact <help-field key="{{ ctrl.helpFieldKey }}"/></label>
            <div class="col-md-7">
              <ui-select ng-model="ctrl.id"
                         class="form-control input-sm" required>
                <ui-select-match>{{ $select.selected | summarizeExpectedArtifact }}</ui-select-match>
                <ui-select-choices repeat="expected.id as expected in ctrl.expectedArtifacts">
                  <span>{{ expected | summarizeExpectedArtifact }}</span>
                </ui-select-choices>
              </ui-select>
            </div>
          </div>
          <div ng-if="ctrl.account !== undefined" class="form-group">
            <label class="col-md-3 sm-label-right">Artifact Account</label>
            <div class="col-md-7">
              <ui-select ng-model="ctrl.account"
                         class="form-control input-sm">
                <ui-select-match>{{ $select.selected.name }}</ui-select-match>
                <ui-select-choices repeat="account.name as account in ctrl.accounts">
                  <span>{{ account.name }}</span>
                </ui-select-choices>
              </ui-select>
            </div>
          </div>
        </ng-form>
    </render-if-feature>
  `;
}

export const EXPECTED_ARTIFACT_SELECTOR_COMPONENT = 'spinnaker.core.artifacts.expected.selector';
module(EXPECTED_ARTIFACT_SELECTOR_COMPONENT, [EXPECTED_ARTIFACT_SERVICE]).component(
  'expectedArtifactSelector',
  new ExpectedArtifactSelectorComponent(),
);
