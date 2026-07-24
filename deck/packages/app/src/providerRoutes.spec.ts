import { UIRouterReact } from '@uirouter/react';

import '@spinnaker/ecs';
import '@spinnaker/kubernetes';

// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import type { ApplicationStateRegistration } from '../../core/src/application/applicationState.registration';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import {
  getApplicationStateRegistrationsForTests,
  resetApplicationStateRegistrationsForTests,
} from '../../core/src/application/applicationState.registration';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import '../../core/src/navigation/coreRoutes';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { createDeckRuntime } from '../../core/src/bootstrap/DeckRuntime';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { setDirectRouter } from '../../core/src/navigation/directRouter';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import type { RootStateRegistration } from '../../core/src/navigation/rootState.registration';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import {
  getRootStateRegistrationsForTests,
  resetRootStateRegistrationsForTests,
} from '../../core/src/navigation/rootState.registration';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { configureRouter } from '../../core/src/navigation/router';

const providerApplicationRegistrations = getApplicationStateRegistrationsForTests();
const providerRootRegistrations = getRootStateRegistrationsForTests();

describe('direct provider route registration', () => {
  let originalApplicationRegistrations: ApplicationStateRegistration[];
  let originalRootRegistrations: RootStateRegistration[];
  let router: UIRouterReact;

  beforeEach(() => {
    originalApplicationRegistrations = getApplicationStateRegistrationsForTests();
    originalRootRegistrations = getRootStateRegistrationsForTests();
    resetApplicationStateRegistrationsForTests(providerApplicationRegistrations);
    resetRootStateRegistrationsForTests(providerRootRegistrations);
  });

  afterEach(() => {
    router?.dispose();
    setDirectRouter(null);
    resetApplicationStateRegistrationsForTests(originalApplicationRegistrations);
    resetRootStateRegistrationsForTests(originalRootRegistrations);
  });

  it('loads Kubernetes and ECS states into both application trees', () => {
    router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services);
    const stateNames = router.stateRegistry.get().map((state) => state.name);

    [
      'home.applications.application.insight.clusters.kubernetesResource',
      'home.project.application.insight.clusters.kubernetesResource',
      'home.applications.application.insight.clusters.ecsTargetGroupDetails',
      'home.project.application.insight.clusters.ecsTargetGroupDetails',
    ].forEach((stateName) => expect(stateNames).toContain(stateName));
  });
});
