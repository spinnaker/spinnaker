import { cloneDeep, get, has, set } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { DeleteApplicationSection } from './DeleteApplicationSection';
import type { IAccountDetails, IAggregatedAccounts } from '../../account/AccountService';
import { AccountService } from '../../account/AccountService';
import { AngularServices } from '../../angular/services';
import type { Application } from '../application.model';
import { ClusterMatcher } from '../../cluster';
import { SETTINGS } from '../../config/settings';
import { ConfirmationModalService } from '../../confirmationModal';
import type { ICustomBannerConfig } from './customBanner/CustomBannerConfig';
import { CustomBannerConfig } from './customBanner/CustomBannerConfig';
import type { IDefaultTagFilterConfig } from './defaultTagFilter/DefaultTagFilterConfig';
import { DefaultTagFilterConfig } from './defaultTagFilter/DefaultTagFilterConfig';
import type { INotification } from '../../domain';
import { ConfigSectionFooter } from './footer/ConfigSectionFooter';
import { HelpField } from '../../help';
import { ManagedResourceConfig } from '../../managed/externals/ManagedResourceConfig';
import { ModalClose } from '../../modal';
import type { IPermissions } from '../modal/PermissionsConfigurer';
import { PermissionsConfigurer } from '../modal/PermissionsConfigurer';
import { NotificationsList } from '../../notification';
import type { IOverridableProps } from '../../overrideRegistry';
import { overridableComponent } from '../../overrideRegistry';
import type { IModalComponentProps } from '../../presentation';
import { ReactModal, ReactSelectInput } from '../../presentation';
import { Markdown } from '../../presentation/Markdown';
import { PageNavigator, PageSection } from '../../presentation/navigation';
import { ApplicationReader } from '../service/ApplicationReader';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { SnapshotReader } from '../../snapshot/SnapshotReader';
import { SnapshotWriter } from '../../snapshot/SnapshotWriter';
import { TaskReader } from '../../task';
import { DiffView } from '../../utils/json/DiffView';
import { JsonUtils } from '../../utils/json/JsonUtils';
import { UUIDGenerator } from '../../utils/uuid.service';
import type { IClusterMatch } from '../../widgets';
import { ClusterMatches } from '../../widgets';

export interface IApplicationConfigDetailsProps extends IOverridableProps {
  app: Application;
}

interface ISaveState {
  isSaving: boolean;
  saveError: boolean;
}

interface IApplicationConfigState {
  bannerConfigProps: ISaveState;
  defaultTagFilterProps: ISaveState;
  hasManagedResources: boolean;
  notifications: INotification[];
  configured: boolean;
}

export class ApplicationConfigComponent extends React.Component<
  IApplicationConfigDetailsProps,
  IApplicationConfigState
> {
  public state: IApplicationConfigState = {
    bannerConfigProps: {
      isSaving: false,
      saveError: false,
    },
    defaultTagFilterProps: {
      isSaving: false,
      saveError: false,
    },
    hasManagedResources: false,
    notifications: getInitialNotifications(this.props.app),
    configured: Boolean(this.props.app.attributes && this.props.app.attributes.email),
  };

  public componentDidMount(): void {
    const { app } = this.props;

    if (app.notFound || app.hasError) {
      AngularServices.$state.go('home.infrastructure', null, { location: 'replace' });
      return;
    }

    app.attributes.instancePort = app.attributes.instancePort || SETTINGS.defaultInstancePort || null;

    if (SETTINGS.feature.managedResources) {
      app
        .getDataSource('managedResources')
        .ready()
        .then(({ hasManagedResources }: { hasManagedResources: boolean }) => this.setState({ hasManagedResources }));
    }
  }

  private updateBannerConfigs = (bannerConfigs: ICustomBannerConfig[]) => {
    const applicationAttributes = cloneDeep(this.props.app.attributes);
    applicationAttributes.customBanners = bannerConfigs;
    this.setState({ bannerConfigProps: { isSaving: true, saveError: false } });
    ApplicationWriter.updateApplication(applicationAttributes)
      .then(() => {
        this.props.app.attributes = applicationAttributes;
        this.setState({ bannerConfigProps: { isSaving: false, saveError: false } });
      })
      .catch(() => this.setState({ bannerConfigProps: { isSaving: false, saveError: true } }));
  };

  private updateDefaultTagFilterConfigs = (tagConfigs: IDefaultTagFilterConfig[]) => {
    const applicationAttributes = cloneDeep(this.props.app.attributes);
    applicationAttributes.defaultFilteredTags = tagConfigs;
    this.setState({ defaultTagFilterProps: { isSaving: true, saveError: false } });
    ApplicationWriter.updateApplication(applicationAttributes)
      .then(() => {
        this.props.app.attributes = applicationAttributes;
        this.setState({ defaultTagFilterProps: { isSaving: false, saveError: false } });
      })
      .catch(() => this.setState({ defaultTagFilterProps: { isSaving: false, saveError: true } }));
  };

  private updateNotifications = (notifications: INotification[]) => {
    this.setState({ notifications });
  };

  private onAttributesSaved = (attributes: any) => {
    this.props.app.attributes = attributes;
    this.setState({ configured: Boolean(attributes.email) });
  };

  public render() {
    return (
      <div
        className="application-config-page"
        style={{
          boxSizing: 'border-box',
          margin: 0,
          overflowY: 'auto',
          paddingLeft: 0,
          paddingRight: 15,
          width: '100%',
        }}
        data-sticky-headers={true}
      >
        <PageNavigator
          scrollableContainer="[data-sticky-headers]"
          deepLinkParam="section"
          reactInjector={AngularServices}
        >
          {this.renderSections()}
        </PageNavigator>
      </div>
    );
  }

  private renderSections(): React.ReactNode[] {
    const { app } = this.props;
    const sections = [
      <PageSection key="location" pageKey="location" label="Application Attributes">
        <ApplicationAttributes application={app} onAttributesSaved={this.onAttributesSaved} />
      </PageSection>,
    ];

    return this.state.configured ? sections.concat(this.renderConfiguredSections()) : sections;
  }

  private renderConfiguredSections(): React.ReactElement[] {
    const { app } = this.props;
    const { bannerConfigProps, defaultTagFilterProps, hasManagedResources, notifications } = this.state;

    return [
      <PageSection
        key="managed-resources"
        pageKey="managed-resources"
        label="Managed Resources"
        visible={Boolean(SETTINGS.feature.managedResources && hasManagedResources)}
      >
        <ManagedResourceConfig application={app} />
      </PageSection>,
      <PageSection key="notifications" pageKey="notifications" label="Notifications">
        <p>You can edit notification settings for this application</p>
        <NotificationsList
          application={app}
          level="application"
          notifications={notifications}
          updateNotifications={this.updateNotifications}
        />
      </PageSection>,
      <PageSection key="features" pageKey="features" label="Features">
        <ApplicationDataSourceEditor application={app} />
      </PageSection>,
      <PageSection key="links" pageKey="links" label="Links" noWrapper={true}>
        <ApplicationLinksConfig application={app} />
      </PageSection>,
      <PageSection key="chaos" pageKey="chaos" label="Chaos Monkey" visible={SETTINGS.feature.chaosMonkey}>
        <ChaosMonkeyConfigSection application={app} />
      </PageSection>,
      <PageSection key="traffic-guards" pageKey="traffic-guards" label="Traffic Guards">
        <TrafficGuardConfigSection application={app} />
      </PageSection>,
      <PageSection key="snapshot" pageKey="snapshot" label="Serialize Application" visible={SETTINGS.feature.snapshots}>
        <ApplicationSnapshotSection application={app} />
      </PageSection>,
      <PageSection key="banner" pageKey="banner" label="Custom Banners">
        <CustomBannerConfig
          bannerConfigs={app.attributes.customBanners}
          isSaving={bannerConfigProps.isSaving}
          saveError={bannerConfigProps.saveError}
          updateBannerConfigs={this.updateBannerConfigs}
        />
      </PageSection>,
      <PageSection key="default-filters" pageKey="default-filters" label="Default Filters">
        <DefaultTagFilterConfig
          defaultTagFilterConfigs={app.attributes.defaultFilteredTags}
          isSaving={defaultTagFilterProps.isSaving}
          saveError={defaultTagFilterProps.saveError}
          updateDefaultTagFilterConfigs={this.updateDefaultTagFilterConfigs}
        />
      </PageSection>,
      <PageSection key="delete" pageKey="delete" label="Delete Application">
        <DeleteApplicationSection application={app} />
      </PageSection>,
    ];
  }
}

