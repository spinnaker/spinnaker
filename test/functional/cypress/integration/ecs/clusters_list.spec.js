import { registerDefaultFixtures } from '../../support';

describe('Amazon ECS: aws-prod-ecsdemo cluster', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.route('/networks/aws', 'fixture:ecs/default/networks.aws-ecs.json');
    cy.route('/applications/ecsapp/serverGroups', 'fixture:ecs/clusters/serverGroups.json');
    cy.route(
      '/applications/ecsapp/serverGroups/**/aws-prod-ecsdemo-v000?includeDetails=false',
      'fixture:ecs/clusters/serverGroup.ecsdemo-v000.json',
    );
  });

  it('shows stored ECS cluster with their sequences', () => {
    cy.visit('#/applications/ecsapp/clusters');

    cy.get('.sub-group').should('have.length', 6);

    cy.get('.sub-group')
      .first()
      .should('contain.text', 'aws-final-test');
  });

  it('shows stored details view and ECS server group actions', () => {
    cy.visit('#/applications/ecsapp/clusters');

    cy.get('.sub-group:contains("aws-prod-ecsdemo")')
      .find('.server-group:contains("v000")')
      .click({ force: true });

    cy.get('.btn:contains("Server Group Actions")')
      .click()
      .get('.dropdown-menu')
      .get('.ng-scope')
      .should('contain.text', 'Rollback');

    cy.get('a:contains("Rollback")').click({ force: true });

    cy.get('.modal-title').should('contain.text', 'Rollback aws-prod-ecsdemo');
  });

  it('shows stored details view and ECS server group actions', () => {
    cy.visit('#/applications/ecsapp/clusters');

    cy.get('.sub-group:contains("aws-prod-ecsdemo")')
      .find('.server-group:contains("v000")')
      .click({ force: true });

    cy.get('.btn:contains("Server Group Actions")')
      .click()
      .get('a:contains("Rollback")')
      .click();
  });
});
