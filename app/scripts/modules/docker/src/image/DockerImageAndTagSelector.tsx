import * as React from 'react';
import Select, { Option } from 'react-select';
import { groupBy, reduce, trim, uniq } from 'lodash';

import { AccountService, HelpField, IAccount, IFindImageParams, Tooltip } from '@spinnaker/core';

import { DockerImageReader, IDockerImage } from './DockerImageReader';
import { DockerImageUtils, IDockerImageParts } from './DockerImageUtils';

export interface IDockerImageAndTagChanges {
  account?: string;
  organization?: string;
  registry?: string;
  repository?: string;
  tag?: string;
  imageId?: string;
}

export interface IDockerImageAndTagSelectorProps {
  specifyTagByRegex: boolean;
  imageId: string;
  organization: string;
  registry: string;
  repository: string;
  tag: string;
  account: string;
  showRegistry?: boolean;
  labelClass?: string;
  fieldClass?: string;
  onChange: (changes: IDockerImageAndTagChanges) => void;
  deferInitialization?: boolean;
}

export interface IDockerImageAndTagSelectorState {
  accountOptions: Array<Option<string>>;
  imagesLoaded: boolean;
  imagesLoading: boolean;
  imagesRefreshing: boolean;
  organizationOptions: Array<Option<string>>;
  repositoryOptions: Array<Option<string>>;
  tagOptions: Array<Option<string>>;
}

export class DockerImageAndTagSelector extends React.Component<
  IDockerImageAndTagSelectorProps,
  IDockerImageAndTagSelectorState
