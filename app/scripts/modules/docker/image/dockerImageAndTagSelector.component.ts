import {isString, trim} from 'lodash';
import {module} from 'angular';
import {DOCKER_IMAGE_READER_SERVICE, DockerImageReaderService, IDockerImage} from './docker.image.reader.service';

interface IViewState {
  imagesLoading: boolean;
  imagesLoaded: boolean;
  imagesRefreshing: boolean;
}

interface IImageConfig {
  provider: string;
  account: string;
}

interface IOnDockerBindingsChanges extends ng.IOnChangesObject {
  registry: ng.IChangesObject;
}

class DockerImageAndTagSelectorController implements ng.IComponentController {

  private viewState: IViewState = {
    imagesLoading: false,
    imagesLoaded: false,
    imagesRefreshing: false
  };

  private accountMap: { [key: string]: string[] };
  private organizationMap: { [key: string]: string[] };
  private repositoryMap: { [key: string]: string[] };
  private registryMap: { [key: string]: string };

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

  static get $inject() {
    return ['accountService', 'dockerImageReader'];
  }

  constructor(private accountService: any,
              private dockerImageReader: DockerImageReaderService) {}

  private updateOrganizationsList(): void {
    if (this.accountMap) {
      if (this.showRegistry) {
        this.registry = this.registryMap[this.account];
        if (this.onChange) {
          this.onChange({
            registry: this.registry
          });
        }
      }

      this.organizations = this.accountMap[this.showRegistry ? this.account : this.registry] || [];
      if (!this.organizations.includes(this.organization)) {
        this.organization = null;
      }

      this.updateRepositoryList();
    }
  }

  private updateRepositoryList(): void {
    if (this.organizationMap) {
      const key = `${this.showRegistry ? this.account : this.registry}/${this.organization || '' }`;
      this.repositories = this.organizationMap[key] || [];
      if (!this.repositories.includes(this.repository)) {
        this.repository = null;
      }

      this.updateTag();
    }
  }

  private updateTag(): void {
    if (this.specifyTagByRegex) {
      if (trim(this.tag) === '') {
        this.tag = null;
      }
    } else {
      if (this.repositoryMap) {
        const key = this.repository;
        this.tags = this.repositoryMap[key] || [];
        if (!this.tags.includes(this.tag)) {
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
    this.accountMap = images.reduce((map: { [key: string]: string[] }, image: IDockerImage) => {
      const key = image.account;
      if (!key) {
        return map;
      }

      const all: string[] = map[key] || [];
      const parts: string[] = image.repository.split('/');
      parts.pop();
      const org: string = parts.join('/');
      if (!all.includes(org)) {
        map[key] = all.concat(org);
      }

      return map;
    }, {});
  }

  private updateOrganizationMap(images: IDockerImage[]): void {
    this.organizationMap = images.reduce((map: { [key: string]: string[] }, image: IDockerImage) => {
      if (!image.repository) {
        return map;
      }

      const parts = image.repository.split('/');
      parts.pop();
      const key = `${image.account}/${parts.join('/')}`;
      const all: string[] = map[key] || [];
      if (!all.includes((image.repository))) {
        map[key] = all.concat(image.repository);
      }

      return map;
    }, {});
  }

  private updateRepositoryMap(images: IDockerImage[]): void {
    this.repositoryMap = images.reduce((map: { [key: string]: string[] }, image: IDockerImage) => {
      if (!image.repository) {
        return map;
      }

      const key: string = image.repository;
      const all: string[] = map[key] || [];
      if (!all.includes(image.tag)) {
        map[key] = all.concat(image.tag);
      }

      return map;
    }, {});
  }

  private initializeAccounts() {
    this.accountService.listAccounts('dockerRegistry').then((accounts: any) => {
      this.accounts = accounts.map((account: any) => account.name);
      if (this.showRegistry && !this.account) {
        this.account = this.accounts[0];
      }

      this.refreshImages();
    });
  }

  private initializeImages() {
    const imageConfig: IImageConfig = {
      provider: 'dockerRegistry',
      account: this.showRegistry ? this.account : this.registry
    };

    if (!this.viewState.imagesLoading) {
      this.viewState.imagesLoading = true;
      this.dockerImageReader.findImages(imageConfig).then((images: IDockerImage[]) => {
        this.updateRegistryMap(images);
        this.updateAccountMap(images);
        this.accounts = this.accounts || Object.keys(this.accountMap);

        this.updateOrganizationMap(images);
        this.organizations.push(...Object.keys(this.organizationMap));
        this.updateOrganizationsList();

        this.updateRepositoryMap(images);

        this.viewState.imagesLoaded = true;
      }).finally(() => {
        this.viewState.imagesLoading = false;
        this.viewState.imagesRefreshing = false;
      });
    }
  }

  private isNew(): boolean {
    return (!this.account && !this.organization && !this.registry && !this.repository && !this.tag);
  }

  public refreshImages(): void {
    this.viewState.imagesRefreshing = true;
    this.initializeImages();
  }

  public changeAccount() {
    this.organization = this.repository = this.tag = null;
    this.initializeImages();
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

class DockerImageAndTagSelectorComponent implements ng.IComponentOptions {
  public bindings: any = {
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
    deferInitialization: '=?'
  };
  public controller: ng.IComponentController = DockerImageAndTagSelectorController;
  public templateUrl: string = require('./dockerImageAndTagSelector.component.html');
}

export const DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT_MODULE = 'spinnaker.deck.docker.imageAndTagSelector.component';
module(DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT_MODULE, [require('core/account/account.service.js'), DOCKER_IMAGE_READER_SERVICE])
  .component('dockerImageAndTagSelector', new DockerImageAndTagSelectorComponent());
