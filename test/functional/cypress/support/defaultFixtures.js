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
  cy.server();
  cy.route('/applications', 'fixture:default/applications.json');
  cy.route('/applications/*/clusters', []);
  cy.route('/applications/*/firewalls', []);
  cy.route('/applications/*/loadBalancers', []);
  cy.route('/applications/*/pipelineConfigs', []);
  cy.route('/applications/*/pipelines?expand=true*', []);
  cy.route('/applications/*/serverGroupManagers', []);
  cy.route('/applications/*/serverGroups', []);
  cy.route('/applications/*/strategyConfigs', []);
  cy.route('/applications/*/tasks?statuses=RUNNING,SUSPENDED,NOT_STARTED', []);
  cy.route('/applications/compute?*', 'fixture:default/application.compute.json');
  cy.route('/applications/ecsapp?*', 'fixture:default/application.ecsapp.json');
  cy.route('/applications/cloudfoundryapp?*', 'fixture:default/application.cfapp.json');
  cy.route('/auth/user', 'fixture:default/auth.user.anonymous.json');
  cy.route('/credentials?expand=true', 'fixture:default/credentials.expand.json');
  cy.route('/jobs/preconfigured', []);
  cy.route('/loadBalancers?provider=appengine', []);
  cy.route('/loadBalancers?provider=gce', 'fixture:default/loadBalancers.gce.json');
  cy.route('/networks/gce', 'fixture:default/networks.gce.json');
  cy.route('/notifications/application/*', {});
  cy.route('/plugins/deck/plugin-manifest.json', []);
  cy.route('/search*', []);
  cy.route('/securityGroups', []);
  cy.route('notifications/metadata', []);
  cy.route('/subnets/gce', 'fixture:default/subnets.gce.json');
  cy.route('/webhooks/preconfigured', []);
};
