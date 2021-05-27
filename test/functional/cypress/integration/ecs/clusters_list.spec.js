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
    cy.route('/instances/ecs-my-aws-devel-acct/us-west-2/f8757e00-184d-4288-b535-4124a739e7be',
      'fixture:ecs/clusters/serverGroups.json');
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

  it('shows stored instance details view action', () => {
    cy.visit('#/applications/ecsapp/clusters');

    cy.get('.sub-group:contains("aws-prod-ecsdemo")')
      .find('.server-group:contains("v000")')
      .find('.cluster-container')
      .find('.instances')
      .find('.instance-group')
      .get('a[title="f8757e00-184d-4288-b535-4124a739e7be"]')
      .click();

    cy.get('.details-panel > .header')
      .get('instance-details-header')
      .get('.header-text')
      .get('h3:contains("f8757e00-184d-4288-b535-4124a739e7be")');

    cy.get('[data-test-id="instanceDetails.content"]')
      .get('.collapsible-section').eq(0)
      .get('dd:contains("2020-07-20 03:41:03 PDT")');

    cy.get('[data-test-id="instanceDetails.content"]')
      .get('.collapsible-section').eq(0)
      .get('dd:contains("aws-final-test-v000")');

    cy.get('[data-test-id="instanceDetails.content"]')
      .get('.collapsible-section').eq(1)
      .get('span:contains("Up")');

    cy.get('[data-test-id="instanceDetails.content"]')
      .get('.collapsible-heading').eq(2)
      .click()
      .get('span:contains("SpinnakerVPC (vpc-0a9ccfd1cba7bc715)")');
  });
});
