import { IController, IComponentOptions, module } from 'angular';

export interface IJarDiffItem {
  displayDiff: string;
}

export interface IJarDiff {
  [key: string]: IJarDiffItem[];
  added: IJarDiffItem[];
  downgraded: IJarDiffItem[];
  duplicates: IJarDiffItem[];
  removed: IJarDiffItem[];
  unchanged: IJarDiffItem[];
  unknown: IJarDiffItem[];
  upgraded: IJarDiffItem[];
}

class JarDiffComponentController implements IController {
  public jarDiffs: IJarDiff;
  public hasJarDiffs = false;

  public $onInit() {
    this.hasJarDiffs = Object.keys(this.jarDiffs).some((key: string) => this.jarDiffs[key].length > 0);
  }

  public $onChanges() {
    this.$onInit();
  }
}

const jarDiffComponent: IComponentOptions = {
  bindings: {
    jarDiffs: '<',
  },
  controller: JarDiffComponentController,
  template: `
  <div ng-if="$ctrl.hasJarDiffs">
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.added && $ctrl.jarDiffs.added.length">
      <tr><th>Added</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.added">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.removed && $ctrl.jarDiffs.removed.length">
      <tr><th>Removed</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.removed">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.upgraded && $ctrl.jarDiffs.upgraded.length">
      <tr><th>Upgraded</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.upgraded">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.downgraded && $ctrl.jarDiffs.downgraded.length">
      <tr><th>Downgraded</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.downgraded">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.duplicates && $ctrl.jarDiffs.duplicates.length">
      <tr><th>Duplicates</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.duplicates">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
    <table class="table table-condensed no-lines" ng-if="$ctrl.jarDiffs.unknown && $ctrl.jarDiffs.unknown.length">
      <tr><th>Unknown</th></tr>
      <tr ng-repeat="jar in $ctrl.jarDiffs.unknown">
        <td>{{jar.displayDiff}}</td>
      </tr>
    </table>
  </div>
  `
};

export const JAR_DIFF_COMPONENT = 'spinnaker.diffs.jar.diff.component';
module(JAR_DIFF_COMPONENT, []).component('jarDiff', jarDiffComponent);
