import { registerDefaultFixtures } from '../../support';

describe('kubernetes: Firewalls', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/securityGroups', {
      fixture: 'kubernetes/securityGroups/securityGroups.json',
    });
    cy.intercept('/search?pageSize=500&q=kubernetesapp&type=securityGroups', {
      fixture: 'kubernetes/securityGroups/search/search_result.json',
    });
    cy.intercept('/securityGroups/k8s-local/dev/networkPolicy*?provider=kubernetes&vpcId=', {
      fixture: 'kubernetes/securityGroups/networkPolicy.json',
    });
    cy.intercept('/manifests/k8s-local/dev/networkPolicy*', {
      fixture: 'kubernetes/manifests/networkPolicy.json',
    });
    cy.intercept('POST', '/tasks', {ref: '/tasks/01K17CHBN7Y358PSRE7GR04DC0'});
    cy.intercept('/tasks/01K17CHBN7Y358PSRE7GR04DC0', {
      fixture: 'kubernetes/task/task.success.json',
    });
  });

  it('should display the firewall section', () => {
    cy.visit('#/applications/kubernetesapp/firewalls');

    cy.contains('.form-group label', 'Show').should('exist');
    cy.get('input[type="checkbox"][name="showServerGroups"]').should('be.checked');
    cy.get('input[type="checkbox"][name="showLoadBalancers"]').should('be.checked');

    cy.contains('button', 'Create Firewall').should('exist');

    cy.contains('.rollup-title-cell', 'networkPolicy backend-security-policy')
      .should('be.visible')
      .within(() => {
        cy.contains('span.account-tag-name', 'k8s-local').should('exist');
      });

    cy.get('.security-group-pod')
      .should('exist')
      .within(() => {
        cy.get('.pod-subgroup.clickable')
          .should('have.attr', 'href')
          .and('include', '/firewalls/firewallDetails/kubernetes/k8s-local/dev/networkPolicy%20backend-security-policy');

        cy.contains('h6 .clickable-header', 'DEV').should('exist');

        cy.contains('.rollup-details-section.col-md-6', 'No server groups').should('exist');
        cy.contains('.rollup-details-section.col-md-6', 'No load balancers').should('exist');
      });
  });

  it('should open network policy details and validates content', () => {
    cy.visit('#/applications/kubernetesapp/firewalls');

    cy.contains('.rollup-title-cell', 'networkPolicy backend-security-policy')
      .should('be.visible')
      .parents('.security-group-pod')
      .within(() => {
        cy.contains('h6.highlightable-header', 'DEV').click();
      });

    cy.get('.details-panel .header h3').should('contain.text', 'backend-security-policy');
    cy.get('.details-panel .header .icon-kubernetes').should('exist');
    cy.contains('button', 'Network Policy Actions').should('exist');

    cy.contains('h4', 'Information').should('be.visible');
    cy.contains('dt', 'Created').next('dd').should('contain.text', '2025-07-28');
    cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
    cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'dev');
    cy.contains('dt', 'Kind').next('dd').should('contain.text', 'networkPolicy');

    cy.contains('.collapsible-section', 'deployment info')
      .as('deploymentInfoSection')
      .should('exist')
      .within(() => {
        cy.contains('Account:').should('exist').parent().should('contain.text', 'k8s-local');
        cy.contains('Display Name:').should('exist').parent().should('contain.text', 'backend-security-policy');
      });

    const labels = [
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

    cy.contains('button', 'Network Policy Actions').click();
    cy.contains('a', 'Delete Firewall').should('exist');
    cy.contains('a', 'Edit Firewall').should('exist');
  });

});