export const ApplicationConfig = overridableComponent(ApplicationConfigComponent, 'applicationConfigView');

function getInitialNotifications(application: Application): INotification[] {
  const notifications = get(application, 'attributes.notifications', []);
  return Array.isArray(notifications) ? notifications : [];
}

export function ApplicationAttributes({
  application,
  onAttributesSaved,
}: {
  application: Application;
  onAttributesSaved: (attributes: any) => void;
}) {
  const attributes = application.attributes || {};
  const permissions = SETTINGS.feature.fiatEnabled ? formatPermissions(attributes.permissions) : null;
  const showEditor = () => {
    ReactModal.show<IApplicationAttributesFormProps>(
      ApplicationAttributesForm,
      { application, isConfigured: Boolean(attributes.email), onAttributesSaved },
      { dialogClassName: 'modal-lg' },
    );
  };

  return (
    <>
      <dl className="dl-horizontal">
        <dt>Owner</dt>
        <dd>{attributes.email}</dd>
        {attributes.appGroup && <dt>App Group</dt>}
        {attributes.appGroup && <dd>{attributes.appGroup}</dd>}
        {attributes.aliases && <dt>Alias(es)</dt>}
        {attributes.aliases && <dd>{attributes.aliases}</dd>}
        {SETTINGS.feature.pagerDuty && attributes.pdApiKey && <dt>Pager Duty</dt>}
        {SETTINGS.feature.pagerDuty && attributes.pdApiKey && <dd>{attributes.pdApiKey}</dd>}
        {SETTINGS.feature.slack && attributes.slackChannel && attributes.slackChannel.name && <dt>Slack Channel</dt>}
        {SETTINGS.feature.slack && attributes.slackChannel && attributes.slackChannel.name && (
          <dd>
            <a
              target="_blank"
              href={`${get(SETTINGS, 'slack.baseUrl', '')}/app_redirect?channel=${encodeURIComponent(
                attributes.slackChannel.id,
              )}`}
            >
              #{attributes.slackChannel.name}
            </a>
          </dd>
        )}
        {attributes.repoType && <dt>Source Repo Type</dt>}
        {attributes.repoType && <dd>{attributes.repoType}</dd>}
        {attributes.repoProjectKey && <dt>Source Repo Project</dt>}
        {attributes.repoProjectKey && <dd>{attributes.repoProjectKey}</dd>}
        {attributes.repoSlug && <dt>Source Repo</dt>}
        {attributes.repoSlug && <dd>{attributes.repoSlug}</dd>}
        <dt>Description</dt>
        <dd>{attributes.description}</dd>
        <dt>Account(s)</dt>
        <dd>{(attributes.accounts || []).join(', ')}</dd>
        {!!(attributes.cloudProviders || []).length && <dt>Cloud Provider(s)</dt>}
        {!!(attributes.cloudProviders || []).length && <dd>{attributes.cloudProviders.join(', ')}</dd>}
        {(attributes.platformHealthOnly || attributes.platformHealthOnlyShowOverride) && <dt>Instance health</dt>}
        {(attributes.platformHealthOnly || attributes.platformHealthOnlyShowOverride) && (
          <dd>{getHealthMessage(attributes)}</dd>
        )}
        <dt>Instance Port</dt>
        <dd>{attributes.instancePort}</dd>
        {(attributes.enableRestartRunningExecutions || attributes.enableRerunActiveExecutions) && (
          <dt>Pipeline Behavior</dt>
        )}
        {attributes.enableRestartRunningExecutions && <dd>Allows restarting running pipelines</dd>}
        {attributes.enableRerunActiveExecutions && <dd>Allows re-running active executions</dd>}
        {attributes.legacyUdf && <dt>User Data Format</dt>}
        {attributes.legacyUdf && <dd>This application requires legacy user data format.</dd>}
        {permissions && <dt>Permissions</dt>}
        {permissions && <dd>{permissions}</dd>}
      </dl>
      {!attributes.email && <p>This application has not been configured.</p>}
      <button className="btn btn-link" onClick={showEditor}>
        <span className="glyphicon glyphicon-cog" />{' '}
        {attributes.email ? 'Edit Application Attributes' : 'Create Application'}
      </button>
    </>
  );
}

export interface IApplicationAttributesFormProps extends IModalComponentProps<any> {
  application: Application;
  isConfigured: boolean;
  onAttributesSaved: (attributes: any) => void;
}

