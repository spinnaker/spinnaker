import { IComponentOptions, IController, module } from 'angular';

import { IExpectedArtifact, IArtifact } from 'core/domain';
import { IArtifactAccount } from 'core/account';
import { ArtifactIconService } from './ArtifactIconService';

import './artifactSelector.less';

class ExpectedArtifactSelectorCtrl implements IController {
  public command: any;
  public id: string;
  public account: string;
  public accounts: IArtifactAccount[];
  public expectedArtifacts: IExpectedArtifact[];
  public helpFieldKey: string;
  public showIcons: boolean;
  public fieldColumns: number;
  public excludeArtifactTypePatterns: RegExp[];

  private artifactFromExpected(expected: IExpectedArtifact): IArtifact | null {
    if (expected) {
      return expected.matchArtifact || expected.defaultArtifact;
    } else {
      return null;
    }
  }

  private selectedArtifact(): IArtifact | null {
    const expected = this.expectedArtifacts.find(ea => ea.id === this.id);
    return this.artifactFromExpected(expected);
  }

  private selectedArtifactAccounts(): IArtifactAccount[] {
    const artifact = this.selectedArtifact();
    if (artifact == null || this.accounts == null) {
      return [];
    }
    const filteredAccounts = this.accounts.filter(acc => acc.types.includes(artifact.type));
    return filteredAccounts;
  }

  public $onInit() {
    this.fieldColumns = this.fieldColumns || 8;
  }

  public iconPath(expected: IExpectedArtifact): string {
    const artifact = this.artifactFromExpected(expected);
    if (artifact == null) {
      return '';
    }
    return ArtifactIconService.getPath(artifact.type);
  }

  public setAccountForSelectedArtifact() {
    const accounts = this.selectedArtifactAccounts();
    if (accounts.length > 0) {
      this.account = accounts[0].name;
    }
  }

  public showArtifactAccountSelect(): boolean {
    return this.selectedArtifactAccounts().length > 1;
  }

  public getExpectedArtifacts(): IExpectedArtifact[] {
    let eas = this.expectedArtifacts || [];
    if (this.excludeArtifactTypePatterns) {
      eas = eas.filter(ea => {
        const artifact = this.artifactFromExpected(ea);
        if (artifact === this.selectedArtifact()) {
          return true;
        }
        if (artifact) {
          return !this.excludeArtifactTypePatterns.find(patt => patt.test(artifact.type));
        } else {
          return false;
        }
      });
    }
    return eas;
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
    showIcons: '<',
    fieldColumns: '@',
    excludeArtifactTypePatterns: '<',
  };
  public controller: any = ExpectedArtifactSelectorCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <render-if-feature feature="artifacts">
      <ng-form name="artifact">
        <stage-config-field label="Expected Artifact" help-key="{{ctrl.helpFieldKey}}" field-columns="{{ ctrl.fieldColumns }}">
          <ui-select ng-model="ctrl.id"
                     class="form-control input-sm expected-artifact-selector"
                     on-select="ctrl.setAccountForSelectedArtifact()"
                     required>
            <ui-select-match>
              <img
                ng-if="ctrl.showIcons && ctrl.iconPath($select.selected)"
                width="16"
                height="16"
                class="artifact-icon"
                ng-src="{{ ctrl.iconPath($select.selected) }}" />
              {{ $select.selected | summarizeExpectedArtifact }}
            </ui-select-match>
            <ui-select-choices repeat="expected.id as expected in ctrl.getExpectedArtifacts()">
              <img
                ng-if="ctrl.showIcons && ctrl.iconPath(expected)"
                width="16"
                height="16"
                class="artifact-icon"
                ng-src="{{ ctrl.iconPath(expected) }}" />
              <span>{{ expected | summarizeExpectedArtifact }}</span>
            </ui-select-choices>
          </ui-select>
        </stage-config-field>
        <stage-config-field ng-if="ctrl.showArtifactAccountSelect()"
                            label="Artifact Account"
                            fieldColumns="{{ ctrl.fieldColumns }}">
          <ui-select ng-model="ctrl.account"
                     class="form-control input-sm">
            <ui-select-match>{{ $select.selected.name }}</ui-select-match>
            <ui-select-choices repeat="account.name as account in ctrl.selectedArtifactAccounts()">
              <span>{{ account.name }}</span>
            </ui-select-choices>
          </ui-select>
        </stage-config-field>
      </ng-form>
    </render-if-feature>
  `;
}

export const EXPECTED_ARTIFACT_SELECTOR_COMPONENT = 'spinnaker.core.artifacts.expected.selector';
module(EXPECTED_ARTIFACT_SELECTOR_COMPONENT, []).component(
  'expectedArtifactSelector',
  new ExpectedArtifactSelectorComponent(),
);
