import {IComponentController, IComponentOptions, module} from 'angular';
import {IBranch, ScmReader, SCM_READ_SERVICE, ITag, ICommit} from '../services/scm.read.service';
import {IGitTrigger} from 'core/domain/ITrigger';

interface IViewState {
  buildSource: string;
  branches: ISourceState;
  commits: ISourceState;
  tags: ISourceState;
}

interface ISourceState {
  loaded: boolean;
  loading: boolean;
  loadError: boolean;
}

class NetflixCiTriggerHandlerController implements IComponentController {

  public command: any;
  public showOptions = false;
  public viewState: IViewState = {
    buildSource: 'branch',
    branches: { loaded: false, loading: false, loadError: false },
    commits: { loaded: false, loading: false, loadError: false },
    tags: { loaded: false, loading: false, loadError: false },
  };
  public branches: IBranch[];
  public tags: ITag[];
  public commits: ICommit[];

  public buildSources = ['branch', 'commit', 'tag'];

  static get $inject() { return ['scmReader']; }
  constructor(private scmReader: ScmReader) {}

  public $onInit(): void {
    const trigger: IGitTrigger = this.command.trigger;
    this.showOptions = trigger.source === 'stash';
    if (this.showOptions) {
      this.buildSourceChanged();
    }
  }

  public buildSourceChanged(): void {
    this.command.trigger.hash = null;
    if (this.viewState.buildSource === 'tag') {
      this.loadTags();
    }
    if (this.viewState.buildSource === 'branch') {
      this.loadBranches();
    }
    if (this.viewState.buildSource === 'commit') {
      this.loadCommits();
    }
  }

  public branchSelected(): void {
    const selected: IBranch = this.branches.find(b => b.displayId === this.command.trigger.branch);
    if (selected) {
      this.command.trigger.hash = selected.latestCommitId;
    }
  }

  private loadTags(): void {
    const trigger: IGitTrigger = this.command.trigger;
    if (this.viewState.tags.loaded && this.tags.length) {
      trigger.hash = this.tags[0].commitId;
      return;
    }
    this.viewState.tags.loading = true;
    this.scmReader.getTags(trigger.project, trigger.slug)
      .then((tags: ITag[]) => {
        this.tags = tags || [];
        this.viewState.tags.loading = false;
        this.viewState.tags.loaded = true;
        if (this.tags.length) {
          trigger.hash = tags[0].commitId;
        }
      })
      .catch(() => {
        this.viewState.tags.loaded = false;
        this.viewState.tags.loadError = true;
      });
  }

  private loadBranches(): void {
    const trigger: IGitTrigger = this.command.trigger;
    if (this.viewState.branches.loaded && this.branches.length) {
      this.setToDefaultBranch();
      return;
    }
    this.viewState.branches.loading = true;
    this.scmReader.getBranches(trigger.project, trigger.slug)
      .then((branches: IBranch[]) => {
        this.branches = branches || [];
        this.setToDefaultBranch();
        this.viewState.branches.loading = false;
        this.viewState.branches.loaded = true;
      })
      .catch(() => {
        this.viewState.branches.loading = false;
        this.viewState.branches.loadError = true;
      });
  }

  private setToDefaultBranch(): void {
    const defaultBranch = this.branches.find(b => b.default);
    if (defaultBranch) {
      this.command.trigger.branch = defaultBranch.displayId;
      this.branchSelected();
    }
  }

  private loadCommits(): void {
    const trigger: IGitTrigger = this.command.trigger;
    if (this.viewState.commits.loaded && this.commits.length) {
      trigger.hash = this.commits[0].id;
      return;
    }
    this.viewState.commits.loading = true;
    this.scmReader.getCommits(trigger.project, trigger.slug)
      .then((commits: ICommit[]) => {
        this.commits = commits || [];
        if (this.commits.length) {
          this.command.trigger.hash = this.commits[0].id;
        }
        this.viewState.commits.loading = false;
        this.viewState.commits.loaded = true;
      })
      .catch(() => {
        this.viewState.commits.loading = false;
        this.viewState.commits.loadError = true;
      });
  }
}

class NetflixCiTriggerHandlerComponent implements IComponentOptions {
  public bindings: any = {
    command: '<',
  };
  public templateUrl = require('./ci.trigger.handler.component.html');
  public controller: any = NetflixCiTriggerHandlerController;
}

export const NETFLIX_CI_TRIGGER_HANDLER_COMPONENT = 'spinnaker.netflix.ci.trigger.handler.component';
module(NETFLIX_CI_TRIGGER_HANDLER_COMPONENT, [
  SCM_READ_SERVICE,
])
  .component('netflixCiTriggerHandler', new NetflixCiTriggerHandlerComponent());
