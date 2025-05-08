/**
 * This file configures a test to use a default set of fixtures.
 * This is intended to make it easier to bootstrap a new test without recording ALL request/responses from the workflow.
 * It provides a "backend baseline" that is shared between functional tests.
 * The provided baseline can also be overridden or extended for specific tests.
 *
 * Most of these fixtures will return an empty list (for application clusters, for example).
 * However, there are some baseline fixtures such as accounts and applications that are populated with some base data.
 */
export const registerDefaultFixtures = () => {
  cy.intercept('/applications', { fixture: 'default/applications.json' });
  cy.intercept('/applications/*/clusters', []);
  cy.intercept('/applications/*/firewalls', []);
  cy.intercept('/applications/*/loadBalancers', []);
  cy.intercept('/applications/*/pipelineConfigs', []);
  cy.intercept('/applications/*/pipelines?expand=true*', []);
  cy.intercept('/applications/*/serverGroupManagers', []);
  cy.intercept('/applications/*/serverGroups', []);
  cy.intercept('/applications/*/strategyConfigs', []);
  cy.intercept('/applications/*/tasks?statuses=RUNNING,SUSPENDED,NOT_STARTED', []);
  cy.intercept('/applications/compute?*', { fixture: 'default/application.compute.json' });
  cy.intercept('/applications/ecsapp?*', { fixture: 'default/application.ecsapp.json' });
  cy.intercept('/applications/cloudfoundryapp?*', { fixture: 'default/application.cfapp.json' });
  cy.intercept('/auth/user', { fixture: 'default/auth.user.anonymous.json' });
  cy.intercept('/credentials?expand=true', { fixture: 'default/credentials.expand.json' });
  cy.intercept('/jobs/preconfigured', []);
  cy.intercept('/loadBalancers?provider=appengine', []);
  cy.intercept('/loadBalancers?provider=gce', { fixture: 'default/loadBalancers.gce.json' });
  cy.intercept('/networks/gce', { fixture: 'default/networks.gce.json' });
  cy.intercept('/notifications/application/*', {});
  cy.intercept('/plugins/deck/plugin-manifest.json', []);
  cy.intercept('/search*', []);
  cy.intercept('/securityGroups', []);
  cy.intercept('/notifications/metadata', []);
  cy.intercept('/subnets/gce', { fixture: 'default/subnets.gce.json' });
  cy.intercept('/webhooks/preconfigured', []);
};
