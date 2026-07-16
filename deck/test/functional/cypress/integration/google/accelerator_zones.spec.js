import { registerDefaultFixtures } from '../../support';

describe('google: Create Server Group Modal GPU Accelerators', () => {
  before(() => {
    require('events').EventEmitter.defaultMaxListeners = 15;
  });

  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/credentials?expand=true', {
      fixture: 'google/accelerator_zones/credentials.json',
    });

    cy.intercept('/images/find?*', {
      fixture: 'google/shared/images.json',
    });
  });

  it('should not show NVIDIA Tesla K80 initially', () => {
    cy.visit('#/applications/compute/clusters');
    cy.window().its('angular').should('exist');
    cy.get('button:contains("Create Server Group")', { timeout: 10000 }).should('be.visible').click();

    cy.contains('ul.steps-indicator li a', 'Advanced Settings').click();

    cy.get('[data-testid="add-accelerator"]:visible').click();
    cy.get('[data-testid="accelerator-type-0"]:visible option').then((options) => {
      const types = Array.from(options).map((option) => option.textContent);
      expect(types).not.to.include('NVIDIA Tesla K80');
    });
  });

  it('should show NVIDIA Tesla K80 after selecting us-east1-c zone', () => {
    cy.visit('#/applications/compute/clusters');
    cy.window().its('angular').should('exist');
    cy.get('button:contains("Create Server Group")', { timeout: 10000 }).should('be.visible').click();

    // Select region and zone
    cy.get('select[aria-label="Region"]:visible').select('us-east1');
    cy.contains('ul.steps-indicator li a', 'Capacity/Distribution').click();
    cy.get('select[aria-label="Zone"]:visible').select('us-east1-c');

    cy.contains('ul.steps-indicator li a', 'Advanced Settings').click();

    cy.get('[data-testid="add-accelerator"]:visible').click();
    cy.get('[data-testid="accelerator-type-0"]:visible option').then((options) => {
      const types = Array.from(options).map((option) => option.textContent);
      expect(types).to.include('NVIDIA Tesla K80');
    });
  });

  afterEach(() => {
    cy.window().then((win) => {
      win && win.removeAllListeners && win.removeAllListeners();
    });
  });
});
