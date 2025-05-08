import { registerDefaultFixtures } from '../../support';

describe('cf: CloudFoundry Bake Manifest Tab', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/cloudfoundryapp/pipelines?expand=false&limit=2', {
      fixture: 'cloudfoundry/pipelines/pipelines.json',
    });
    cy.intercept('/applications/cloudfoundryapp/pipelineConfigs', {
      fixture: 'cloudfoundry/pipelines/pipelineConfigs.json',
    });
    cy.intercept('/applications/cloudfoundryapp/pipelineConfigs/bake-cf-manifest', {
      fixture: 'cloudfoundry/pipelines/pipelineConfigs.json',
    });
    cy.intercept('/pipelines/01F6848ZR2N8NJAPBVTEP9R8FV', {
      fixture: 'cloudfoundry/pipelines/01F6848ZR2N8NJAPBVTEP9R8FV.succeeded.json',
    });
  });

  it('shows the Baked Manifest Tab', () => {
    cy.visit('#/applications/cloudfoundryapp/executions');
    cy.get('.execution-group').should('have.length', 1);
    cy.get('.execution-details-button').contains('Execution Details').click();
    cy.get('ul.nav.nav-pills li').first().contains('Baked Manifest').should('contain.text', 'Baked Manifest');
    cy.get('.step-section-details a').first().click();
    cy.get('.modal-dialog').should('exist');
  });
});
