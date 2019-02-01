import { InfrastructurePage } from '../core/pages/InfrastructurePage';
import { CreateServerGroupModalPage } from './pages/CreateServerGroupModalPage';

describe('Create Server Group Modal', () => {
  describe('GPU Accelerators', () => {
    it(`provides different accelerators according to the server group's chosen zone`, () => {
      const infra = new InfrastructurePage();
      const createModal = new CreateServerGroupModalPage();
      infra.openClustersForApplication('compute');
      infra.click(InfrastructurePage.locators.createServerGroupButton);
      createModal.addAccelerator();
      const usCentralAcceleratorNames = createModal.getAcceleratorList();
      createModal.selectRegion('us-east1');
      createModal.selectZone('us-east1-c');
      createModal.addAccelerator();
      const usEastAcceleratorNames = createModal.getAcceleratorList();
      expect(usCentralAcceleratorNames).not.toContain('NVIDIA Tesla K80');
      expect(usEastAcceleratorNames).toContain('NVIDIA Tesla K80');
    });
  });
});