export function ApplicationAttributesForm({
  application,
  closeModal,
  dismissModal,
  isConfigured,
  onAttributesSaved,
}: IApplicationAttributesFormProps) {
  const [draft, setDraft] = React.useState<any>(() =>
    attributesToDraft(application.attributes || {}, application.name),
  );
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState(false);
  const [validationError, setValidationError] = React.useState<string | null>(null);
  const [availableCloudProviders, setAvailableCloudProviders] = React.useState<string[]>([]);
  const update = (field: string, value: any) => setDraft((prev: any) => ({ ...prev, [field]: value }));
  const selectedCloudProviders = splitCsv(draft.cloudProviders);
  const updateCloudProviders = (event: React.ChangeEvent<HTMLInputElement>) => {
    update('cloudProviders', ((event.target.value || []) as string[]).join(', '));
  };
  const updatePermissions = (permissions: IPermissions) => update('permissions', permissions);

  React.useEffect(() => {
    let mounted = true;
    AccountService.listProviders().then((providers: string[]) => mounted && setAvailableCloudProviders(providers));
    return () => {
      mounted = false;
    };
  }, []);

  const saveAttributes = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const draftError = validateApplicationAttributesDraft(draft, availableCloudProviders);
    if (draftError) {
      setValidationError(draftError);
      return;
    }

    const nextAttributes = draftToAttributes(draft, application.attributes || {});
    setSaving(true);
    setSaveError(false);
    setValidationError(null);
    ApplicationWriter.updateApplication(nextAttributes)
      .then((task) => TaskReader.waitUntilTaskCompletes(task))
      .then(() => {
        onAttributesSaved(nextAttributes);
        application.refresh(true);
        setSaving(false);
        closeModal?.(nextAttributes);
      })
      .catch(() => {
        setSaveError(true);
        setSaving(false);
      });
  };

  return (
    <form role="form" className="container-fluid form-horizontal" noValidate onSubmit={saveAttributes}>
      <ModalClose dismiss={dismissModal || (() => {})} />
      <Modal.Header>
        <Modal.Title>{isConfigured ? 'Edit Application' : 'Create Application'}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {!isConfigured && <p>This application has not been configured.</p>}
        <div className="form-group row">
          <div className="col-md-3 sm-label-right">Name</div>
          <div className="col-md-7 form-control-static">{draft.name}</div>
        </div>
        <TextField label="Owner Email" value={draft.email} onChange={(value) => update('email', value)} />
        <TextField label="App Group" value={draft.appGroup} onChange={(value) => update('appGroup', value)} />
        <TextField label="Alias(es)" value={draft.aliases} onChange={(value) => update('aliases', value)} />
        {SETTINGS.feature.pagerDuty && (
          <TextField label="Pager Duty" value={draft.pdApiKey} onChange={(value) => update('pdApiKey', value)} />
        )}
        {SETTINGS.feature.slack && (
          <>
            <TextField
              label="Slack Channel Name"
              value={draft.slackChannelName}
              onChange={(value) => update('slackChannelName', value)}
            />
            <TextField
              label="Slack Channel ID"
              value={draft.slackChannelId}
              onChange={(value) => update('slackChannelId', value)}
            />
          </>
        )}
        <div className="form-group row">
          <div className="col-md-3 sm-label-right">Repo Type</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              name="repoType"
              value={draft.repoType || ''}
              onChange={(event) => update('repoType', event.target.value)}
            >
              <option value="">Select Repo Type</option>
              {(SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab']).map((repoType) => (
                <option key={repoType} value={repoType}>
                  {repoType}
                </option>
              ))}
            </select>
          </div>
        </div>
        {draft.repoType && (
          <>
            <TextField
              label="Repo Project"
              value={draft.repoProjectKey}
              placeholder="Enter your source repository project name"
              onChange={(value) => update('repoProjectKey', value)}
            />
            <TextField
              label="Repo Name"
              value={draft.repoSlug}
              placeholder="Enter your source repository name (not the url)"
              onChange={(value) => update('repoSlug', value)}
            />
          </>
        )}
        <TextAreaField
          label="Description"
          value={draft.description}
          onChange={(value) => update('description', value)}
        />
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Account(s)</label>
          <div className="col-md-7 form-control-static">
            {draft.accounts || '(none)'}
            <div className="small text-muted">Accounts are managed by Front50 and cannot be changed here.</div>
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">Cloud Provider(s)</label>
          <div className="col-md-7">
            {availableCloudProviders.length ? (
              <ReactSelectInput
                name="cloudProviders"
                multi={true}
                value={selectedCloudProviders}
                stringOptions={availableCloudProviders}
                onChange={updateCloudProviders}
              />
            ) : (
              <p className="form-control-static">{draft.cloudProviders || '(none)'}</p>
            )}
          </div>
        </div>
        <CheckboxField
          groupLabel="Instance Health"
          label="Consider only cloud provider health when executing tasks"
          helpFieldId="application.platformHealthOnly"
          checked={draft.platformHealthOnly}
          onChange={(value) => update('platformHealthOnly', value)}
        />
        <CheckboxField
          label="Show a health override option for each operation"
          helpFieldId="application.showPlatformHealthOverride"
          checked={draft.platformHealthOnlyShowOverride}
          offset={true}
          onChange={(value) => update('platformHealthOnlyShowOverride', value)}
        />
        <TextField
          label="Instance Port"
          helpFieldId="application.instance.port"
          value={draft.instancePort}
          onChange={(value) => update('instancePort', value)}
        />
        <CheckboxField
          groupLabel="Pipeline Behavior"
          label="Allow restarting running pipelines"
          helpFieldId="application.enableRestartRunningExecutions"
          checked={draft.enableRestartRunningExecutions}
          onChange={(value) => update('enableRestartRunningExecutions', value)}
        />
        <CheckboxField
          label="Allow re-running active executions"
          helpFieldId="application.enableRerunActiveExecutions"
          checked={draft.enableRerunActiveExecutions}
          offset={true}
          onChange={(value) => update('enableRerunActiveExecutions', value)}
        />
        <CheckboxField
          label="This application requires legacy user data format"
          checked={draft.legacyUdf}
          onChange={(value) => update('legacyUdf', value)}
        />
        {SETTINGS.feature.fiatEnabled && (
          <div className="form-group">
            <label className="col-md-3 sm-label-right">
              Permissions <HelpField id="application.permissions" />
            </label>
            <div className="col-md-7">
              <PermissionsConfigurer
                permissions={draft.permissions}
                requiredGroupMembership={[]}
                onPermissionsChange={updatePermissions}
              />
            </div>
          </div>
        )}
        {validationError && <div className="error-message">{validationError}</div>}
      </Modal.Body>
      <Modal.Footer>
        <div className="text-right">
          {isConfigured && (
            <button type="button" className="btn btn-default" onClick={dismissModal || (() => {})} disabled={saving}>
              Cancel
            </button>
          )}{' '}
          <button type="submit" className="btn btn-primary" disabled={saving || !draft.email}>
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
          {saveError && <div className="error-message">There was an error saving your changes. Please try again.</div>}
        </div>
      </Modal.Footer>
    </form>
  );
}

interface IApplicationDataSourceEditorState {
  explicitlyDisabled: string[];
  explicitlyEnabled: string[];
  model: { [key: string]: boolean };
  original: string;
  saveError: boolean;
  saving: boolean;
}

export class ApplicationDataSourceEditor extends React.Component<
  { application: Application },
  IApplicationDataSourceEditorState
