import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Manual execution', () => {
  beforeEach(() => {
    registerDefaultFixtures();

    // Start with no pipeline executions
    cy.route('/applications/compute/pipelines*', []);
    cy.route('/applications/compute/pipelineConfigs', 'fixture:core/manual_execution/pipelineConfigs.json');
    cy.route(
      '/applications/compute/pipelineConfigs/Simple Wait Pipeline',
      'fixture:core/manual_execution/Simple Wait Pipeline.json',
    );
    cy.route('POST', '/pipelines/v2/compute/*', {
      eventId: 'c34674fd-12f8-4a40-9cb8-a9a16fdf7bcc',
      ref: '/pipelines/01E1ZG0473QSGSNNMEB3P04C1A',
    });
  });

  it('executes a simple Wait Stage pipeline', () => {
    cy.visit('#/applications/compute/executions');
    // Start a manual execution of Simple Wait Pipeline
    const simpleWaitPipeline = cy.get('.execution-group:contains("Simple Wait Pipeline")');
    simpleWaitPipeline.get('a.btn-link:contains("Start Manual Execution")').click();

    // Start the execution (Click Run)
    cy.route(
      '/pipelines/01E1ZG0473QSGSNNMEB3P04C1A',
      'fixture:core/manual_execution/01E1ZG0473QSGSNNMEB3P04C1A.running.json',
    );
    cy.route('/applications/compute/pipelines*', 'fixture:core/manual_execution/pipelines.running.json');
    cy.get('button:contains("Run")').click();

    // Execution status says RUNNING
    cy.get('.execution-group:contains("Simple Wait Pipeline")').get('.execution-status:contains("RUNNING")');

    // Execution has succeeded -- click refresh
    cy.route('/applications/compute/pipelines*', 'fixture:core/manual_execution/pipelines.succeeded.json');
    cy.get('.application-header-icon').click();

    // Execution status says SUCCEEDED
    cy.get('.execution-group:contains("Simple Wait Pipeline")').get('.execution-status:contains("SUCCEEDED")');
  });
});
