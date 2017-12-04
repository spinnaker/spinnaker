import { copy, IComponentOptions, IController, module } from 'angular';

import { IArtifact, IExpectedArtifact } from 'core/domain';

class ExpectedArtifactSelectorCtrl implements IController {
  public command: any;
  public idField: string;
  public expectedArtifacts: IExpectedArtifact[];

  public summarizeExpectedArtifact(expected: IExpectedArtifact): string {
    if (!expected) {
      return '';
    }

    const artifact = copy(expected.matchArtifact);
    return Object.keys(artifact)
      .filter((k: keyof IArtifact) => artifact[k])
      .map((k: keyof IArtifact) => (`${k}: ${artifact[k]}`))
      .join(', ');
  };
}

class ExpectedArtifactSelectorComponent implements IComponentOptions {
  public bindings: any = { command: '=', expectedArtifacts: '<', idField: '<' };
  public controller: any = ExpectedArtifactSelectorCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <div class="container-fluid form-horizontal">
      <ng-form name="manifest">
        <div class="form-group">
          <label class="col-md-3 sm-label-right">Expected Artifact</label>
          <div class="col-md-7">
            <ui-select ng-model="ctrl.command[ctrl.idField]"
                       class="form-control input-sm">
              <ui-select-match>{{ ctrl.summarizeExpectedArtifact($select.selected) }}</ui-select-match>
              <ui-select-choices repeat="expected.id as expected in ctrl.expectedArtifacts">
                <span>{{ ctrl.summarizeExpectedArtifact(expected) }}</span>
              </ui-select-choices>
            </ui-select>
          </div>
        </div>
      </ng-form>
    </div>
  `;
}

export const EXPECTED_ARTIFACT_SELECTOR_COMPONENT = 'spinnaker.core.artifacts.expected.selector';
module(EXPECTED_ARTIFACT_SELECTOR_COMPONENT, [])
  .component('expectedArtifactSelector', new ExpectedArtifactSelectorComponent());
