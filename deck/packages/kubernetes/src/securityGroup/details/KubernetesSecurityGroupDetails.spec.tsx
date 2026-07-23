import { shallow } from 'enzyme';
import React from 'react';
import { MenuItem } from 'react-bootstrap';

import {
  AccountTag,
  AddEntityTagLinks,
  CloudProviderLogo,
  CollapsibleSection,
  EntityNotifications,
  ManifestReader,
  SETTINGS,
} from '@spinnaker/core';

import {
  KubernetesSecurityGroupActions,
  KubernetesSecurityGroupDetailsComponent as KubernetesSecurityGroupDetails,
} from './KubernetesSecurityGroupDetails';
import type { IKubernetesSecurityGroupDetailsProps } from './KubernetesSecurityGroupDetails';
import { KubernetesV2SecurityGroupTransformer } from '../transformer';
import { AnnotationCustomSections } from '../../manifest/AnnotationCustomSections';
import { DeleteModal } from '../../manifest/delete/DeleteModal';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestLabels } from '../../manifest/ManifestLabels';
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

    securityGroupReader = {
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.returnValue(Promise.resolve(securityGroup())),
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

    spyOn(ManifestReader, 'getManifest').and.returnValue(Promise.resolve(manifestDetails()) as any);
  });

  it('replaces missing details through the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go'), params: {} };
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

  afterEach(() => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
    SETTINGS.feature.entityTags = originalEntityTags;
  });

  it('loads security group and manifest details before rendering the React sections', async () => {
    const component = shallow(<KubernetesSecurityGroupDetails {...props} />);

    await settle();
    component.update();

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
    expect(component.find(CloudProviderLogo).prop('provider')).toBe('kubernetes');
    expect(component.find('h3').text()).toContain('backend-security-policy');
    expect(component.find(KubernetesSecurityGroupActions).prop('securityGroup')).toEqual(securityGroup());
    expect(component.find(CollapsibleSection).map((section) => section.prop('heading'))).toEqual([
      'Information',
      'Labels',
    ]);
    expect(component.find(AccountTag).prop('account')).toBe('k8s-local');
    expect(component.find(AnnotationCustomSections).prop('manifest')).toEqual(manifestDetails().manifest);
    expect(component.find(AnnotationCustomSections).prop('resource')).toEqual(securityGroup());
    expect(component.find(ManifestLabels).prop('manifest')).toEqual(manifestDetails().manifest);
  });

  it('auto-closes when the security group cannot be found', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    securityGroupReader.getSecurityGroupDetails.and.returnValue(Promise.resolve(null));
    const component = shallow(<KubernetesSecurityGroupDetails {...props} autoClose={autoClose} />);

    await settle();
    component.update();

    expect(autoClose).toHaveBeenCalled();
  });

  it('renders entity tag integrations when enabled', async () => {
    SETTINGS.feature.entityTags = true;
    const component = shallow(<KubernetesSecurityGroupDetails {...props} />);

    await settle();
    component.update();

    expect(component.find(EntityNotifications).prop('entity')).toEqual(securityGroup());
  });

  it('loads standalone security group details when the securityGroups data source is absent', async () => {
    const app = {
      ...appWithSecurityGroups(),
      isStandalone: true,
      getDataSource: () => undefined,
    };
    const component = shallow(<KubernetesSecurityGroupDetails {...props} app={app} />);

    await settle();
    component.update();

    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      app,
      'k8s-local',
      'kubernetes',
      'dev',
      '',
      'networkPolicy backend-security-policy',
    );
    expect(component.find('h3').text()).toContain('backend-security-policy');
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
  });

  afterEach(() => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
    SETTINGS.feature.entityTags = originalEntityTags;
  });

  it('opens the delete modal from the actions menu', () => {
    const component = shallow(
      <KubernetesSecurityGroupActions
        app={appWithSecurityGroups()}
        manifest={manifestDetails()}
        securityGroup={securityGroup()}
      />,
    );

    expect(component.find(DeleteModal).prop('isOpen')).toBe(false);

    component.find(MenuItem).at(0).prop('onClick')({} as any);

    expect(component.find(DeleteModal).prop('isOpen')).toBe(true);
    expect(component.find(DeleteModal).prop('resource')).toEqual(securityGroup());
  });

  it('opens the manifest wizard from the actions menu', async () => {
    const command = { manifest: {} };
    spyOn(KubernetesManifestCommandBuilder, 'buildNewManifestCommand').and.returnValue(Promise.resolve(command) as any);
    spyOn(ManifestWizard, 'show');
    const app = appWithSecurityGroups();
    const component = shallow(
      <KubernetesSecurityGroupActions app={app} manifest={manifestDetails()} securityGroup={securityGroup()} />,
    );

    component.find(MenuItem).at(1).prop('onClick')({} as any);
    await settle();

    expect(KubernetesManifestCommandBuilder.buildNewManifestCommand).toHaveBeenCalledWith(
      app,
      manifestDetails().manifest,
      securityGroup().moniker,
      'k8s-local',
    );
    expect(ManifestWizard.show).toHaveBeenCalledWith({
      title: 'Edit Manifest',
      application: app,
      command,
    });
  });

  it('renders entity tag links when enabled', () => {
    SETTINGS.feature.entityTags = true;
    const app = appWithSecurityGroups();
    const component = shallow(
      <KubernetesSecurityGroupActions app={app} manifest={manifestDetails()} securityGroup={securityGroup()} />,
    );

    expect(component.find(AddEntityTagLinks).prop('component')).toEqual(securityGroup());
    expect(component.find(AddEntityTagLinks).prop('application')).toBe(app);
    expect(component.find(AddEntityTagLinks).prop('entityType')).toBe('securityGroup');
  });

  it('does not render action controls when ad-hoc infrastructure writes are disabled', () => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = false;

    const component = shallow(
      <KubernetesSecurityGroupActions
        app={appWithSecurityGroups()}
        manifest={manifestDetails()}
        securityGroup={securityGroup()}
      />,
    );

    expect(component.isEmptyRender()).toBe(true);
    expect(component.find('Dropdown').exists()).toBe(false);
    expect(component.find(MenuItem).exists()).toBe(false);
    expect(component.find(DeleteModal).exists()).toBe(false);
  });
});

describe('KubernetesV2SecurityGroupTransformer', () => {
  it('normalizes Kubernetes security groups without modifying them', async () => {
    const group = securityGroup();

    await expectAsync(new KubernetesV2SecurityGroupTransformer().normalizeSecurityGroup(group)).toBeResolvedTo(group);
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));

const appWithSecurityGroups = () =>
  ({
    isStandalone: false,
    getDataSource: () => ({
      ready: () => Promise.resolve(),
      onRefresh: () => () => null,
    }),
    securityGroups: {
      refresh: jasmine.createSpy('refreshSecurityGroups'),
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
          'strategy.spinnaker.io/deployment-info': 'Account: {{ resource.account }}',
        },
        labels: {
          'app.kubernetes.io/name': 'kubernetesapp',
        },
        name: 'backend-security-policy',
      },
    },
  } as any);
