import { InfrastructurePage } from '../core/pages/InfrastructurePage';
import { CloneModalPage } from './pages/CloneModalPage';

describe('Clone Modal', () => {
  describe('Build custom instance type', () => {
    it('provides options for cores and memory', () => {
      const infra = new InfrastructurePage();
      const cloneModal = new CloneModalPage();
      infra.openClustersForApplication('compute');
      infra.click(InfrastructurePage.locators.clickableServerGroup);
      infra.click(InfrastructurePage.locators.actionsButton);
      infra.click(InfrastructurePage.locators.cloneMenuItem);
      cloneModal.selectMachineImage('1404-trusty');
      cloneModal.selectMachineInstanceType('Build Custom');
      const cores = cloneModal.getAvailableCustomInstanceCores();
      const memory = cloneModal.getAvailableCustomInstanceMemory();
      expect(cores.length).toBeGreaterThan(2);
      expect(memory.length).toBeGreaterThan(2);
      expect(cores).toContain('1');
      expect(cores).toContain('4');
      expect(cores).toContain('16');
      expect(cores).toContain('32');
      expect(cores).toContain('64');
      expect(cores).toContain('96');
      expect(memory).toContain('1');
      expect(memory).toContain('1.25');
      expect(memory).toContain('4.5');
      expect(memory).toContain('5.75');
      expect(memory).toContain('6.5');
    });
  });
});
