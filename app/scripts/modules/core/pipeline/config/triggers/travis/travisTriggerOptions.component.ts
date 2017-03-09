import {module, IScope} from 'angular';

import {IGOR_SERVICE, IgorService} from 'core/ci/igor.service';
import {IBuild} from 'core/domain/IBuild';

interface IViewState {
  buildsLoading: boolean;
  loadError: boolean;
  selectedBuild: IBuild;
}

export class TravisTriggerOptions {
  public viewState: IViewState;
  public command: any;
  public builds: IBuild[];

  static get $inject() {
    return ['$scope', 'igorService'];
  }

  constructor($scope: IScope, private igorService: IgorService) {
    // These fields will be added to the trigger when the form is submitted
    this.command.extraFields = {};

    this.viewState = {
      buildsLoading: true,
      loadError: false,
      selectedBuild: null,
    };

    $scope.$watch(() => this.command.trigger, () => this.initialize());
  }

  private buildLoadSuccess(builds: IBuild[]): void {
    this.builds = builds
      .filter((build) => !build.building && build.result === 'SUCCESS')
      .sort((a, b) => b.number - a.number);
    if (this.builds.length) {
      const defaultSelection = this.builds[0];
      this.viewState.selectedBuild = defaultSelection;
      this.updateSelectedBuild(defaultSelection);
    }
    this.viewState.buildsLoading = false;
  };

  private buildLoadFailure(): void {
    this.viewState.buildsLoading = false;
    this.viewState.loadError = true;
  };

  private initialize() {
    const command = this.command;
    // do not re-initialize if the trigger has changed to some other type
    if (command.trigger.type !== 'travis') {
      return;
    }
    this.viewState.buildsLoading = true;
    this.igorService.listBuildsForJob(command.trigger.master, command.trigger.job)
      .then((builds) => this.buildLoadSuccess(builds))
      .catch(_reason => this.buildLoadFailure());
  };

  public updateSelectedBuild(item: any): void {
    this.command.extraFields.buildNumber = item.number;
  };
}

export const TRAVIS_TRIGGER_OPTIONS_COMPONENT = 'spinnaker.core.pipeline.config.triggers.travis.options.component';
module(TRAVIS_TRIGGER_OPTIONS_COMPONENT, [
  IGOR_SERVICE
]).component('travisTriggerOptions', () => {
  return {
    restrict: 'E',
    templateUrl: require('./travisTriggerOptions.component.html'),
    bindToController: {
      command: '=',
    },
    controller: 'TravisTriggerOptionsCtrl',
    scope: {}
  };
}).controller('TravisTriggerOptionsCtrl', TravisTriggerOptions);
