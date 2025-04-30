import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('appengine: Pipelines', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/gae?expand=false', {
      fixture: 'appengine/pipelines_list/application.json',
    });
    cy.intercept('/applications/gae/pipelines?expand=true&*&statuses=*', {
      fixture: 'appengine/pipelines_list/pipelines.running.json',
    });
    cy.intercept('/applications/gae/pipelines?expand=false*', {
      fixture: 'appengine/pipelines_list/pipelines.json',
    });
    cy.intercept('/applications/gae/pipelineConfigs', {
      fixture: 'appengine/pipelines_list/pipelineConfigs.json',
    });
  });

  it('shows stored appengine pipelines with their account tag', () => {
    cy.visit('#/applications/gae/executions');
    cy.get('.execution-group').should('have.length', 4);
    cy.get('.account-tag').first().should('contain.text', 'gae');
  });
});
