import { render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';
import React from 'react';

import { AccountService, EntityTagEditor, ManifestReader, SETTINGS } from '@spinnaker/core';

import {
  KubernetesSecurityGroupActions,
  KubernetesSecurityGroupDetailsComponent as KubernetesSecurityGroupDetails,
} from './KubernetesSecurityGroupDetails';
import type { IKubernetesSecurityGroupDetailsProps } from './KubernetesSecurityGroupDetails';
import { KubernetesV2SecurityGroupTransformer } from '../transformer';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

describe('<KubernetesSecurityGroupDetails />', () => {
  let originalAdHocInfraWritesEnabled: boolean;
  let originalEntityTags: boolean;
  let props: IKubernetesSecurityGroupDetailsProps;
  let securityGroupReader: any;

  beforeEach(() => {
    originalAdHocInfraWritesEnabled = SETTINGS.kubernetesAdHocInfraWritesEnabled;
    originalEntityTags = SETTINGS.feature.entityTags;
    SETTINGS.kubernetesAdHocInfraWritesEnabled = true;
    SETTINGS.feature.entityTags = false;
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);

    securityGroupReader = {
      getSecurityGroupDetails: vi.fn().mockResolvedValue(securityGroup()),
    };
    props = {
      app: appWithSecurityGroups(),
      resolvedSecurityGroup: {
        accountId: 'k8s-local',
        name: 'networkPolicy backend-security-policy',
        region: 'dev',
      },
      securityGroupReader,
    } as IKubernetesSecurityGroupDetailsProps;

    vi.spyOn(ManifestReader, 'getManifest').mockResolvedValue(manifestDetails() as any);
  });

  afterEach(() => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
    SETTINGS.feature.entityTags = originalEntityTags;
  });

  it('replaces missing details through the injected state service', () => {
    const stateService = { go: vi.fn(), params: {} };
    const component = new KubernetesSecurityGroupDetails({
      ...props,
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).autoClose();

    expect(stateService.params.allowModalToStayOpen).toBe(true);
    expect(stateService.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
  });

  it('loads security group and manifest details before rendering the sections', async () => {
    render(<KubernetesSecurityGroupDetails {...props} />);

    expect(await screen.findByRole('heading', { name: 'backend-security-policy' })).toBeInTheDocument();
    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      props.app,
      'k8s-local',
      'kubernetes',
      'dev',
      '',
      'networkPolicy backend-security-policy',
    );
    expect(ManifestReader.getManifest).toHaveBeenCalledWith(
      'k8s-local',
      'dev',
      'networkPolicy backend-security-policy',
    );
    expect(screen.getByRole('heading', { name: 'Information' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Labels' })).toBeInTheDocument();
    expect(screen.getByText('k8s-local')).toBeInTheDocument();
    expect(screen.getByText('Account: k8s-local')).toBeInTheDocument();
    expect(screen.getByText(/app.kubernetes.io\/name:\s*kubernetesapp/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Network Policy Actions' })).toBeInTheDocument();
  });

  it('auto-closes when the security group cannot be found', async () => {
    const autoClose = vi.fn();
    securityGroupReader.getSecurityGroupDetails.mockResolvedValue(null);
    render(<KubernetesSecurityGroupDetails {...props} autoClose={autoClose} />);

    await waitFor(() => expect(autoClose).toHaveBeenCalled());
  });

  it('renders notifications from the loaded security group tags', async () => {
    SETTINGS.feature.entityTags = true;
    const user = setupUser();
    securityGroupReader.getSecurityGroupDetails.mockResolvedValue(
      securityGroup({
        entityTags: {
          alerts: [],
          entityRef: {
            account: 'k8s-local',
            cloudProvider: 'kubernetes',
            entityId: 'networkPolicy backend-security-policy',
            entityType: 'securityGroup',
            region: 'dev',
          },
          id: 'security-group-tags',
          notices: [
            {
              lastModified: 1753718892000,
              name: 'backend-maintenance',
              value: {
                message: 'Backend policy maintenance tonight',
                type: 'notice',
              },
            },
          ],
          tags: [],
          tagsMetadata: [],
        },
      }),
    );
    const { container } = render(<KubernetesSecurityGroupDetails {...props} />);

    expect(await screen.findByRole('heading', { name: 'backend-security-policy' })).toBeInTheDocument();
    const noticeMarker = container.querySelector('.notification.fa-flag');
    expect(noticeMarker).toBeInTheDocument();

    await user.hover(noticeMarker as HTMLElement);

    expect(await screen.findByText('Backend policy maintenance tonight')).toBeVisible();
  });

  it('loads standalone security group details when the securityGroups data source is absent', async () => {
    const app = {
      ...appWithSecurityGroups(),
      isStandalone: true,
      getDataSource: () => undefined,
    };
    render(<KubernetesSecurityGroupDetails {...props} app={app} />);

    expect(await screen.findByRole('heading', { name: 'backend-security-policy' })).toBeInTheDocument();
    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      app,
      'k8s-local',
      'kubernetes',
      'dev',
      '',
      'networkPolicy backend-security-policy',
    );
  });
});

describe('<KubernetesSecurityGroupActions />', () => {
  let originalAdHocInfraWritesEnabled: boolean;
  let originalEntityTags: boolean;

  beforeEach(() => {
    originalAdHocInfraWritesEnabled = SETTINGS.kubernetesAdHocInfraWritesEnabled;
    originalEntityTags = SETTINGS.feature.entityTags;
    SETTINGS.kubernetesAdHocInfraWritesEnabled = true;
    SETTINGS.feature.entityTags = false;
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
  });

  afterEach(() => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
    SETTINGS.feature.entityTags = originalEntityTags;
  });

  const openActions = async (app = appWithSecurityGroups()) => {
    const user = setupUser();
    const resource = securityGroup();
    render(<KubernetesSecurityGroupActions app={app} manifest={manifestDetails()} securityGroup={resource} />);
    await user.click(screen.getByRole('button', { name: 'Network Policy Actions' }));
    return { app, resource, user };
  };

  it('opens the delete modal from the actions menu', async () => {
    const { user } = await openActions();

    await user.click(screen.getByRole('menuitem', { name: /Delete/ }));

    expect(screen.getByText('Delete NetworkPolicy backend-security-policy in dev')).toBeInTheDocument();
  });

  it('opens the manifest wizard from the actions menu', async () => {
    const command = { manifest: {} };
    vi.spyOn(KubernetesManifestCommandBuilder, 'buildNewManifestCommand').mockResolvedValue(command as any);
    vi.spyOn(ManifestWizard, 'show').mockReturnValue(undefined);
    const { app, user } = await openActions();

    await user.click(screen.getByRole('menuitem', { name: /Edit/ }));

    await waitFor(() =>
      expect(KubernetesManifestCommandBuilder.buildNewManifestCommand).toHaveBeenCalledWith(
        app,
        manifestDetails().manifest,
        securityGroup().moniker,
        'k8s-local',
      ),
    );
    expect(ManifestWizard.show).toHaveBeenCalledWith({
      title: 'Edit Manifest',
      application: app,
      command,
    });
  });

  it('opens the entity tag editor with the security group context', async () => {
    SETTINGS.feature.entityTags = true;
    const showTagEditor = vi.spyOn(EntityTagEditor, 'show').mockResolvedValue(undefined);
    const { app, resource, user } = await openActions();

    await user.click(screen.getByText('Add notice'));

    expect(showTagEditor).toHaveBeenCalledWith({
      application: app,
      entityRef: null,
      entityType: 'securityGroup',
      isNew: true,
      onUpdate: expect.any(Function),
      owner: resource,
      ownerOptions: undefined,
      tag: {
        name: null,
        value: {
          message: null,
          type: 'notice',
        },
      },
    });
    showTagEditor.mock.calls[0][0].onUpdate();
    expect(app.securityGroups.refresh).toHaveBeenCalled();
  });

  it('does not render action controls when ad-hoc infrastructure writes are disabled', () => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = false;

    const { container } = render(
      <KubernetesSecurityGroupActions
        app={appWithSecurityGroups()}
        manifest={manifestDetails()}
        securityGroup={securityGroup()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole('button', { name: /Actions/ })).not.toBeInTheDocument();
  });
});

describe('KubernetesV2SecurityGroupTransformer', () => {
  it('normalizes Kubernetes security groups without modifying them', async () => {
    const group = securityGroup();

    await expectAsync(new KubernetesV2SecurityGroupTransformer().normalizeSecurityGroup(group)).toBeResolvedTo(group);
  });
});

const appWithSecurityGroups = () =>
  ({
    isStandalone: false,
    getDataSource: () => ({
      ready: () => Promise.resolve(),
      onRefresh: () => () => null,
    }),
    securityGroups: {
      refresh: vi.fn(),
    },
    serverGroups: {
      refresh: vi.fn(),
    },
  } as any);

const securityGroup = (overrides: any = {}) =>
  ({
    account: 'k8s-local',
    apiVersion: 'networking.k8s.io/v1',
    cloudProvider: 'kubernetes',
    createdTime: 1753718892000,
    displayName: 'backend-security-policy',
    kind: 'networkPolicy',
    moniker: { app: 'kubernetesapp', cluster: 'networkPolicy backend-security-policy' },
    name: 'networkPolicy backend-security-policy',
    namespace: 'dev',
    region: 'dev',
    ...overrides,
  } as any);

const manifestDetails = () =>
  ({
    account: 'k8s-local',
    manifest: {
      metadata: {
        annotations: {
          'strategy.details.spinnaker.io/deployment-info': 'Account: {{ resource.account }}',
        },
        labels: {
          'app.kubernetes.io/name': 'kubernetesapp',
        },
        name: 'backend-security-policy',
      },
    },
  } as any);
