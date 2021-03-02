import { groupBy, reduce, trim, uniq } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import { AccountService, HelpField, IAccount, IFindImageParams, Tooltip, ValidationMessage } from '@spinnaker/core';

import { DockerImageReader, IDockerImage } from './DockerImageReader';
import { DockerImageUtils, IDockerImageParts } from './DockerImageUtils';

export type IDockerLookupType = 'tag' | 'digest';

export interface IDockerImageAndTagChanges {
  account?: string;
  organization?: string;
  registry?: string;
  repository?: string;
  tag?: string;
  digest?: string;
  imageId?: string;
}

export interface IDockerImageAndTagSelectorProps {
  specifyTagByRegex: boolean;
  imageId: string;
  organization: string;
  registry: string;
  repository: string;
  tag: string;
  digest: string;
  account: string;
  showRegistry?: boolean;
  onChange: (changes: IDockerImageAndTagChanges) => void;
  deferInitialization?: boolean;
  showDigest?: boolean;
  allowManualDefinition?: boolean;
}

export interface IDockerImageAndTagSelectorState {
  accountOptions: Array<Option<string>>;
  switchedManualWarning: string;
  missingFields?: string[];
  imagesLoaded: boolean;
  imagesLoading: boolean;
  organizationOptions: Array<Option<string>>;
  repositoryOptions: Array<Option<string>>;
  defineManually: boolean;
  tagOptions: Array<Option<string>>;
  lookupType: IDockerLookupType;
}

const imageFields = ['organization', 'repository', 'tag', 'digest'];
const defineOptions = [
  { label: 'Manually', value: true },
  { label: 'Select from list', value: false },
];

export class DockerImageAndTagSelector extends React.Component<
  IDockerImageAndTagSelectorProps,
  IDockerImageAndTagSelectorState