> {
  public state: IApplicationDataSourceEditorState = this.getInitialState();

  private getVisibleDataSources() {
    return this.props.application.dataSources.filter((ds) => ds.visible && ds.optional && !ds.hidden);
  }

  private getInitialState(): IApplicationDataSourceEditorState {
    const { application } = this.props;
    if (!application.attributes) {
      application.attributes = {};
    }
    if (!application.attributes.dataSources) {
      application.attributes.dataSources = { enabled: [], disabled: [] };
    }
    const model: { [key: string]: boolean } = {};
    this.getVisibleDataSources().forEach((ds) => {
      model[ds.key] = !ds.disabled;
    });

    return {
      explicitlyDisabled: application.attributes.dataSources.disabled,
      explicitlyEnabled: application.attributes.dataSources.enabled,
      model,
      original: JSON.stringify(model),
      saveError: false,
      saving: false,
    };
  }

  private isDirty(): boolean {
    return JSON.stringify(this.state.model) !== this.state.original;
  }

  private dataSourceChanged = (key: string) => {
    this.setState((state) => {
      const model = { ...state.model, [key]: !state.model[key] };
      const explicitlyEnabled = model[key]
        ? addUnique(state.explicitlyEnabled, key)
        : removeValue(state.explicitlyEnabled, key);
      const explicitlyDisabled = model[key]
        ? removeValue(state.explicitlyDisabled, key)
        : addUnique(state.explicitlyDisabled, key);

      return { explicitlyDisabled, explicitlyEnabled, model };
    });
  };

  private revert = () => {
    this.setState(this.getInitialState());
  };

  private save = () => {
    const { application } = this.props;
    const newDataSources = { enabled: this.state.explicitlyEnabled, disabled: this.state.explicitlyDisabled };
    this.setState({ saving: true, saveError: false });

    ApplicationWriter.updateApplication({
      name: application.name,
      accounts: application.attributes.accounts,
      dataSources: newDataSources,
    })
      .then((task) => TaskReader.waitUntilTaskCompletes(task))
      .then(() => {
        application.attributes.dataSources = newDataSources;
        ApplicationReader.setDisabledDataSources(application);
        application.refresh(true);
        this.setState({ ...this.getInitialState(), saving: false });
      })
      .catch(() => this.setState({ saving: false, saveError: true }));
  };

  public render() {
    const dataSources = this.getVisibleDataSources();

    return (
      <>
        <p>If you don't need or want certain features for your application, you can disable them here.</p>
        <p>Disabling a feature only changes the display in Spinnaker - it won't delete any actual data.</p>
        {dataSources.map((dataSource) => (
          <div key={dataSource.key} className="checkbox application-feature-option">
            <label className="application-feature-label">
              <input
                type="checkbox"
                checked={this.state.model[dataSource.key]}
                onChange={() => this.dataSourceChanged(dataSource.key)}
              />{' '}
              <strong className="application-feature-name">{dataSource.label}</strong>
            </label>
            {dataSource.description && (
              <div className="application-feature-description">
                <Markdown message={dataSource.description} />
              </div>
            )}
          </div>
        ))}
        <ConfigSectionFooter
          isDirty={this.isDirty()}
          isValid={true}
          isSaving={this.state.saving}
          saveError={this.state.saveError}
          onRevertClicked={this.revert}
          onSaveClicked={this.save}
        />
      </>
    );
  }
}

type InstanceLink = { title: string; path: string };
type LinkSection = { title: string; cloudProviders?: string[]; links: InstanceLink[] };

function ApplicationLinksConfig({ application }: { application: Application }) {
  const applicationCloudProviders = application.attributes.cloudProviders || [];
  const initialSections = React.useMemo(() => getInitialLinkSections(application), [application]);
  const [sections, setSections] = React.useState<LinkSection[]>(initialSections);
  const [original, setOriginal] = React.useState(JSON.stringify(initialSections));
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState(false);
  const isDirty = JSON.stringify(sections) !== original;

  const updateSection = (index: number, updates: Partial<LinkSection>) => {
    setSections((prev) => prev.map((section, i) => (i === index ? { ...section, ...updates } : section)));
  };
  const updateLink = (sectionIndex: number, linkIndex: number, updates: Partial<InstanceLink>) => {
    setSections((prev) =>
      prev.map((section, i) => {
        if (i !== sectionIndex) {
          return section;
        }
        return {
          ...section,
          links: section.links.map((link, j) => (j === linkIndex ? { ...link, ...updates } : link)),
        };
      }),
    );
  };
  const toggleSectionProvider = (sectionIndex: number, provider: string) => {
    setSections((prev) =>
      prev.map((section, i) => {
        if (i !== sectionIndex) {
          return section;
        }
        const selected = section.cloudProviders || [];
        return {
          ...section,
          cloudProviders: selected.includes(provider)
            ? selected.filter((existing) => existing !== provider)
            : selected.concat(provider),
        };
      }),
    );
  };
  const save = () => {
    saveApplicationConfig(application, 'instanceLinks', sections, setSaving, setSaveError).then((saved) => {
      if (saved) {
        setOriginal(JSON.stringify(sections));
      }
    });
  };
  const editJson = () => {
    ReactModal.show<IEditLinksJsonModalProps, LinkSection[]>(
      EditLinksJsonModal,
      { sections },
      { dialogClassName: 'modal-lg modal-fullscreen' },
    )
      .then((newSections) => setSections(newSections))
      .catch(() => {});
  };

  return (
    <div className="application-links-config form-horizontal">
      <div className="row section-body">
        <div className="col-md-9">
          <p>
            Links appear in the instance details panel and provide a shortcut to common features, such as logs, health,
            etc.
          </p>
          <p>
            Links paths are templates - you can customize them using a number of field attributes available on the
            instance.
          </p>
        </div>
        <div className="col-md-3 text-right">
          <button className="btn btn-sm btn-default" onClick={editJson}>
            <span className="small glyphicon glyphicon-cog" /> Edit as JSON
          </button>
        </div>
      </div>
      {sections.map((section, sectionIndex) => (
        <div className="link-section section-body" key={sectionIndex}>
          <TextField
            label="Section Heading"
            value={section.title || ''}
            onChange={(value) => updateSection(sectionIndex, { title: value })}
          />
          <div className="form-group">
            <label className="col-md-3 sm-label-right">Cloud Provider(s)</label>
            <div className="col-md-7">
              {applicationCloudProviders.map((provider: string) => (
                <label className="checkbox-inline" key={provider}>
                  <input
                    type="checkbox"
                    checked={(section.cloudProviders || []).includes(provider)}
                    onChange={() => toggleSectionProvider(sectionIndex, provider)}
                  />{' '}
                  {provider}
                </label>
              ))}
              {!applicationCloudProviders.length && (
                <p className="form-control-static">No cloud providers configured.</p>
              )}
            </div>
          </div>
          {section.links.map((link, linkIndex) => (
            <div className="application-link" key={linkIndex}>
              <TextField
                label="Label"
                value={link.title || ''}
                placeholder="Label, e.g. Health"
                onChange={(value) => updateLink(sectionIndex, linkIndex, { title: value })}
              />
              <TextField
                label="Path"
                value={link.path || ''}
                placeholder="Path, e.g. /health"
                onChange={(value) => updateLink(sectionIndex, linkIndex, { path: value })}
              />
              <div className="form-group row">
                <div className="col-md-7 col-md-offset-3">
                  <button
                    className="btn btn-link"
                    onClick={() =>
                      setSections((prev) =>
                        prev.map((existingSection, i) =>
                          i === sectionIndex
                            ? {
                                ...existingSection,
                                links: existingSection.links.filter((_link, j) => j !== linkIndex),
                              }
                            : existingSection,
                        ),
                      )
                    }
                  >
                    Remove Link
                  </button>
                </div>
              </div>
            </div>
          ))}
          <button
            className="btn btn-block add-new small"
            onClick={() =>
              setSections((prev) =>
                prev.map((existingSection, i) =>
                  i === sectionIndex
                    ? { ...existingSection, links: existingSection.links.concat({ title: '', path: '' }) }
                    : existingSection,
                ),
              )
            }
          >
            <span className="glyphicon glyphicon-plus-sign" /> Add Link
          </button>
          <button
            className="btn btn-link"
            onClick={() => setSections((prev) => prev.filter((_section, i) => i !== sectionIndex))}
          >
            Remove Section
          </button>
        </div>
      ))}
      <button
        className="btn btn-block add-new"
        onClick={() => setSections((prev) => prev.concat({ title: '', links: [{ title: '', path: '' }] }))}
      >
        <span className="glyphicon glyphicon-plus-sign" /> Add Section
      </button>
      {SETTINGS.defaultInstanceLinks && (
        <button className="btn btn-link" onClick={() => setSections(cloneDeep(SETTINGS.defaultInstanceLinks))}>
          Revert to default links
        </button>
      )}
      <ConfigSectionFooter
        isDirty={isDirty}
        isValid={true}
        isSaving={saving}
        saveError={saveError}
        onRevertClicked={() => setSections(JSON.parse(original))}
        onSaveClicked={save}
      />
    </div>
  );
}

