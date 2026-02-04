import { registerDefaultFixtures } from '../../support';

describe('kubernetes: Raw Resource', () => {

  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/kubernetesapp/rawResources', {
      fixture: 'kubernetes/rawResources/rawResources.json',
    });
    cy.intercept('/manifests/k8s-local/dev/deployment*', {
      fixture: 'kubernetes/manifests/deployment.json',
    });
  });

  it('should display the raw resource section', () => {
    cy.visit('#/applications/kubernetesapp/kubernetes');

    cy.get('.K8sResources .header').should('exist');
    cy.contains('.StandardFieldLayout_Label', 'Group By').should('be.visible');

    cy.get('input[name="groupBy"]').should('have.value', 'None');
    cy.get('.Select.groupby .Select-value-label').should('contain.text', 'None');

    cy.get('.K8sResources .content .RawResource.card.clickable.clickable-row')
      .as('cards')
      .should('have.length', 12);

    cy.get('@cards').each(($card) => {
      cy.wrap($card).find('.title .icon-kubernetes').should('exist');
    });
  });

  it('should render all raw resources with correct metadata', () => {
    cy.visit('#/applications/kubernetesapp/kubernetes');

    const expectedResources = [
      { kind: 'daemonSet', name: 'daemonset', apiVersion: 'apps/v1' },
      { kind: 'deployment', name: 'backend', apiVersion: 'apps/v1' },
      { kind: 'deployment', name: 'database', apiVersion: 'apps/v1'},
      { kind: 'deployment', name: 'frontend', apiVersion: 'apps/v1'},
      { kind: 'ingress', name: 'backend', apiVersion: 'networking.k8s.io/v1' },
      { kind: 'networkPolicy', name: 'backend-security-policy', apiVersion: 'networking.k8s.io/v1' },
      { kind: 'replicaSet', name: 'backend-65b97dd546', apiVersion: 'apps/v1' },
      { kind: 'replicaSet', name: 'database-cb985c8d8', apiVersion: 'apps/v1' },
      { kind: 'replicaSet', name: 'frontend-5b94cfdd4c', apiVersion: 'apps/v1' },
      { kind: 'service', name: 'backend', apiVersion: 'v1' },
      { kind: 'service', name: 'statefulset', apiVersion: 'v1' },
      { kind: 'statefulSet', name: 'statefulset', apiVersion: 'apps/v1' },
    ];

    expectedResources.forEach(({ kind, name, apiVersion }) => {
      cy.contains('.RawResource .title', `${kind} ${name}`)
        .scrollIntoView()
        .should('be.visible')
        .parents('.RawResource.card.clickable.clickable-row')
        .as('card');

      cy.get('@card').within(() => {
        cy.contains('.details .column .title', 'account:')
          .siblings('div')
          .should('contain.text', 'k8s-local');

        cy.contains('.details .column .title', 'namespace:')
          .siblings('div')
          .should('contain.text', 'dev');

        cy.contains('.details .column .title', 'apiVersion:')
          .siblings('div')
          .should('contain.text', apiVersion);
      });
    });
  });

  it('should open deployment details', () => {
    cy.visit('#/applications/kubernetesapp/kubernetes');

    cy.contains('.collapsible-filter-section h4', 'Kind')
      .parents('.collapsible-filter-section')
      .within(() => {
        cy.get('.content-body').should('be.visible');
        cy.contains('label', 'deployment')
          .find('input[type="checkbox"]')
          .check({ force: true })
          .should('be.checked');
      });

    cy.contains('.K8sResources .content .RawResource .title', 'deployment backend')
      .parents('.RawResource.card.clickable.clickable-row')
      .then(($card) => {
        cy.wrap($card).click({ force: true });
      });

    cy.get('.details-panel .header h3').should('contain.text', 'backend');
  });

  it('should group by', () => {
    cy.visit('#/applications/kubernetesapp/kubernetes');

    const selectGroupBy = (label) => {
      cy.get('.Select.groupby .Select-control').click({ force: true });
      cy.get('.Select-menu-outer .Select-option, .Select-menu .Select-option')
        .contains(new RegExp(`^${label}$`))
        .click({ force: true });
      cy.get('input[name="groupBy"]').should('have.value', label);
      cy.get('.Select.groupby .Select-value-label').should('contain.text', label);
    };

    cy.get('.K8sResources .content .RawResource.card.clickable.clickable-row')
      .should('have.length.at.least', 1);

    // Select "Kind"
    selectGroupBy('Kind');
    cy.get('.RawResourceGroups .RawResourceGroup')
      .as('groups')
      .should('have.length', 7);

    // Select "Account"
    selectGroupBy('Account');
    cy.get('@groups')
      .should('have.length', 1);

    // Select "Namespace"
    selectGroupBy('Namespace');
    cy.get('@groups')
      .should('have.length', 1);

    // Select "None"
    selectGroupBy('None');
    cy.contains('@groups').should('not.exist');
  });
});
