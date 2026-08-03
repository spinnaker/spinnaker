import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Manual execution', () => {
  let executionStatus = 'empty';

  beforeEach(() => {
    executionStatus = 'empty';
    registerDefaultFixtures();

    // Start with no pipeline executions
    cy.intercept('/applications/compute/pipelines*', (req) => {
      req.alias = req.url.includes('statuses=') ? 'runningPipelines' : 'visiblePipelines';
      if (executionStatus === 'running') {
        req.reply({ fixture: 'core/manual_execution/pipelines.running.json' });
      } else if (executionStatus === 'succeeded') {
        req.reply({ fixture: 'core/manual_execution/pipelines.succeeded.json' });
      } else {
        req.reply([]);
      }
    });
    cy.intercept('/applications/compute/pipelineConfigs', { fixture: 'core/manual_execution/pipelineConfigs.json' });
    cy.intercept('/applications/compute/pipelineConfigs/Simple%20Wait%20Pipeline', {
      fixture: 'core/manual_execution/Simple Wait Pipeline.json',
    });
    cy.intercept('POST', '/pipelines/v2/compute/*', {
      eventId: 'c34674fd-12f8-4a40-9cb8-a9a16fdf7bcc',
      ref: '/pipelines/01E1ZG0473QSGSNNMEB3P04C1A',
    });
  });

  it('executes a simple Wait Stage pipeline', () => {
    cy.visit('#/applications/compute/executions');
    cy.wait('@visiblePipelines').its('response.body').should('have.length', 0);

    // Start a manual execution of Simple Wait Pipeline
    cy.get('.input-sm:contains("Pipeline")').select('Pipeline');
    cy.contains('.execution-group', 'Simple Wait Pipeline')
      .contains('a.btn-link', 'Start Manual Execution')
      .scrollIntoView()
      .click({ force: true });

    // Start the execution (Click Run)
    cy.intercept('GET', '/pipelines/01E1ZG0473QSGSNNMEB3P04C1A', {
      fixture: 'core/manual_execution/01E1ZG0473QSGSNNMEB3P04C1A.running.json',
    });
    cy.then(() => (executionStatus = 'running'));
    cy.get('button:contains("Run")').click();
    cy.get('.application-header-icon').click();
    cy.wait('@visiblePipelines').its('response.body').should('have.length', 1);

    // Execution status says RUNNING
    cy.get('.execution-group:contains("Simple Wait Pipeline")').get('.execution-status:contains("RUNNING")');

    // Execution has succeeded -- click refresh
    cy.then(() => (executionStatus = 'succeeded'));
    cy.get('.application-header-icon').click();
    cy.wait('@visiblePipelines').its('response.body').should('have.length', 1);

    // Execution status says SUCCEEDED
    cy.get('.execution-group:contains("Simple Wait Pipeline")').get('.execution-status:contains("SUCCEEDED")');
  });
});