interface IEditLinksJsonModalProps extends IModalComponentProps<LinkSection[]> {
  sections: LinkSection[];
}

function EditLinksJsonModal({ closeModal, dismissModal, sections }: IEditLinksJsonModalProps) {
  const [jsonValue, setJsonValue] = React.useState(JSON.stringify(sections, null, 2));
  const [jsonError, setJsonError] = React.useState<string | null>(null);
  const applyJson = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const parsed = JSON.parse(jsonValue);
      if (!Array.isArray(parsed)) {
        setJsonError('Links JSON must be an array of link sections.');
        return;
      }
      closeModal?.(parsed);
    } catch (e) {
      setJsonError((e as Error).message || 'Links JSON must be valid JSON.');
    }
  };

  return (
    <form role="form" className="container-fluid form-horizontal" noValidate onSubmit={applyJson}>
      <ModalClose dismiss={dismissModal || (() => {})} />
      <Modal.Header>
        <Modal.Title>Edit Links JSON</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <TextAreaField label="Links JSON" value={jsonValue} onChange={setJsonValue} />
        {jsonError && <div className="error-message">{jsonError}</div>}
      </Modal.Body>
      <Modal.Footer>
        <button type="button" className="btn btn-default" onClick={dismissModal || (() => {})}>
          Cancel
        </button>{' '}
        <button type="submit" className="btn btn-primary">
          Update
        </button>
      </Modal.Footer>
    </form>
  );
}

interface IChaosConfig {
  enabled: boolean;
  meanTimeBetweenKillsInWorkDays: number;
  minTimeBetweenKillsInWorkDays: number;
  grouping: string;
  regionsAreIndependent: boolean;
  exceptions: any[];
}

type ChaosMatchesStatus = 'loading' | 'ready' | 'unavailable';