> {
  public static defaultProps: Partial<IDockerImageAndTagSelectorProps> = {
    fieldClass: 'col-md-8',
    labelClass: 'col-md-3',
    organization: '',
    registry: '',
    repository: '',
  };

  private images: IDockerImage[];
  private accounts: string[];

  private registryMap: { [key: string]: string };
  private accountMap: { [key: string]: string[] };
  private newAccounts: string[];
  private organizationMap: { [key: string]: string[] };
  private repositoryMap: { [key: string]: string[] };
  private organizations: string[];

  public constructor(props: IDockerImageAndTagSelectorProps) {
    super(props);

    const accountOptions = props.account ? [{ label: props.account, value: props.account }] : [];
    const organizationOptions =
      props.organization && props.organization.length ? [{ label: props.organization, value: props.organization }] : [];
    const repositoryOptions =
      props.repository && props.repository.length ? [{ label: props.repository, value: props.repository }] : [];
    const tagOptions = props.tag && props.tag.length ? [{ label: props.tag, value: props.tag }] : [];

    this.state = {
      accountOptions,
      imagesLoaded: false,
      imagesLoading: false,
      imagesRefreshing: false,
      organizationOptions,
      repositoryOptions,
      tagOptions,
    };
  }

  private getAccountMap(images: IDockerImage[]): { [key: string]: string[] } {
    const groupedImages = groupBy(images.filter(image => image.account), 'account');
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(
          image.map(
            i =>
              `${i.repository
                .split('/')
                .slice(0, -1)
                .join('/')}`,
          ),
        );
        return acc;
      },
      {},
    );
  }

  private getRegistryMap(images: IDockerImage[]) {
    return images.reduce(
      (m: { [key: string]: string }, image: IDockerImage) => {
        m[image.account] = image.registry;
        return m;
      },
      {} as { [key: string]: string },
    );
  }

  private getOrganizationMap(images: IDockerImage[]): { [key: string]: string[] } {
    const extractGroupByKey = (image: IDockerImage) =>
      `${image.account}/${image.repository
        .split('/')
        .slice(0, -1)
        .join('/')}`;
    const groupedImages = groupBy(images.filter(image => image.repository), extractGroupByKey);
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(image.map(i => i.repository));
        return acc;
      },
      {},
    );
  }

  private getRepositoryMap(images: IDockerImage[]) {
    const groupedImages = groupBy(images.filter(image => image.account), 'repository');
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(image.map(i => i.tag));
        return acc;
      },
      {},
    );
  }

  private getOrganizationsList(accountMap: { [key: string]: string[] }) {
    return accountMap ? accountMap[this.props.showRegistry ? this.props.account : this.props.registry] || [] : [];
  }

  private getRepositoryList(organizationMap: { [key: string]: string[] }, organization: string, registry: string) {
    if (organizationMap) {
      const key = `${this.props.showRegistry ? this.props.account : registry}/${organization || ''}`;
      return organizationMap[key] || [];
    }
    return [];
  }

  private getTags(tag: string, repositoryMap: { [key: string]: string[] }, repository: string) {
    let tags: string[] = [];
    if (this.props.specifyTagByRegex) {
      if (tag && trim(tag) === '') {
        tag = undefined;
      }
    } else {
      if (repositoryMap) {
        tags = repositoryMap[repository] || [];
        if (!tags.includes(tag) && tag && !tag.includes('${')) {
          tag = undefined;
        }
      }
    }

    return { tag, tags };
  }

  public componentWillReceiveProps(nextProps: IDockerImageAndTagSelectorProps) {
    if (
      !this.images ||
      ['account', 'showRegistry'].some(
        (key: keyof IDockerImageAndTagSelectorProps) => this.props[key] !== nextProps[key],
      )
    ) {
      this.refreshImages(nextProps);
    } else if (
      ['organization', 'registry', 'repository'].some(
        (key: keyof IDockerImageAndTagSelectorProps) => this.props[key] !== nextProps[key],
      )
    ) {
      this.updateThings(nextProps);
    }
  }

  private synchronizeChanges(values: IDockerImageParts, registry: string) {
    const { organization, repository, tag } = values;
    if (this.props.onChange) {
      const imageId = DockerImageUtils.generateImageId({ organization, repository, tag });
      const changes: IDockerImageAndTagChanges = {};
      if (tag !== this.props.tag) {
        changes.tag = tag;
      }
      if (imageId !== this.props.imageId) {
        changes.imageId = imageId;
      }
      if (organization !== this.props.organization) {
        changes.organization = organization;
      }
      if (registry !== this.props.registry) {
        changes.registry = registry;
      }
      if (repository !== this.props.repository) {
        changes.repository = repository;
      }
      if (Object.keys(changes).length > 0) {
        this.props.onChange(changes);
      }
    }
  }

  private updateThings(props: IDockerImageAndTagSelectorProps) {
    let { organization, registry, repository } = props;
    const { account, showRegistry } = props;

    organization =
      !this.organizations.includes(organization) && organization && !organization.includes('${') ? '' : organization;

    if (showRegistry) {
      registry = this.registryMap[account];
    }

    const repositories = this.getRepositoryList(this.organizationMap, organization, registry);

    if (!repositories.includes(repository) && repository && !repository.includes('${')) {
      repository = '';
    }

    const { tag, tags } = this.getTags(props.tag, this.repositoryMap, repository);

    this.synchronizeChanges({ organization, repository, tag }, registry);

    this.setState({
      accountOptions: this.newAccounts.sort().map(a => ({ label: a, value: a })),
      organizationOptions: this.organizations
        .filter(o => o)
        .sort()
        .map(o => ({ label: o, value: o })),
      imagesLoaded: true,
      repositoryOptions: repositories.sort().map(r => ({ label: r, value: r })),
      tagOptions: tags.sort().map(t => ({ label: t, value: t })),
    });
  }

  private initializeImages(props: IDockerImageAndTagSelectorProps, refresh?: boolean) {
    if (this.state.imagesLoading) {
      return;
    }

    const { showRegistry, account, registry } = props;

    const imageConfig: IFindImageParams = {
      provider: 'dockerRegistry',
      account: showRegistry ? account : registry,
    };

    this.setState({
      imagesLoading: true,
      imagesRefreshing: refresh ? true : false,
    });
    DockerImageReader.findImages(imageConfig)
      .then((images: IDockerImage[]) => {
        this.images = images;
        this.registryMap = this.getRegistryMap(this.images);
        this.accountMap = this.getAccountMap(this.images);
        this.newAccounts = this.accounts || Object.keys(this.accountMap);

        this.organizationMap = this.getOrganizationMap(this.images);
        this.repositoryMap = this.getRepositoryMap(this.images);
        this.organizations = this.getOrganizationsList(this.accountMap);
        this.updateThings(props);
      })
      .finally(() => {
        this.setState({
          imagesLoading: false,
          imagesRefreshing: false,
        });
      });
  }

  public handleRefreshImages = (): void => {
    this.refreshImages(this.props);
  };

  public refreshImages(props: IDockerImageAndTagSelectorProps): void {
    this.initializeImages(props, true);
  }

  private initializeAccounts(props: IDockerImageAndTagSelectorProps) {
    let { account } = props;
    AccountService.listAccounts('dockerRegistry').then((allAccounts: IAccount[]) => {
      const accounts = allAccounts.map((a: IAccount) => a.name);
      if (this.props.showRegistry && !account) {
        account = accounts[0];
      }
      this.accounts = accounts;
      this.refreshImages({ ...props, ...{ account } });
    });
  }

  private isNew(): boolean {
    const { account, organization, registry, repository, tag } = this.props;
    return !account && !organization && !registry && !repository && !tag;
  }

  public componentDidMount() {
    if (!this.props.deferInitialization && (this.props.registry || this.isNew())) {
      this.initializeAccounts(this.props);
    }
  }

  private valueChanged(name: string, value: string) {
    const changes = { [name]: value };
    if (['organization', 'repository', 'tag'].some(n => n === name)) {
      // values are parts of the image
      const { organization, repository, tag } = this.props;
      const imageParts = { ...{ organization, repository, tag }, ...changes };
      const imageId = DockerImageUtils.generateImageId(imageParts);
      changes.imageId = imageId;
    }
    this.props.onChange && this.props.onChange(changes);
  }

  public render() {
    const {
      account,
      fieldClass,
      imageId,
      labelClass,
      organization,
      repository,
      showRegistry,
      specifyTagByRegex,
      tag,
    } = this.props;
    const {
      accountOptions,
      imagesLoading,
      imagesRefreshing,
      organizationOptions,
      repositoryOptions,
      tagOptions,
    } = this.state;

    if (imageId && imageId.includes('${')) {
      return (
        <div className="form-group">
          <div className={`sm-label-right ${labelClass}`}>Image ID</div>
          <div className={fieldClass}>
            <input
              className="form-control input-sm"
              value={imageId}
              onChange={e => this.valueChanged('imageId', e.target.value)}
            />
          </div>
        </div>
      );
    }

    const Registry = showRegistry ? (
      <div className="form-group">
        <div className={`sm-label-right ${labelClass}`}>Registry Name</div>
        <div className={fieldClass}>
          <Select
            value={account}
            disabled={imagesRefreshing}
            onChange={(o: Option<string>) => this.valueChanged('account', o ? o.value : '')}
            options={accountOptions}
            isLoading={imagesRefreshing}
          />
        </div>
        <div className="col-md-1 text-center">
          <Tooltip value={imagesRefreshing ? 'Images refreshing' : 'Refresh images list'}>
            <a className="clickable" onClick={this.handleRefreshImages}>
              <span className={`fa fa-sync-alt ${imagesRefreshing ? 'fa-spin' : ''}`} />
            </a>
          </Tooltip>
        </div>
      </div>
    ) : null;

    const Organization = (
      <div className="form-group">
        <div className={`sm-label-right ${labelClass}`}>Organization</div>
        <div className={fieldClass}>
          {organization.includes('${') ? (
            <input
              disabled={imagesRefreshing}
              className="form-control input-sm"
              value={organization}
              onChange={e => this.valueChanged('organization', e.target.value)}
            />
          ) : (
            <Select
              value={organization}
              disabled={imagesRefreshing}
              onChange={(o: Option<string>) => this.valueChanged('organization', (o && o.value) || '')}
              placeholder="No organization"
              options={organizationOptions}
              isLoading={imagesRefreshing}
            />
          )}
        </div>
      </div>
    );

    const Image = (
      <div className="form-group">
        <div className={`sm-label-right ${labelClass}`}>Image</div>
        <div className={fieldClass}>
          {repository.includes('${') ? (
            <input
              className="form-control input-sm"
              disabled={imagesRefreshing}
              value={repository}
              onChange={e => this.valueChanged('repository', e.target.value)}
            />
          ) : (
            <Select
              value={repository}
              disabled={imagesRefreshing}
              onChange={(o: Option<string>) => this.valueChanged('repository', (o && o.value) || '')}
              options={repositoryOptions}
              required={true}
              isLoading={imagesRefreshing}
            />
          )}
        </div>
      </div>
    );

    const Tag = specifyTagByRegex ? (
      <div className="form-group">
        <div className={`sm-label-right ${labelClass}`}>
          Tag <HelpField id="pipeline.config.docker.trigger.tag" />
        </div>
        <div className={fieldClass}>
          <input
            type="text"
            className="form-control input-sm"
            value={tag || ''}
            disabled={imagesRefreshing || !repository}
            onChange={e => this.valueChanged('tag', e.target.value)}
          />
        </div>
      </div>
    ) : (
      <div className="form-group">
        <div className={`sm-label-right ${labelClass}`}>Tag</div>
        <div className={fieldClass}>
          {tag && tag.includes('${') ? (
            <input
              className="form-control input-sm"
              disabled={imagesRefreshing}
              value={tag || ''}
              onChange={e => this.valueChanged('tag', e.target.value)}
              required={true}
            />
          ) : (
            <Select
              value={tag || ''}
              disabled={imagesRefreshing || !repository}
              isLoading={imagesLoading}
              onChange={(o: Option<string>) => this.valueChanged('tag', o ? o.value : undefined)}
              options={tagOptions}
              placeholder="No tag"
              required={true}
            />
          )}
        </div>
      </div>
    );

    return (
      <>
        {Registry}
        {Organization}
        {Image}
        {Tag}
      </>
    );
  }
}
