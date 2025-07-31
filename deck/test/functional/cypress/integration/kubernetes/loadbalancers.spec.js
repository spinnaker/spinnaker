import { registerDefaultFixtures } from '../../support';

describe('kubernetes: Load Balancers', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/kubernetesapp/loadBalancers', {
      fixture: 'kubernetes/loadBalancers/loadBalancers.json',
    });
    cy.intercept('/manifests/k8s-local/dev/service*', {
      fixture: 'kubernetes/manifests/service.json',
    });
    cy.intercept('POST', '/tasks', {ref: '/tasks/01K17CHBN7Y358PSRE7GR04DC0'});
    cy.intercept('/tasks/01K17CHBN7Y358PSRE7GR04DC0', {
      fixture: 'kubernetes/task/task.success.json',
    });
  });

  it('should display the loadbalancer section', () => {
    cy.visit('#/applications/kubernetesapp/loadBalancers');

    cy.contains('.form-group label', 'Show').should('exist');
    cy.get('input[type="checkbox"][name="showServerGroups"]').should('be.checked');
    cy.get('input[type="checkbox"][name="showInstances"]').should('exist');

    cy.contains('button', 'Create Load Balancer').should('exist');

    cy.contains('.rollup-title-cell', 'ingress backend')
      .should('be.visible')
      .parent()
      .parent()
      .within(() => {
        cy.contains('span.account-tag-name', 'k8s-local').should('exist');
        cy.contains('h6', 'DEV').should('exist');
      });

    cy.contains('.rollup-title-cell', 'service backend')
      .should('be.visible')
      .parent()
      .parent()
      .within(() => {
        cy.contains('span.account-tag-name', 'k8s-local').should('exist');
        cy.contains('h6', 'DEV').should('exist');
        cy.get('.health-counts').should('contain.text', '2').and('contain.text', '100%');
        cy.contains('.server-group-title', 'replicaSet backend-65b97dd546').should('exist');
      });

    cy.contains('.rollup-title-cell', 'service statefulset')
      .should('be.visible')
      .parent()
      .parent()
      .within(() => {
        cy.contains('span.account-tag-name', 'k8s-local').should('exist');
        cy.contains('h6', 'DEV').should('exist');
      });
  });

  it('should displays load balancer instances when "instances" is checked', () => {
    cy.visit('#/applications/kubernetesapp/loadBalancers');

    cy.contains('label', 'Server Groups')
      .find('input[type="checkbox"]')
      .check({force: true});

    cy.contains('label', 'Instances')
      .find('input[type="checkbox"]')
      .check({force: true});

    cy.get('.instance-group.instance-group-undefined').should('exist');

    cy.get('.instance-group.instance-group-undefined').within(() => {
      cy.get('a').each(($a) => {
        cy.wrap($a)
          .should('have.attr', 'title')
          .and('match', /^pod/);
      });
    });
  });

  it('should open service details and validates content', () => {
    cy.visit('#/applications/kubernetesapp/loadBalancers');

    cy.contains('.rollup-title-cell', 'service backend')
      .should('be.visible')
      .parents('.load-balancer-pod')
      .within(() => {
        cy.contains('h6.clickable', 'DEV').click();
      });

    cy.get('.details-panel .header h3').should('contain.text', 'backend');
    cy.get('.details-panel .header .icon-kubernetes').should('exist');
    cy.contains('button', 'Service Actions').should('exist');

    cy.contains('h4', 'Information').should('be.visible');
    cy.contains('dt', 'Created').next('dd').should('contain.text', '2025-07-28');
    cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
    cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'dev');
    cy.contains('dt', 'Kind').next('dd').should('contain.text', 'service');
    cy.contains('dt', 'Service Type').next('dd').should('contain.text', 'ClusterIP');
    cy.contains('dt', 'Sess. Affinity').next('dd').should('contain.text', 'None');

    cy.get('.collapsible-section')
      .contains('Status')
      .parent()
      .within(() => {
      cy.get('.collapsible-heading').should('contain.text', 'Status');
      cy.get('.content-body').should('be.visible');

      cy.contains('dt', 'Workloads').should('exist');
      cy.contains('dd a', 'replicaSet backend-65b97dd546')
        .should('have.attr', 'href')
        .and('include', 'replicaSet%20backend-65b97dd546');

      cy.contains('dt', 'Pod status').should('exist');
      cy.get('.instance-health-counts')
        .should('contain.text', '2')
        .and('contain.text', '100%');

      cy.contains('dt', 'Cluster IP').should('exist');
      cy.contains('dd a', '10.96.17.52')
        .should('have.attr', 'href')
        .and('include', '//10.96.17.52');

      cy.get('button.clipboard-btn').should('have.attr', 'aria-label', 'Copy to clipboard');
    });

    cy.contains('h4', 'Events').scrollIntoView().should('be.visible');
    cy.get('.collapsible-section')
      .contains('Events')
      .parent()
      .should('contain.text', 'No recent events found');

    const labels = [
      'app: backend',
      'app.kubernetes.io/managed-by: spinnaker',
      'app.kubernetes.io/name: kubernetesapp',
    ];
    cy.contains('h4', 'Labels').scrollIntoView().should('be.visible');
    labels.forEach(label => {
      cy.get('.collapsible-section')
        .contains('Labels')
        .parent()
        .should('contain.text', label);
    });

    cy.contains('button', 'Service Actions').click();
    cy.contains('a', 'Delete').should('exist');
    cy.contains('a', 'Edit').should('exist');
  });

});
