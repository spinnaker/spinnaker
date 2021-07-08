import { IChangesObject, IController, IOnChangesObject, module } from 'angular';
import { isString, trim, uniq } from 'lodash';

import { AccountService, IAccount, IFindImageParams } from '@spinnaker/core';

import { DockerImageReader, IDockerImage } from '@spinnaker/docker';

interface IViewState {
  imagesLoading: boolean;
  imagesLoaded: boolean;
  imagesRefreshing: boolean;
}

export interface IOnDockerBindingsChanges extends IOnChangesObject {
  registry: IChangesObject<string>;
}

export class DockerImageAndTagSelectorController implements IController {
  private viewState: IViewState = {
    imagesLoading: false,
    imagesLoaded: false,
    imagesRefreshing: false,
  };

  private accountMap: { [key: string]: string[] };
  private organizationMap: { [key: string]: string[] };
  private repositoryMap: { [key: string]: string[] };
  private registryMap: { [key: string]: string };
  private imageLoader: PromiseLike<any>;

  public labelClass: string;
  public fieldClass: string;
  public showRegistry: boolean;
  public registry: string;
  public accounts: string[];
  public account: string;
  public organizations: string[] = [];
  public organization: string;
  public repositories: string[];
  public repository: string;
  public specifyTagByRegex: boolean;
  public tags: string[];
  public tag: string;
  public onChange: Function;
  public deferInitialization: boolean;

  private updateOrganizationsList(): void {
    if (this.accountMap) {
      if (this.showRegistry) {
        this.registry = this.registryMap[this.account];
        if (this.onChange) {
          this.onChange({
            registry: this.registry,
          });
        }
      }
      this.organizations = this.accountMap[this.showRegistry ? this.account : this.registry] || [];
      if (!this.organizations.includes(this.organization) && this.organization && !this.organization.includes('${')) {
        this.organization = null;
      }

      this.updateRepositoryList();
    }
  }

  private updateRepositoryList(): void {
    if (this.organizationMap) {
      const key = `${this.showRegistry ? this.account : this.registry}/${this.organization || ''}`;
      this.repositories = this.organizationMap[key] || [];
      if (!this.repositories.includes(this.repository) && this.repository && !this.repository.includes('${')) {
        this.repository = null;
      }

      this.updateTag();
    }
  }

  private updateTag(): void {
    if (this.specifyTagByRegex) {
      if (trim(this.tag) === '' && this.tag) {
        this.tag = null;
      }
    } else {
      if (this.repositoryMap) {
        const key = this.repository;
        this.tags = this.repositoryMap[key] || [];
        if (!this.tags.includes(this.tag) && this.tag && !this.tag.includes('${')) {
          this.tag = null;
        }
      }
    }
  }

  private updateRegistryMap(images: IDockerImage[]): void {
    this.registryMap = images.reduce((map: { [key: string]: string }, image: IDockerImage) => {
      map[image.account] = image.registry;
      return map;
    }, {});
  }

  private updateAccountMap(images: IDockerImage[]): void {
    const results: { [key: string]: string[] } = images.reduce(
      (map: { [key: string]: string[] }, image: IDockerImage) => {
        const key = image.account;
        if (!key) {
          return map;
        }

        const parts: string[] = image.repository.split('/');
        parts.pop();
        const org: string = parts.join('/');
        if (!map[key]) {
          map[key] = [];
        }
        map[key].push(org);

        return map;
      },
      {},
    );
    this.uniqMapEntries(results);
    this.accountMap = results;
  }

  private updateOrganizationMap(images: IDockerImage[]): void {
    const results: { [key: string]: string[] } = images.reduce(
      (map: { [key: string]: string[] }, image: IDockerImage) => {
        if (!image.repository) {
          return map;
        }

        const parts = image.repository.split('/');
        parts.pop();
        const key = `${image.account}/${parts.join('/')}`;
        if (!map[key]) {
          map[key] = [];
        }
        map[key].push(image.repository);
        return map;
      },
      {},
    );
    this.uniqMapEntries(results);
    this.organizationMap = results;
  }

  private updateRepositoryMap(images: IDockerImage[]): void {
    const results: { [key: string]: string[] } = images.reduce(
      (map: { [key: string]: string[] }, image: IDockerImage) => {
        if (!image.repository) {
          return map;
        }

        const key: string = image.repository;
        if (!map[key]) {
          map[key] = [];
        }
        map[key].push(image.tag);
        return map;
      },
      {},
    );

    this.uniqMapEntries(results);
    this.repositoryMap = results;
  }

  private initializeAccounts() {
    AccountService.listAccounts('dockerRegistry').then((accounts: IAccount[]) => {
      this.accounts = accounts.map((account: IAccount) => account.name);
      if (this.showRegistry && !this.account) {
        this.account = this.accounts[0];
      }

      this.refreshImages();
    });
  }

  private initializeImages() {
    const imageConfig: IFindImageParams = {
      provider: 'dockerRegistry',
      account: this.showRegistry ? this.account : this.registry,
    };

    this.viewState.imagesLoading = true;
    const imageLoader = DockerImageReader.findImages(imageConfig)
      .then((images: IDockerImage[]) => {
        if (this.imageLoader !== imageLoader) {
          // something else is getting loaded
          return;
        }
        this.updateRegistryMap(images);
        this.updateAccountMap(images);
        this.accounts = this.accounts || Object.keys(this.accountMap);

        this.updateOrganizationMap(images);
        this.organizations.push(...Object.keys(this.organizationMap));
        this.updateRepositoryMap(images);
        this.updateOrganizationsList();

        this.viewState.imagesLoaded = true;
      })
      .finally(() => {
        this.viewState.imagesLoading = false;
        this.viewState.imagesRefreshing = false;
      });
    this.imageLoader = imageLoader;
  }

  private uniqMapEntries(map: { [key: string]: string[] }): void {
    Object.keys(map).forEach((k) => {
      map[k] = uniq(map[k]);
    });
  }

  private isNew(): boolean {
    return !this.account && !this.organization && !this.registry && !this.repository && !this.tag;
  }

  public refreshImages(): void {
    this.viewState.imagesRefreshing = true;
    this.initializeImages();
  }

  public changeAccount() {
    this.organization = this.repository = this.tag = null;
    this.initializeImages();
  }

  public hasSpel(value: string): boolean {
    return value.includes('${');
  }

  public $onInit(): void {
    this.labelClass = this.labelClass || 'col-md-3';
    this.fieldClass = this.fieldClass || 'col-md-8';
    if (!this.deferInitialization && (this.registry || this.isNew())) {
      this.initializeAccounts();
    }
  }

  public $onChanges(changes: IOnDockerBindingsChanges): void {
    if (this.deferInitialization && changes.registry && isString(changes.registry.currentValue)) {
      this.initializeAccounts();
    }
  }
}

const dockerImageAndTagSelectorComponent: ng.IComponentOptions = {
  bindings: {
    specifyTagByRegex: '=',
    organization: '=',
    registry: '<',
    repository: '=',
    tag: '=',
    account: '=',
    showRegistry: '<?',
    labelClass: '@?',
    fieldClass: '@?',
    onChange: '=?',
    deferInitialization: '=?',
  },
  controller: DockerImageAndTagSelectorController,
  templateUrl: require('./dockerImageAndTagSelector.component.html'),
};

export const DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT = 'spinnaker.docker.imageAndTagSelector.component';
module(DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT, []).component(
  'dockerImageAndTagSelector',
  dockerImageAndTagSelectorComponent,
);
