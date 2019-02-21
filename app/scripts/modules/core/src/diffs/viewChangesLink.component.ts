import { extend } from 'lodash';
import { IController, IComponentOptions, module } from 'angular';
import { IModalInstanceService, IModalService } from 'angular-ui-bootstrap';

import { IBuildDiffInfo, ICreationMetadata, ICreationMetadataTag, IJenkinsInfo } from 'core/domain';
import { ICommit, COMMIT_HISTORY_COMPONENT } from './commitHistory.component';
import { EXECUTION_SERVICE, ExecutionService } from 'core/pipeline/service/execution.service';
import { JAR_DIFF_COMPONENT, IJarDiff } from './jarDiff.component';

export interface IViewChangesConfig {
  buildInfo?: IBuildDiffInfo;
  commits?: ICommit[];
  jarDiffs?: IJarDiff;
  metadata?: ICreationMetadataTag;
}

class ViewChangesModalController {
  public previousBuildLink: string;
  public currentBuildLink: string;

  public static $inject = ['$uibModalInstance', 'buildInfo', 'commits', 'hasJarChanges', 'jarDiffs', 'nameItem'];
  constructor(
    private $uibModalInstance: IModalInstanceService,
    public buildInfo: IBuildDiffInfo,
    public commits: ICommit[],
    public hasJarChanges: boolean,
    public jarDiffs: IJarDiff,
    public nameItem: { name: string },
  ) {
    if (buildInfo.jenkins) {
      this.previousBuildLink = this.buildJenkinsLink(buildInfo.jenkins, buildInfo.ancestor);
      this.currentBuildLink = this.buildJenkinsLink(buildInfo.jenkins, buildInfo.target);
    }
  }

  public buildJenkinsLink(jenkins: IJenkinsInfo, build: string): string {
    let result: string;
    if (build) {
      result = `${jenkins.host}job/${jenkins.name}/${build}`;
    }

    return result;
  }

  public close(): void {
    this.$uibModalInstance.close();
  }
}

class ViewChangesLinkController implements IController {
  public changeConfig: IViewChangesConfig;
  public viewType: string;
  public linkText = 'View Changes';
  public nameItem: { name: string };

  public changesAvailable = false;
  public hasJarChanges = false;
  public metadata: ICreationMetadataTag;
  public commits: ICommit[];
  public jarDiffs: IJarDiff;

  private loadingExecution = false;
  private executionLoaded = false;

  public static $inject = ['$uibModal', 'executionService'];
  constructor(private $uibModal: IModalService, private executionService: ExecutionService) {}

  private setJarDiffs(): void {
    this.hasJarChanges = Object.keys(this.jarDiffs).some(
      (key: string) => Array.isArray(this.jarDiffs[key]) && this.jarDiffs[key].length > 0,
    );
  }

  private lookForDiffs(stageId: string, executionId: string): void {
    if (this.executionLoaded || this.loadingExecution) {
      return;
    }
    this.loadingExecution = true;
    this.executionService
      .getExecution(executionId)
      .then((details: any) => {
        const stage: any = details.stages.find((s: any) => s.id === stageId);
        this.jarDiffs = stage.context.jarDiffs || {};
        this.commits = stage.context.commits || [];
        extend(this.changeConfig.buildInfo, stage.context.buildInfo);
        this.setJarDiffs();

        if (this.hasJarChanges || this.commits.length) {
          this.changesAvailable = true;
        }
        // if the stage is still running, and we haven't found commits or changes, reload it on the next refresh cycle
        this.executionLoaded = stage.status !== 'RUNNING' || this.changesAvailable;
      })
      .catch(() => {})
      .finally(() => (this.loadingExecution = false));
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
        buildInfo: () => this.changeConfig.buildInfo,
        commits: () => this.commits,
        hasJarChanges: () => this.hasJarChanges,
        jarDiffs: () => this.jarDiffs,
        nameItem: () => this.nameItem,
      },
    });
  }
}

const viewChangesLink: IComponentOptions = {
  bindings: {
    changeConfig: '<',
    viewType: '@',
    linkText: '@?',
    nameItem: '<',
  },
  controller: ViewChangesLinkController,
  template: `
    <dt ng-if="$ctrl.viewType === 'description' && $ctrl.changesAvailable">Changes</dt>
    <dd ng-if="$ctrl.viewType === 'description' && $ctrl.changesAvailable">
      <a href ng-click="$ctrl.showChanges()">{{ $ctrl.linkText }}</a>
    </dd>
    <span ng-if="$ctrl.viewType === 'linkOnly' && $ctrl.changesAvailable">
      <a href ng-click="$ctrl.showChanges()">{{ $ctrl.linkText }}</a>
    </span>
  `,
};

export const viewChangesLinkWrapper: IComponentOptions = {
  bindings: {
    changeConfig: '<',
    viewType: '<',
    linkText: '<?',
    nameItem: '<',
  },
  tempate: `<view-changes-link change-config="$ctrl.changeConfig" view-type="{{::$ctrl.viewType}}" link-text="{{::$ctrl.linkText}}" name-item="$ctrl.nameItem"></view-changes-link>`,
};

export const VIEW_CHANGES_LINK = 'spinnaker.diffs.view.changes.link';
module(VIEW_CHANGES_LINK, [COMMIT_HISTORY_COMPONENT, JAR_DIFF_COMPONENT, EXECUTION_SERVICE])
  .component('viewChangesLink', viewChangesLink)
  .component('viewChangesLinkWrapper', viewChangesLinkWrapper);
