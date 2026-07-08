import type { ApplicationStateProvider, INestedState } from '@spinnaker/core';
import { ApplicationDataSourceRegistry, INFRASTRUCTURE_KEY, SETTINGS } from '@spinnaker/core';

// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import type { ApplicationStateRegistration } from '../../../core/src/application/applicationState.registration';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import {
  getApplicationStateRegistrationsForTests,
  resetApplicationStateRegistrationsForTests,
} from '../../../core/src/application/applicationState.registration';
import { registerKubernetesRawResources } from '../rawResource';
import {
  KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY,
  registerKubernetesRawResourceDataSource,
} from '../rawResource/rawResource.dataSource';
import {
  KUBERNETES_RAW_RESOURCE_DETAILS_STATE,
  KUBERNETES_RAW_RESOURCE_INSIGHT_STATE,
  registerKubernetesRawResourceStates,
} from '../rawResource/rawResource.states';
import { KUBERNETES_RESOURCE_DETAILS_STATE } from './resources.state';

describe('Kubernetes resource state registration', () => {
  let originalRegistrations: ApplicationStateRegistration[];

  function makeApplicationStateProvider(): ApplicationStateProvider {
    return {
      addInsightState: jasmine.createSpy('addInsightState'),
      addInsightDetailState: jasmine.createSpy('addInsightDetailState'),
    } as any;
  }

  beforeEach(() => {
    originalRegistrations = getApplicationStateRegistrationsForTests();
    resetApplicationStateRegistrationsForTests();
    ApplicationDataSourceRegistry.clearDataSources();
  });

  afterEach(() => {
    resetApplicationStateRegistrationsForTests(originalRegistrations);
    ApplicationDataSourceRegistry.clearDataSources();
    SETTINGS.resetToOriginal();
  });

  it('defines the generic Kubernetes resource detail state as a React insight detail', () => {
    const applicationStateProvider = makeApplicationStateProvider();

    applicationStateProvider.addInsightDetailState(KUBERNETES_RESOURCE_DETAILS_STATE);

    expect(applicationStateProvider.addInsightDetailState).toHaveBeenCalledWith(
      jasmine.objectContaining<INestedState>({
        name: 'kubernetesResource',
        url: '/manifest/:provider/:accountId/:region/:kubernetesResource',
      }),
    );
    expect(KUBERNETES_RESOURCE_DETAILS_STATE.views['detail@../insight'].$type).toBe('react');
  });

  it('defines raw resource insight and detail states as React states', () => {
    expect(KUBERNETES_RAW_RESOURCE_INSIGHT_STATE).toEqual(
      jasmine.objectContaining<INestedState>({
        name: 'k8s',
        url: '/kubernetes',
      }),
    );
    expect(KUBERNETES_RAW_RESOURCE_INSIGHT_STATE.views.nav.$type).toBe('react');
    expect(KUBERNETES_RAW_RESOURCE_INSIGHT_STATE.views.master.$type).toBe('react');

    expect(KUBERNETES_RAW_RESOURCE_DETAILS_STATE).toEqual(
      jasmine.objectContaining<INestedState>({
        name: 'rawResourceDetails',
        url: '/rawResourceDetails/:account/:region/:name',
      }),
    );
    expect(KUBERNETES_RAW_RESOURCE_DETAILS_STATE.views['detail@../insight'].$type).toBe('react');
  });

  it('queues raw resource states without an Angular module config block', () => {
    registerKubernetesRawResourceStates();
    const applicationStateProvider = makeApplicationStateProvider();

    getApplicationStateRegistrationsForTests().forEach((registration) => registration(applicationStateProvider));

    expect(applicationStateProvider.addInsightState).toHaveBeenCalledWith(
      jasmine.objectContaining<INestedState>({
        name: 'k8s',
        url: '/kubernetes',
      }),
    );
    expect(applicationStateProvider.addInsightDetailState).toHaveBeenCalledWith(
      jasmine.objectContaining<INestedState>({
        name: 'rawResourceDetails',
        url: '/rawResourceDetails/:account/:region/:name',
      }),
    );
  });

  it('registers the raw resource data source without an Angular module run block', () => {
    registerKubernetesRawResourceDataSource();

    const rawResourceDataSource = ApplicationDataSourceRegistry.getDataSources().find(
      (dataSource) => dataSource.key === KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY,
    );

    expect(rawResourceDataSource).toEqual(
      jasmine.objectContaining({
        key: KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY,
        label: 'Kubernetes',
        category: INFRASTRUCTURE_KEY,
        sref: '.insight.k8s',
        primary: true,
        providerField: 'cloudProvider',
        credentialsField: 'account',
        regionField: 'region',
      }),
    );
  });

  it('keeps raw resource registration behind the feature flag', () => {
    SETTINGS.feature.kubernetesRawResources = false;

    registerKubernetesRawResources();

    expect(ApplicationDataSourceRegistry.getDataSources()).toEqual([]);
    expect(getApplicationStateRegistrationsForTests()).toEqual([]);

    SETTINGS.feature.kubernetesRawResources = true;

    registerKubernetesRawResources();

    expect(
      ApplicationDataSourceRegistry.getDataSources().some(
        (dataSource) => dataSource.key === KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY,
      ),
    ).toBe(true);
    expect(getApplicationStateRegistrationsForTests().length).toBe(1);
  });
});
