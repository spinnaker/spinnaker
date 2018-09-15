import * as React from 'react';
import Select, { Option } from 'react-select';
import { groupBy, reduce, trim, uniq } from 'lodash';

import { AccountService, HelpField, IAccount, IFindImageParams, Tooltip, Spinner } from '@spinnaker/core';

import { DockerImageReader, IDockerImage } from './DockerImageReader';

export interface IDockerImageAndTagSelectorProps {
  specifyTagByRegex: boolean;
  organization: string;
  registry: string;
  repository: string;
  tag: string;
  account: string;
  showRegistry?: boolean;
  labelClass?: string;
  fieldClass?: string;
  onChange: (foo: any) => void;
  deferInitialization?: boolean;
}

export interface IDockerImageAndTagSelectorState {
  accountOptions: Array<Option<string>>;
  imagesLoaded: boolean;
  imagesLoading: boolean;
  imagesRefreshing: boolean;
  organizationMap: { [key: string]: string[] };
  organizationOptions: Array<Option<string>>;
  repositoryMap: { [key: string]: string[] };
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
  };

  private images: IDockerImage[];
  private accounts: string[];

  public constructor(props: IDockerImageAndTagSelectorProps) {
    super(props);

    this.state = {
      accountOptions: [],
      imagesLoaded: false,
      imagesLoading: false,
      imagesRefreshing: false,
      organizationMap: {},
      organizationOptions: [],
      repositoryMap: {},
      repositoryOptions: [],
      tagOptions: [],
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

  private getTags(repositoryMap: { [key: string]: string[] }, repository: string) {
    let tag = this.props.tag;
    let tags: string[] = [];
    if (this.props.specifyTagByRegex) {
      if (tag && trim(tag) === '') {
        tag = '';
      }
    } else {
      if (repositoryMap) {
        tags = repositoryMap[repository] || [];
        if (!tags.includes(tag) && tag && !tag.includes('${')) {
          tag = '';
        }
      }
    }

    return { tag, tags };
  }

  public componentWillReceiveProps(nextProps: IDockerImageAndTagSelectorProps) {
    if (!this.images || ['account', 'showRegistry'].some(key => (this.props as any)[key] !== (nextProps as any)[key])) {
      this.refreshImages(nextProps);
    } else if (
      ['organization', 'registry', 'repository'].some(key => (this.props as any)[key] !== (nextProps as any)[key])
    ) {
      this.updateThings(nextProps);
    }
  }

  private updateThings(props: IDockerImageAndTagSelectorProps) {
    let { account, organization, showRegistry, registry, repository } = props;

    const registryMap = this.getRegistryMap(this.images);
    const accountMap = this.getAccountMap(this.images);
    const newAccounts = this.accounts || Object.keys(accountMap);

    const organizationMap = this.getOrganizationMap(this.images);
    // this.organizations.push(...Object.keys(this.organizationMap)); // wat...
    const repositoryMap = this.getRepositoryMap(this.images);
    const organizations = this.getOrganizationsList(accountMap);

    organization =
      !organizations.includes(organization) && organization && !organization.includes('${') ? null : organization;

    if (showRegistry) {
      registry = registryMap[account];
    }

    const repositories = this.getRepositoryList(organizationMap, organization, registry);

    if (!repositories.includes(repository) && repository && !repository.includes('${')) {
      repository = '';
    }

    const { tag, tags } = this.getTags(repositoryMap, repository);

    if (this.props.onChange) {
      this.props.onChange({ account, organization, registry, repository, tag });
    }

    this.setState({
      accountOptions: newAccounts.sort().map(a => ({ label: a, value: a })), // def internal state
      organizationOptions: organizations
        .filter(o => o)
        .sort()
        .map(o => ({ label: o, value: o })),
      imagesLoaded: true, // def internal state
      organizationMap,
      repositoryMap,
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
        this.updateThings(props);
      })
      .finally(() => {
        this.setState({
          imagesLoading: false,
          imagesRefreshing: false,
        });
      });
  }

  public handleRefreshImages(): void {
    this.refreshImages(this.props);
  }

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
    // TODO: Handle changes
    this.props.onChange && this.props.onChange({ [name]: value });
  }

  public render() {
    const {
      account,
      fieldClass,
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

    return (
      <>
        {showRegistry && (
          <div className="form-group">
            <div className={`sm-label-right ${labelClass}`}>Registry Name</div>
            <div className={fieldClass}>
              <Select
                value={account}
                disabled={imagesRefreshing}
                onChange={(o: Option<string>) => this.valueChanged('account', o.value)}
                options={accountOptions}
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
        )}
        <div className="form-group">
          <div className={`sm-label-right ${labelClass}`}>Organization</div>
          <div className={fieldClass}>
            {imagesLoading && (
              <div className="form-field-loading">
                <Spinner size="small" />
              </div>
            )}
            {!imagesLoading &&
              (organization.includes('${') ? (
                <input
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
                />
              ))}
          </div>
        </div>
        <div className="form-group">
          <div className={`sm-label-right ${labelClass}`}>Image</div>
          <div className={fieldClass}>
            {imagesLoading && (
              <div className="form-field-loading">
                <Spinner size="small" />
              </div>
            )}
            {!imagesLoading &&
              (repository.includes('${') ? (
                <input
                  className="form-control input-sm"
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
                />
              ))}
          </div>
        </div>
        {specifyTagByRegex && (
          <div className="form-group">
            <div className={`sm-label-right ${labelClass}`}>
              Tag <HelpField id="pipeline.config.docker.trigger.tag" />
            </div>
            <div className={fieldClass}>
              <input
                type="text"
                className="form-control input-sm"
                value={tag}
                disabled={imagesRefreshing || !repository}
                onChange={e => this.valueChanged('tag', e.target.value)}
              />
            </div>
          </div>
        )}
        {!specifyTagByRegex && (
          <div className="form-group">
            <div className={`sm-label-right ${labelClass}`}>Tag</div>
            <div className={fieldClass}>
              {imagesLoading && (
                <div className="form-field-loading">
                  <Spinner size="small" />
                </div>
              )}
              {!imagesLoading &&
                (tag.includes('${') ? (
                  <input
                    className="form-control input-sm"
                    value={tag}
                    onChange={e => this.valueChanged('tag', e.target.value)}
                    required={true}
                  />
                ) : (
                  <Select
                    value={tag}
                    disabled={imagesRefreshing || !repository}
                    onChange={(o: Option<string>) => this.valueChanged('tag', o.value)}
                    options={tagOptions}
                    placeholder="No tag"
                    required={true}
                  />
                ))}
            </div>
          </div>
        )}
      </>
    );
  }
}