> {
  public static defaultProps: Partial<IDockerImageAndTagSelectorProps> = {
    organization: '',
    registry: '',
    repository: '',
    showDigest: true,
    allowManualDefinition: true,
  };

  private unmounted = false;
  private images: IDockerImage[];
  private accounts: string[];

  private registryMap: { [key: string]: string };
  private accountMap: { [key: string]: string[] };
  private newAccounts: string[];
  private organizationMap: { [key: string]: string[] };
  private repositoryMap: { [key: string]: string[] };
  private organizations: string[];
  private cachedValues: { [key: string]: string } = {};

  public constructor(props: IDockerImageAndTagSelectorProps) {
    super(props);

    const accountOptions = props.account ? [{ label: props.account, value: props.account }] : [];
    const organizationOptions =
      props.organization && props.organization.length ? [{ label: props.organization, value: props.organization }] : [];
    const repositoryOptions =
      props.repository && props.repository.length ? [{ label: props.repository, value: props.repository }] : [];
    const tagOptions = props.tag && props.tag.length ? [{ label: props.tag, value: props.tag }] : [];
    const parsedImageId = DockerImageUtils.splitImageId(props.imageId);
    const defineManually = props.allowManualDefinition && Boolean(props.imageId && props.imageId.includes('${'));

    this.state = {
      accountOptions,
      switchedManualWarning: undefined,
      imagesLoaded: false,
      imagesLoading: false,
      organizationOptions,
      repositoryOptions,
      defineManually,
      tagOptions,
      lookupType: props.digest || parsedImageId.digest ? 'digest' : 'tag',
    };
  }

  private getAccountMap(images: IDockerImage[]): { [key: string]: string[] } {
    const groupedImages = groupBy(
      images.filter((image) => image.account),
      'account',
    );
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(image.map((i) => `${i.repository.split('/').slice(0, -1).join('/')}`));
        return acc;
      },
      {},
    );
  }

  private getRegistryMap(images: IDockerImage[]) {
    return images.reduce((m: { [key: string]: string }, image: IDockerImage) => {
      m[image.account] = image.registry;
      return m;
    }, {} as { [key: string]: string });
  }

  private getOrganizationMap(images: IDockerImage[]): { [key: string]: string[] } {
    const extractGroupByKey = (image: IDockerImage) =>
      `${image.account}/${image.repository.split('/').slice(0, -1).join('/')}`;
    const groupedImages = groupBy(
      images.filter((image) => image.repository),
      extractGroupByKey,
    );
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(image.map((i) => i.repository));
        return acc;
      },
      {},
    );
  }

  private getRepositoryMap(images: IDockerImage[]) {
    const groupedImages = groupBy(
      images.filter((image) => image.account),
      'repository',
    );
    return reduce<IDockerImage[], { [key: string]: string[] }>(
      groupedImages,
      (acc, image, key) => {
        acc[key] = uniq(image.map((i) => i.tag));
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

    if (nextProps.imageId && nextProps.imageId.includes('${')) {
      this.setState({ defineManually: true });
    }
  }

  componentWillUnmount() {
    this.unmounted = true;
  }

  private synchronizeChanges(values: IDockerImageParts, registry: string) {
    const { organization, repository, tag, digest } = values;
    if (this.props.onChange) {
      const imageId = DockerImageUtils.generateImageId({ organization, repository, tag, digest });
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
      if (digest !== this.props.digest) {
        changes.digest = digest;
      }
      if (Object.keys(changes).length > 0) {
        this.props.onChange(changes);
      }
    }
  }

  private updateThings(props: IDockerImageAndTagSelectorProps, allowAutoSwitchToManualEntry = false) {
    if (!this.repositoryMap || this.unmounted) {
      return;
    }

    const { imageId, specifyTagByRegex } = props;
    let { organization, registry, repository } = props;

    if (props.showRegistry) {
      registry = this.registryMap[props.account];
    }

    const organizationFound = !organization || this.organizations.includes(organization) || organization.includes('${');
    if (!organizationFound) {
      organization = '';
    }

    const repositories = this.getRepositoryList(this.organizationMap, organization, registry);
    const repositoryFound = !repository || repository.includes('${') || repositories.includes(repository);

    if (!repositoryFound) {
      repository = '';
    }

    const { tag, tags } = this.getTags(props.tag, this.repositoryMap, repository);
    const tagFound = tag === props.tag || specifyTagByRegex;

    const newState = {
      accountOptions: this.newAccounts.sort().map((a) => ({ label: a, value: a })),
      organizationOptions: this.organizations
        .filter((o) => o)
        .sort()
        .map((o) => ({ label: o, value: o })),
      imagesLoaded: true,
      repositoryOptions: repositories.sort().map((r) => ({ label: r, value: r })),
      tagOptions: tags.sort().map((t) => ({ label: t, value: t })),
    } as IDockerImageAndTagSelectorState;

    if (
      imageId &&
      (!this.state.imagesLoaded || allowAutoSwitchToManualEntry) &&
      (!organizationFound || !repositoryFound || !tagFound)
    ) {
      newState.defineManually = true;

      const missingFields: string[] = [];
      if (!organizationFound) {
        missingFields.push('organization');
      }
      if (!repositoryFound) {
        missingFields.push('image');
      }
      if (!tagFound) {
        missingFields.push('tag');
      }
      newState.missingFields = missingFields;
      newState.switchedManualWarning = `Could not find ${missingFields.join(' or ')}, switched to manual entry`;
    } else if (!imageId || !imageId.includes('${')) {
      this.synchronizeChanges(
        this.state.defineManually
          ? DockerImageUtils.splitImageId(imageId)
          : { organization, repository, tag, digest: this.props.digest },
        registry,
      );
    }

    this.setState(newState);
  }

  private initializeImages(props: IDockerImageAndTagSelectorProps) {
    if (this.state.imagesLoading) {
      return;
    }

    const { showRegistry, account, registry } = props;

    const imageConfig: IFindImageParams = {
      provider: 'dockerRegistry',
      account: showRegistry ? account : registry,
    };

    this.setState({ imagesLoading: true });
    DockerImageReader.findImages(imageConfig)
      .then((images: IDockerImage[]) => {
        this.images = images;
        this.registryMap = this.getRegistryMap(this.images);
        this.accountMap = this.getAccountMap(this.images);
        this.newAccounts = this.accounts || Object.keys(this.accountMap);

        this.organizationMap = this.getOrganizationMap(this.images);
        this.repositoryMap = this.getRepositoryMap(this.images);
        this.organizations = this.getOrganizationsList(this.accountMap);
        this.updateThings(props, true);
      })
      .finally(() => {
        if (!this.unmounted) {
          this.setState({ imagesLoading: false });
        }
      });
  }

  public handleRefreshImages = (): void => {
    this.refreshImages(this.props);
  };

  public refreshImages(props: IDockerImageAndTagSelectorProps): void {
    this.initializeImages(props);
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
    if (imageFields.some((n) => n === name)) {
      // values are parts of the image
      const { organization, repository, tag, digest } = this.props;
      const imageParts = { ...{ organization, repository, tag, digest }, ...changes };
      const imageId = DockerImageUtils.generateImageId(imageParts);
      changes.imageId = imageId;
    }
    this.props.onChange && this.props.onChange(changes);
  }

  private lookupTypeChanged = (o: Option<IDockerLookupType>) => {
    const newType = o.value;
    const oldType = this.state.lookupType;
    const oldValue = this.props[oldType];
    const cachedValue = this.cachedValues[newType];

    this.valueChanged(oldType, undefined);
    if (this.cachedValues[newType]) {
      this.valueChanged(newType, cachedValue);
    }
    this.setState({ lookupType: newType });
    this.cachedValues[oldType] = oldValue;
  };

  private showManualInput = (defineManually: boolean) => {
    if (!defineManually) {
      const newFields = DockerImageUtils.splitImageId(this.props.imageId || '');
      this.props.onChange(newFields);
      if (this.state.switchedManualWarning) {
        this.setState({ switchedManualWarning: undefined, missingFields: undefined });
      }
    }
    this.setState({ defineManually });
  };

  public render() {
    const {
      account,
      allowManualDefinition,
      digest,
      imageId,
      organization,
      repository,
      showDigest,
      showRegistry,
      specifyTagByRegex,
      tag,
    } = this.props;
    const {
      accountOptions,
      switchedManualWarning,
      missingFields,
      imagesLoading,
      lookupType,
      organizationOptions,
      repositoryOptions,
      defineManually,
      tagOptions,
    } = this.state;

    const parsedImageId = DockerImageUtils.splitImageId(imageId);

    const manualInputToggle = (
      <div className="sp-formItem groupHeader">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Define Image ID</div>

          <div className="sp-formActions sp-formActions--mobile">
            <span className="action" />
          </div>
        </div>

        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              <Select
                value={defineManually}
                disabled={imagesLoading || !allowManualDefinition}
                onChange={(o: Option<boolean>) => this.showManualInput(o.value)}
                options={defineOptions}
                clearable={false}
              />
            </span>
          </div>
        </div>
      </div>
    );

    const warning = switchedManualWarning ? (
      <div className="sp-formItem">
        <div className="sp-formItem__left" />
        <div className="sp-formItem__right">
          <ValidationMessage
            type="warning"
            message={
              <>
                {switchedManualWarning}
                {(missingFields || []).map((f) => (
                  <div key={f}>
                    <HelpField expand={true} id={`pipeline.config.docker.trigger.missing.${f}`} />
                  </div>
                ))}
              </>
            }
          />
        </div>
      </div>
    ) : null;

    if (defineManually) {
      return (
        <div className="sp-formGroup">
          {manualInputToggle}
          <div className="sp-formItem">
            <div className="sp-formItem__left">
              <div className="sp-formLabel">Image ID</div>
              <div className="sp-formActions sp-formActions--mobile">
                <span className="action" />
              </div>
            </div>
            <div className="sp-formItem__right">
              <div className="sp-form">
                <span className="field">
                  <input
                    className="form-control input-sm"
                    value={imageId || ''}
                    onChange={(e) => this.valueChanged('imageId', e.target.value)}
                  />
                </span>
              </div>
            </div>
          </div>
          {warning}
        </div>
      );
    }

    const Registry = showRegistry ? (
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Registry Name</div>
        </div>
        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              <Select
                value={account}
                disabled={imagesLoading}
                onChange={(o: Option<string>) => this.valueChanged('account', o ? o.value : '')}
                options={accountOptions}
                isLoading={imagesLoading}
              />
            </span>
            <span className="sp-formActions sp-formActions--web">
              <span className="action">
                <Tooltip value={imagesLoading ? 'Images refreshing' : 'Refresh images list'}>
                  <i
                    className={`fa icon-button-refresh-arrows ${imagesLoading ? 'fa-spin' : ''}`}
                    onClick={this.handleRefreshImages}
                  />
                </Tooltip>
              </span>
            </span>
          </div>
        </div>
      </div>
    ) : null;

    const Organization = (
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Organization</div>
        </div>

        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              {organization.includes('${') ? (
                <input
                  disabled={imagesLoading}
                  className="form-control input-sm"
                  value={organization || ''}
                  onChange={(e) => this.valueChanged('organization', e.target.value)}
                />
              ) : (
                <Select
                  value={organization || ''}
                  disabled={imagesLoading}
                  onChange={(o: Option<string>) => this.valueChanged('organization', (o && o.value) || '')}
                  placeholder="No organization"
                  options={organizationOptions}
                  isLoading={imagesLoading}
                />
              )}
            </span>
          </div>
        </div>
      </div>
    );

    const Image = (
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Image</div>
        </div>

        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              {repository.includes('${') ? (
                <input
                  className="form-control input-sm"
                  disabled={imagesLoading}
                  value={repository || ''}
                  onChange={(e) => this.valueChanged('repository', e.target.value)}
                />
              ) : (
                <Select
                  value={repository || ''}
                  disabled={imagesLoading}
                  onChange={(o: Option<string>) => this.valueChanged('repository', (o && o.value) || '')}
                  options={repositoryOptions}
                  required={true}
                  isLoading={imagesLoading}
                />
              )}
            </span>
          </div>
        </div>
      </div>
    );

    const Tag =
      lookupType === 'tag' ? (
        specifyTagByRegex ? (
          <div className="sp-formItem">
            <div className="sp-formItem__left">
              <div className="sp-formLabel">Tag</div>
            </div>

            <div className="sp-formItem__right">
              <div className="sp-form">
                <span className="field">
                  <input
                    type="text"
                    className="form-control input-sm"
                    value={tag || ''}
                    disabled={imagesLoading || !repository}
                    onChange={(e) => this.valueChanged('tag', e.target.value)}
                  />
                </span>
              </div>
              <HelpField id="pipeline.config.docker.trigger.tag" expand={true} />
            </div>
          </div>
        ) : (
          <div className="sp-formItem">
            <div className="sp-formItem__left">
              <div className="sp-formLabel">Tag</div>
            </div>

            <div className="sp-formItem__right">
              <div className="sp-form">
                <span className="field">
                  {tag && tag.includes('${') ? (
                    <input
                      className="form-control input-sm"
                      disabled={imagesLoading}
                      value={tag || ''}
                      onChange={(e) => this.valueChanged('tag', e.target.value)}
                      required={true}
                    />
                  ) : (
                    <>
                      <Select
                        value={tag || ''}
                        disabled={imagesLoading || !repository}
                        isLoading={imagesLoading}
                        onChange={(o: Option<string>) => this.valueChanged('tag', o ? o.value : undefined)}
                        options={tagOptions}
                        placeholder="No tag"
                        required={true}
                      />
                      <HelpField id="pipeline.config.docker.trigger.tag.additionalInfo" expand={true} />
                    </>
                  )}
                </span>
              </div>
            </div>
          </div>
        )
      ) : null;

    const Digest =
      lookupType === 'digest' ? (
        <div className="sp-formItem">
          <div className="sp-formItem__left">
            <div className="sp-formLabel">
              Digest <HelpField id="pipeline.config.docker.trigger.digest" />
            </div>
          </div>
          <div className="sp-formItem__right">
            <div className="sp-form">
              <span className="field">
                <input
                  className="form-control input-sm"
                  placeholder="sha256:abc123"
                  value={digest || parsedImageId.digest || ''}
                  onChange={(e) => this.valueChanged('digest', e.target.value)}
                  required={true}
                />
              </span>
            </div>
          </div>
        </div>
      ) : null;

    const LookupTypeSelector = showDigest ? (
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          <div className="sp-formLabel">Type</div>
        </div>

        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              <Select
                clearable={false}
                value={lookupType}
                options={[
                  { value: 'digest', label: 'Digest' },
                  { value: 'tag', label: 'Tag' },
                ]}
                onChange={this.lookupTypeChanged}
              />
            </span>
          </div>
        </div>
      </div>
    ) : null;

    return (
      <div className="sp-formGroup">
        {manualInputToggle}
        {Registry}
        {Organization}
        {Image}
        {LookupTypeSelector}
        {Digest}
        {Tag}
      </div>
    );
  }
}
