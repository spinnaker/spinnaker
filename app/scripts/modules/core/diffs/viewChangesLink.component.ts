import {IComponentController, IComponentOptions, module} from 'angular';
import {IModalInstanceService, IModalService} from 'angular-ui-bootstrap';

import {ICreationMetadata, ICreationMetadataTag} from 'core/domain';
import {ICommit} from './commitHistory.component';
import {COMMIT_HISTORY_COMPONENT} from './commitHistory.component';
import {EXECUTION_SERVICE, ExecutionService} from 'core/delivery/service/execution.service';
import {JAR_DIFF_COMPONENT, IJarDiff} from './jarDiff.component';

export interface IViewChangesConfig {
  commits?: ICommit[];
  jarDiffs: IJarDiff;
  metadata?: ICreationMetadataTag;
}

export interface INameItem {
  name: string;
}

class ViewChangesModalController {

  static get $inject(): string[] {
    return ['$uibModalInstance', 'hasJarChanges', 'commits', 'jarDiffs', 'nameItem'];
  }

  constructor(private $uibModalInstance: IModalInstanceService,
              public hasJarChanges: boolean,
              public commits: ICommit[],
              public jarDiffs: IJarDiff,
              public nameItem: INameItem) {}

  public close(): void {
    this.$uibModalInstance.close();
  }
}

class ViewChangesLinkController implements IComponentController {

  public changeConfig: IViewChangesConfig;
  public viewType: string;
  public linkText = 'View Changes';
  public nameItem: INameItem;

  public changesAvailable = false;
  public hasJarChanges = false;
  public metadata: ICreationMetadataTag;
  public commits: ICommit[];
  public jarDiffs: IJarDiff;

  static get $inject(): string[] {
    return ['$uibModal', 'executionService'];
  }

  constructor(private $uibModal: IModalService,
              private executionService: ExecutionService) {}

  private setJarDiffs(): void {
    this.hasJarChanges =
      Object.keys(this.jarDiffs).some((key: string) =>
        Array.isArray(this.jarDiffs[key]) && this.jarDiffs[key].length > 0);
  }

  private lookForDiffs(stageId: string, executionId: string): void {
    this.executionService.getExecution(executionId).then((details: any) => {

      const stage: any = details.stages.find((s: any) => s.id === stageId);
      this.jarDiffs = stage.context.jarDiffs;
      this.commits = stage.context.commits;
      this.setJarDiffs();

      if (this.hasJarChanges || this.commits.length) {
        this.changesAvailable = true;
      }
    });
  }

  public $onInit(): void {

    if (this.changeConfig.metadata) {
      if (this.changeConfig.metadata.value.executionType === 'pipeline') {
        const value: ICreationMetadata = this.changeConfig.metadata.value;
        this.lookForDiffs(value.stageId, value.executionId);
      }
    } else if (this.changeConfig.jarDiffs || this.changeConfig.commits) {
      this.commits = this.changeConfig.commits || [];
      this.jarDiffs = this.changeConfig.jarDiffs || null;
      if (this.jarDiffs) {
        this.setJarDiffs();
      }

      if (this.hasJarChanges || this.commits.length) {
        this.changesAvailable = true;
      }
    }
  }

  public $onChanges(): void {
    this.$onInit();
  }

  public showChanges(): void {
    this.$uibModal.open({
      templateUrl: require('./changes.html'),
      controller: ViewChangesModalController,
      controllerAs: '$ctrl',
      resolve: {
        hasJarChanges: () => this.hasJarChanges,
        commits: () => this.commits,
        jarDiffs: () => this.jarDiffs,
        nameItem: () => this.nameItem
      }
    });
  }
}

class ViewChangesLink implements IComponentOptions {
  public bindings: any = {
    changeConfig: '<',
    viewType: '@',
    linkText: '@?',
    nameItem: '<'
  };
  public controller: any = ViewChangesLinkController;
  public template = `
    <dt ng-if="$ctrl.viewType === 'description' && $ctrl.changesAvailable">Changes</dt>
    <dd ng-if="$ctrl.viewType === 'description' && $ctrl.changesAvailable">
      <a href ng-click="$ctrl.showChanges()">{{ $ctrl.linkText }}</a>
    </dd>
    <span ng-if="$ctrl.viewType === 'linkOnly' && $ctrl.changesAvailable">
      <a href ng-click="$ctrl.showChanges()">{{ $ctrl.linkText }}</a>
    </span>
  `;
}

export const VIEW_CHANGES_LINK = 'spinnaker.diffs.view.changes.link';
module(VIEW_CHANGES_LINK, [
  COMMIT_HISTORY_COMPONENT,
  JAR_DIFF_COMPONENT,
  EXECUTION_SERVICE
])
  .component('viewChangesLink', new ViewChangesLink());