function ChaosMonkeyConfigSection({ application }: { application: Application }) {
  const initial = React.useMemo(() => getInitialChaosConfig(application), [application]);
  const [config, setConfig] = React.useState<IChaosConfig>(initial);
  const [original, setOriginal] = React.useState(JSON.stringify(initial));
  const [exceptionKeys, setExceptionKeys] = React.useState<string[]>(() =>
    initial.exceptions.map(() => UUIDGenerator.generateUuid()),
  );
  const [accounts, setAccounts] = React.useState<IAccountDetails[]>([]);
  const [regionsByAccount, setRegionsByAccount] = React.useState<{ [account: string]: string[] }>({});
  const [matchesStatus, setMatchesStatus] = React.useState<ChaosMatchesStatus>('loading');
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState(false);
  const update = (updates: Partial<IChaosConfig>) => setConfig((prev) => ({ ...prev, ...updates }));
  const updateException = (index: number, updates: any) =>
    setConfig((prev) => ({
      ...prev,
      exceptions: prev.exceptions.map((ex, i) => (i === index ? { ...ex, ...updates } : ex)),
    }));
  React.useEffect(() => {
    let mounted = true;
    setMatchesStatus('loading');
    AccountService.getCredentialsKeyedByAccount()
      .then((aggregated: IAggregatedAccounts) => {
        if (!mounted) {
          return Promise.resolve();
        }
        const regionAccounts = Object.keys(aggregated)
          .map((name) => aggregated[name])
          .filter((details: any) => details.regions);
        const nextRegionsByAccount: { [account: string]: string[] } = {};
        regionAccounts.forEach((details: any) => {
          nextRegionsByAccount[details.name] = ['*'].concat(details.regions.map((region: any) => region.name));
        });
        setAccounts(regionAccounts);
        setRegionsByAccount(nextRegionsByAccount);
        return application.getDataSource('serverGroups').ready();
      })
      .then(() => mounted && setMatchesStatus('ready'))
      .catch(() => mounted && setMatchesStatus('unavailable'));
    return () => {
      mounted = false;
    };
  }, [application]);
  const clusterMatches = React.useMemo(
    () =>
      config.exceptions.map((exception) =>
        matchesStatus === 'ready' ? getChaosExceptionClusterMatches(application, exception) : [],
      ),
    [application, config.exceptions, matchesStatus],
  );
  const isValid = isChaosConfigValid(config);
  const save = () => {
    if (!isChaosConfigValid(config)) {
      return;
    }
    saveApplicationConfig(application, 'chaosMonkey', normalizeChaosConfig(config), setSaving, setSaveError).then(
      (saved) => {
        if (saved) {
          const normalized = normalizeChaosConfig(config);
          setConfig(normalized);
          setOriginal(JSON.stringify(normalized));
        }
      },
    );
  };

  return (
    <div className="form-inline">
      <CheckboxField label="Enabled" checked={config.enabled} onChange={(value) => update({ enabled: value })} />
      <h5 className="first-header">Termination frequency</h5>
      <TextField
        label="Mean time between terms"
        value={String(config.meanTimeBetweenKillsInWorkDays)}
        onChange={(value) => update({ meanTimeBetweenKillsInWorkDays: value as any })}
      />
      <TextField
        label="Min time between terms"
        value={String(config.minTimeBetweenKillsInWorkDays)}
        onChange={(value) => update({ minTimeBetweenKillsInWorkDays: value as any })}
      />
      <SelectField
        label="Grouping"
        value={config.grouping}
        options={['app', 'stack', 'cluster']}
        onChange={(value) => update({ grouping: value })}
      />
      <CheckboxField
        label="Regions are independent"
        checked={config.regionsAreIndependent}
        onChange={(value) => update({ regionsAreIndependent: value })}
      />
      <h5>Exceptions</h5>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Account</th>
            <th>Region</th>
            <th>Stack</th>
            <th>Detail</th>
            <th>Matched Clusters</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {config.exceptions.map((exception, index) => {
            const accountNames = accounts.map((account) => account.name);
            const accountOptions = addSelectedOption(accountNames, exception.account);
            const regionOptions = addSelectedOption(regionsByAccount[exception.account] || ['*'], exception.region);
            return (
              <tr key={exceptionKeys[index]}>
                <td>
                  <select
                    className="form-control input-sm"
                    name="chaosExceptionAccount"
                    value={exception.account || ''}
                    onChange={(event) => updateException(index, { account: event.target.value, region: '*' })}
                  >
                    <option value="">(select account)</option>
                    {accountOptions.map((account) => (
                      <option key={account} value={account}>
                        {account}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  {exception.account ? (
                    <select
                      className="form-control input-sm"
                      name="chaosExceptionRegion"
                      value={exception.region || '*'}
                      onChange={(event) => updateException(index, { region: event.target.value })}
                    >
                      {regionOptions.map((region) => (
                        <option key={region} value={region}>
                          {region}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <span>(select account)</span>
                  )}
                </td>
                <td>
                  <input
                    className="form-control input-sm"
                    name="chaosExceptionStack"
                    value={exception.stack || ''}
                    onChange={(event) => updateException(index, { stack: event.target.value })}
                  />
                </td>
                <td>
                  <input
                    className="form-control input-sm"
                    name="chaosExceptionDetail"
                    value={exception.detail || ''}
                    onChange={(event) => updateException(index, { detail: event.target.value })}
                  />
                </td>
                <td>
                  {matchesStatus === 'unavailable' ? (
                    <span className="chaos-matches-unavailable">(matches unavailable)</span>
                  ) : matchesStatus === 'ready' ? (
                    <ClusterMatches matches={clusterMatches[index]} />
                  ) : (
                    <span>(loading matches)</span>
                  )}
                </td>
                <td>
                  <button
                    className="btn btn-link"
                    onClick={() => {
                      setConfig((prev) => ({
                        ...prev,
                        exceptions: prev.exceptions.filter((_ex, i) => i !== index),
                      }));
                      setExceptionKeys((prev) => prev.filter((_key, i) => i !== index));
                    }}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <button
        className="btn btn-block btn-add-trigger add-new"
        onClick={() => {
          setConfig((prev) => ({
            ...prev,
            exceptions: prev.exceptions.concat({ account: '', region: '*', stack: '', detail: '' }),
          }));
          setExceptionKeys((prev) => prev.concat(UUIDGenerator.generateUuid()));
        }}
      >
        <span className="glyphicon glyphicon-plus-sign" /> Add Exception
      </button>
      <ConfigSectionFooter
        isDirty={JSON.stringify(config) !== original}
        isValid={isValid}
        isSaving={saving}
        saveError={saveError}
        onRevertClicked={() => {
          const reverted = JSON.parse(original);
          setConfig(reverted);
          setExceptionKeys(reverted.exceptions.map(() => UUIDGenerator.generateUuid()));
        }}
        onSaveClicked={save}
      />
    </div>
  );
}

function TrafficGuardConfigSection({ application }: { application: Application }) {
  const initial = React.useMemo(() => cloneDeep(application.attributes.trafficGuards || []), [application]);
  const [guards, setGuards] = React.useState<any[]>(initial);
  const [original, setOriginal] = React.useState(JSON.stringify(initial));
  const [accounts, setAccounts] = React.useState<IAccountDetails[]>([]);
  const [locationsByAccount, setLocationsByAccount] = React.useState<{ [account: string]: string[] }>({});
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState(false);
  React.useEffect(() => {
    let mounted = true;
    AccountService.getCredentialsKeyedByAccount().then((aggregated: IAggregatedAccounts) => {
      if (!mounted) {
        return;
      }
      const allAccounts = Object.keys(aggregated).map((name) => aggregated[name]);
      const regionAccounts = allAccounts.filter((details: any) => details.regions && !details.namespaces);
      const namespaceAccounts = allAccounts.filter((details: any) => details.namespaces && !details.regions);
      const nextLocationsByAccount: { [account: string]: string[] } = {};
      regionAccounts.forEach((details: any) => {
        nextLocationsByAccount[details.name] = ['*'].concat(details.regions.map((region: any) => region.name));
      });
      namespaceAccounts.forEach((details: any) => {
        nextLocationsByAccount[details.name] = ['*'].concat(details.namespaces);
      });
      setAccounts(regionAccounts.concat(namespaceAccounts));
      setLocationsByAccount(nextLocationsByAccount);
    });
    return () => {
      mounted = false;
    };
  }, []);
  const updateGuard = (index: number, updates: any) =>
    setGuards((prev) => prev.map((guard, i) => (i === index ? { ...guard, ...updates } : guard)));
  const save = () =>
    saveApplicationConfig(application, 'trafficGuards', guards, setSaving, setSaveError).then(
      (saved) => saved && setOriginal(JSON.stringify(guards)),
    );

  return (
    <div>
      <p>Traffic Guards allow you to specify critical clusters that should always have active instances.</p>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Enabled</th>
            <th>Account</th>
            <th>Region</th>
            <th>Stack</th>
            <th>Detail</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {guards.map((guard, index) => (
            <tr key={index}>
              <td>
                <input
                  type="checkbox"
                  checked={guard.enabled}
                  onChange={(e) => updateGuard(index, { enabled: e.target.checked })}
                />
              </td>
              <td>
                <AccountSelect
                  value={guard.account || ''}
                  accounts={accounts}
                  onChange={(value) => updateGuard(index, { account: value, location: '*' })}
                />
              </td>
              <td>
                {guard.account ? (
                  <select
                    className="form-control input-sm"
                    value={guard.location || '*'}
                    onChange={(e) => updateGuard(index, { location: e.target.value })}
                  >
                    {(locationsByAccount[guard.account] || ['*']).map((location) => (
                      <option key={location} value={location}>
                        {location}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span>(select account)</span>
                )}
              </td>
              <td>
                <input
                  className="form-control input-sm"
                  value={guard.stack || ''}
                  onChange={(e) => updateGuard(index, { stack: e.target.value })}
                />
              </td>
              <td>
                <input
                  className="form-control input-sm"
                  value={guard.detail || ''}
                  onChange={(e) => updateGuard(index, { detail: e.target.value })}
                />
              </td>
              <td>
                <button
                  className="btn btn-link"
                  onClick={() => setGuards((prev) => prev.filter((_guard, i) => i !== index))}
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button
        className="btn btn-block btn-add-trigger add-new"
        onClick={() =>
          setGuards((prev) => prev.concat({ enabled: true, account: '', location: '*', stack: '', detail: '' }))
        }
      >
        <span className="glyphicon glyphicon-plus-sign" /> Add Traffic Guard
      </button>
      <ConfigSectionFooter
        isDirty={JSON.stringify(guards) !== original}
        isValid={true}
        isSaving={saving}
        saveError={saveError}
        onRevertClicked={() => setGuards(JSON.parse(original))}
        onSaveClicked={save}
      />
    </div>
  );
}

function ApplicationSnapshotSection({ application }: { application: Application }) {
  const [historyVisible, setHistoryVisible] = React.useState(false);
  const [account, setAccount] = React.useState((application.attributes.accounts || [])[0] || '');
  const [snapshots, setSnapshots] = React.useState<any[]>([]);
  const [selectedSnapshot, setSelectedSnapshot] = React.useState<any>(null);
  const [compareSnapshot, setCompareSnapshot] = React.useState<any>(null);
  const takeSnapshot = () =>
    ConfirmationModalService.confirm({
      header: `Are you sure you want to take a snapshot of: ${application.name}?`,
      buttonText: 'Take snapshot',
      taskMonitorConfig: { application, title: `Taking snapshot of ${application.name}` },
      submitMethod: () => SnapshotWriter.takeSnapshot(application),
    });
  const loadHistory = () =>
    SnapshotReader.getSnapshotHistory(application.name, account).then((history) => {
      setSnapshots(history);
      setSelectedSnapshot(history[0] || null);
      setCompareSnapshot(history[1] || null);
    });
  const restoreSnapshot = () =>
    selectedSnapshot &&
    ConfirmationModalService.confirm({
      header: `Restore snapshot for ${application.name}?`,
      buttonText: 'Restore snapshot',
      taskMonitorConfig: { application, title: `Restoring snapshot of ${application.name}` },
      submitMethod: () =>
        SnapshotWriter.restoreSnapshot(
          application,
          account,
          selectedSnapshot.timestamp || selectedSnapshot.createdAt || selectedSnapshot.id,
        ),
    });

  return (
    <div>
      <p>
        Taking an application snapshot will save the current state of the infrastructure to a storage bucket using
        Terraform's config language.
      </p>
      <button className="btn btn-link" onClick={takeSnapshot}>
        <span className="glyphicon glyphicon-cloud-download" /> Take application snapshot
      </button>
      <button className="btn btn-link" onClick={() => setHistoryVisible(true)}>
        <span className="glyphicon glyphicon-cloud" /> View Snapshot History
      </button>
      {historyVisible && (
        <div className="well">
          <h4>Snapshot History</h4>
          <TextField label="Account" value={account} onChange={setAccount} />
          <button className="btn btn-default" onClick={loadHistory}>
            Load snapshots
          </button>
          <SnapshotSelect
            label="Snapshot"
            snapshots={snapshots}
            selectedSnapshot={selectedSnapshot}
            onChange={setSelectedSnapshot}
          />
          <SnapshotSelect
            label="Compare To"
            snapshots={snapshots}
            selectedSnapshot={compareSnapshot}
            onChange={setCompareSnapshot}
          />
          {selectedSnapshot && compareSnapshot && (
            <div className="horizontal middle snapshot-diff">
              <DiffView diff={buildSnapshotDiff(compareSnapshot, selectedSnapshot)} />
            </div>
          )}
          <button className="btn btn-primary" disabled={!selectedSnapshot} onClick={restoreSnapshot}>
            Restore selected snapshot
          </button>{' '}
          <button className="btn btn-default" onClick={() => setHistoryVisible(false)}>
            Close
          </button>
        </div>
      )}
    </div>
  );
}

function SnapshotSelect({
  label,
  onChange,
  selectedSnapshot,
  snapshots,
}: {
  label: string;
  onChange: (snapshot: any) => void;
  selectedSnapshot: any;
  snapshots: any[];
}) {
  return (
    <div className="form-group row">
      <div className="col-md-3 sm-label-right">{label}</div>
      <div className="col-md-7">
        <select
          className="form-control input-sm"
          value={selectedSnapshot ? snapshots.indexOf(selectedSnapshot) : ''}
          onChange={(e) => onChange(snapshots[Number(e.target.value)] || null)}
        >
          <option value="">Select snapshot</option>
          {snapshots.map((snapshot, index) => (
            <option key={index} value={index}>
              {snapshot.timestamp || snapshot.createdAt || snapshot.id || index}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

function buildSnapshotDiff(left: any, right: any) {
  return JsonUtils.diff(JSON.stringify(left, null, 2), JSON.stringify(right, null, 2), false);
}

function TextField({
  helpFieldId,
  label,
  onChange,
  placeholder,
  value,
}: {
  helpFieldId?: string;
  label: string;
  onChange: (value: string) => void;
  placeholder?: string;
  value: any;
}) {
  return (
    <div className="form-group row">
      <div className="col-md-3 sm-label-right">
        {label} {helpFieldId && <HelpField id={helpFieldId} />}
      </div>
      <div className="col-md-7">
        <input
          className="form-control input-sm"
          value={value || ''}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      </div>
    </div>
  );
}

function TextAreaField({
  label,
  onChange,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <div className="form-group row">
      <div className="col-md-3 sm-label-right">{label}</div>
      <div className="col-md-7">
        <textarea className="form-control input-sm" value={value || ''} onChange={(e) => onChange(e.target.value)} />
      </div>
    </div>
  );
}

export function CheckboxField({
  checked,
  groupLabel,
  helpFieldId,
  label,
  offset,
  onChange,
}: {
  checked: boolean;
  groupLabel?: string;
  helpFieldId?: string;
  label: string;
  offset?: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="form-group row">
      {groupLabel && <div className="col-md-3 sm-label-right application-attributes-group-label">{groupLabel}</div>}
      {!groupLabel && !offset && <div className="col-md-3 sm-label-right" />}
      <div className={`col-md-7 sm-control-field checkbox ${offset ? 'col-md-offset-3' : ''}`}>
        <label>
          <input type="checkbox" checked={Boolean(checked)} onChange={(e) => onChange(e.target.checked)} /> {label}{' '}
          {helpFieldId && <HelpField id={helpFieldId} />}
        </label>
      </div>
    </div>
  );
}

function SelectField({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: string[];
  value: string;
}) {
  return (
    <div className="form-group row">
      <div className="col-md-3 sm-label-right">{label}</div>
      <div className="col-md-7">
        <select className="form-control input-sm" value={value || ''} onChange={(e) => onChange(e.target.value)}>
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

function AccountSelect({
  accounts,
  onChange,
  value,
}: {
  accounts: IAccountDetails[];
  onChange: (value: string) => void;
  value: string;
}) {
  return accounts.length ? (
    <select className="form-control input-sm" value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">(select account)</option>
      {accounts.map((account) => (
        <option key={account.name} value={account.name}>
          {account.name}
        </option>
      ))}
    </select>
  ) : (
    <input
      className="form-control input-sm"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder="account"
    />
  );
}

function saveApplicationConfig(
  application: Application,
  field: string,
  value: any,
  setSaving: (saving: boolean) => void,
  setSaveError: (error: boolean) => void,
): Promise<boolean> {
  const nextAttributes = cloneDeep(application.attributes || {});
  nextAttributes[field] = value;
  setSaving(true);
  setSaveError(false);
  return Promise.resolve(ApplicationWriter.updateApplication(nextAttributes))
    .then((task) => TaskReader.waitUntilTaskCompletes(task))
    .then(() => {
      application.attributes = nextAttributes;
      application.refresh(true);
      setSaving(false);
      return true;
    })
    .catch(() => {
      setSaving(false);
      setSaveError(true);
      return false;
    });
}

function getInitialLinkSections(application: Application): LinkSection[] {
  const cloudProviders = application.attributes.cloudProviders || [];
  return cloneDeep(application.attributes.instanceLinks || SETTINGS.defaultInstanceLinks || [])
    .filter(
      (section: LinkSection) =>
        !section.cloudProviders ||
        !section.cloudProviders.length ||
        section.cloudProviders.some((provider) => cloudProviders.includes(provider)),
    )
    .map((section: LinkSection) => ({
      ...section,
      cloudProviders: section.cloudProviders || [],
      links: section.links || [],
    }));
}

function getInitialChaosConfig(application: Application): IChaosConfig {
  const config = {
    enabled: false,
    meanTimeBetweenKillsInWorkDays: 2,
    minTimeBetweenKillsInWorkDays: 1,
    grouping: 'cluster',
    regionsAreIndependent: true,
    exceptions: [],
    ...(application.attributes.chaosMonkey || {}),
  };
  return { ...config, exceptions: (config.exceptions || []).map(normalizeChaosException) };
}

function isChaosConfigValid(config: IChaosConfig): boolean {
  return [config.meanTimeBetweenKillsInWorkDays, config.minTimeBetweenKillsInWorkDays].every((value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 1;
  });
}

function normalizeChaosConfig(config: IChaosConfig): IChaosConfig {
  return {
    ...config,
    meanTimeBetweenKillsInWorkDays: Number(config.meanTimeBetweenKillsInWorkDays),
    minTimeBetweenKillsInWorkDays: Number(config.minTimeBetweenKillsInWorkDays),
    exceptions: config.exceptions.map(normalizeChaosException),
  };
}

function getChaosExceptionClusterMatches(application: Application, exception: any): IClusterMatch[] {
  const normalizedException = normalizeChaosException(exception);
  const { region: location, ...clusterMatchRule } = normalizedException;
  const rule = { ...clusterMatchRule, location };
  return application.clusters
    .filter((cluster) =>
      cluster.serverGroups.some(
        (serverGroup) =>
          ClusterMatcher.getMatchingRule(cluster.account, serverGroup.region, cluster.name, [rule]) !== null,
      ),
    )
    .map((cluster) => ({
      account: normalizedException.account,
      name: cluster.name,
      regions:
        normalizedException.region === '*'
          ? Array.from(new Set(cluster.serverGroups.map((serverGroup) => serverGroup.region))).sort()
          : [normalizedException.region],
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

function normalizeChaosException(exception: any): any {
  return { ...exception, region: exception.region || '*' };
}

function addSelectedOption(options: string[], selected: string): string[] {
  return selected && !options.includes(selected) ? options.concat(selected) : options;
}

function attributesToDraft(attributes: any, applicationName: string): any {
  return {
    name: attributes.name || applicationName,
    email: attributes.email || '',
    appGroup: attributes.appGroup || '',
    aliases: attributes.aliases || '',
    pdApiKey: attributes.pdApiKey || '',
    slackChannelName: get(attributes, 'slackChannel.name', ''),
    slackChannelId: get(attributes, 'slackChannel.id', ''),
    repoType: attributes.repoType || '',
    repoProjectKey: attributes.repoProjectKey || '',
    repoSlug: attributes.repoSlug || '',
    description: attributes.description || '',
    accounts: (attributes.accounts || []).join(', '),
    cloudProviders: (attributes.cloudProviders || []).join(', '),
    platformHealthOnly: Boolean(attributes.platformHealthOnly),
    platformHealthOnlyShowOverride: Boolean(attributes.platformHealthOnlyShowOverride),
    instancePort: attributes.instancePort || '',
    enableRestartRunningExecutions: Boolean(attributes.enableRestartRunningExecutions),
    enableRerunActiveExecutions: Boolean(attributes.enableRerunActiveExecutions),
    legacyUdf: Boolean(attributes.legacyUdf),
    permissions: normalizePermissions(attributes.permissions),
  };
}

function draftToAttributes(draft: any, existing: any): any {
  const next = {
    ...existing,
    name: draft.name,
    email: draft.email,
    appGroup: draft.appGroup,
    aliases: normalizeAliases(draft.aliases),
    pdApiKey: draft.pdApiKey,
    repoType: draft.repoType,
    repoProjectKey: draft.repoProjectKey,
    repoSlug: draft.repoSlug,
    description: draft.description,
    accounts: existing.accounts,
    cloudProviders: splitCsv(draft.cloudProviders),
    platformHealthOnly: draft.platformHealthOnly,
    platformHealthOnlyShowOverride: draft.platformHealthOnlyShowOverride,
    instancePort: draft.instancePort === '' ? null : Number(draft.instancePort),
    enableRestartRunningExecutions: draft.enableRestartRunningExecutions,
    enableRerunActiveExecutions: draft.enableRerunActiveExecutions,
    legacyUdf: draft.legacyUdf,
    permissions: draft.permissions,
  };

  if (draft.slackChannelName || draft.slackChannelId) {
    next.slackChannel = { name: draft.slackChannelName, id: draft.slackChannelId };
  } else {
    delete next.slackChannel;
  }

  initializeProviderSettings(next);
  return next;
}

function normalizePermissions(permissions: Partial<IPermissions> = {}): IPermissions {
  return {
    READ: permissions.READ || [],
    EXECUTE: permissions.EXECUTE || [],
    WRITE: permissions.WRITE || [],
  };
}

function initializeProviderSettings(attributes: any): void {
  const providers =
    attributes.cloudProviders && attributes.cloudProviders.length
      ? attributes.cloudProviders
      : Object.keys(SETTINGS.providers || {});
  providers.forEach((provider: string) => {
    const providerDefaults = get(SETTINGS.providers, provider, {});
    Object.keys(providerDefaults).forEach((field) => {
      const fieldPath = `${provider}.${field}`;
      const applicationFieldPath = `providerSettings.${fieldPath}`;
      if (has(SETTINGS.providers, fieldPath) && !has(attributes, applicationFieldPath)) {
        set(attributes, applicationFieldPath, get(SETTINGS.providers, fieldPath));
      }
    });
  });
}

function validateApplicationAttributesDraft(draft: any, availableCloudProviders: string[]): string | null {
  if (!draft.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.email)) {
    return 'Please enter a valid email address.';
  }
  if (SETTINGS.feature.fiatEnabled) {
    const permissions = normalizePermissions(draft.permissions);
    if ([...permissions.READ, ...permissions.WRITE, ...permissions.EXECUTE].some((group) => !group)) {
      return 'Permission groups cannot be empty.';
    }
    if (permissions.READ.length && !permissions.WRITE.length) {
      return 'Write permission is required when read permission is configured.';
    }
  }
  if (draft.repoSlug && draft.repoSlug.includes('://')) {
    return 'Enter your source repository name (not the URL).';
  }
  if (draft.instancePort !== '') {
    const port = Number(draft.instancePort);
    if (!Number.isFinite(port) || !Number.isInteger(port) || port < 0 || port > 65535) {
      return 'Instance port must be a whole number between 0 and 65535.';
    }
  }
  const unknownProviders = splitCsv(draft.cloudProviders).filter(
    (provider) => !availableCloudProviders.includes(provider),
  );
  if (availableCloudProviders.length && unknownProviders.length) {
    return `Unknown cloud provider(s): ${unknownProviders.join(', ')}.`;
  }

  return null;
}

function splitCsv(value: string): string[] {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalizeAliases(value: string): string {
  return splitCsv(value).join(',');
}

function getHealthMessage(attributes: any): string {
  const healthParts = [];
  if (attributes.platformHealthOnly) {
    healthParts.push('considers only cloud provider health when executing tasks');
  }
  if (attributes.platformHealthOnlyShowOverride) {
    healthParts.push('shows a health override option for each operation');
  }
  return `This application ${healthParts.join(' and ')}.`;
}

function addUnique(values: string[], value: string): string[] {
  return values.includes(value) ? values : values.concat(value);
}

function removeValue(values: string[], value: string): string[] {
  return values.filter((existing) => existing !== value);
}

function formatPermissions(permissions: any): string | null {
  if (!permissions) {
    return null;
  }

  const permissionsMap = new Map<string, string>();
  (permissions.READ || []).forEach((role: string) => permissionsMap.set(role, 'read'));
  (permissions.EXECUTE || []).forEach((role: string) => {
    permissionsMap.set(role, permissionsMap.has(role) ? `${permissionsMap.get(role)}, execute` : 'execute');
  });
  (permissions.WRITE || []).forEach((role: string) => {
    permissionsMap.set(role, permissionsMap.has(role) ? `${permissionsMap.get(role)}, write` : 'write');
  });

  return Array.from(permissionsMap)
    .map(([role, accessTypes]) => `${role} (${accessTypes})`)
    .join(', ');
}
