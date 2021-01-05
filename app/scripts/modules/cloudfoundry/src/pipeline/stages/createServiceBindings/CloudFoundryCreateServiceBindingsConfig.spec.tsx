import { validateServiceBindingRequests, ServiceBindingRequests } from './CloudFoundryCreateServiceBindingsConfig';

describe('Cloud Foundry Create Service Bindings Config', () => {
  describe('validate Service Binding Requests', () => {
    it('should return false when the array is empty', () => {
      const bindingRequests: ServiceBindingRequests[] = [];
      expect(validateServiceBindingRequests(bindingRequests)).toBe(false);
    });

    it('should return false when at least one serviceInstanceName is null', () => {
      const bindingRequests: ServiceBindingRequests[] = [
        { serviceInstanceName: 'instanceName' },
        { serviceInstanceName: null },
      ];
      expect(validateServiceBindingRequests(bindingRequests)).toBe(false);
    });

    it('should return false when at least one serviceInstanceName is empty', () => {
      const bindingRequests: ServiceBindingRequests[] = [
        { serviceInstanceName: 'instanceName' },
        { serviceInstanceName: '' },
      ];
      expect(validateServiceBindingRequests(bindingRequests)).toBe(false);
    });

    it('should return true when all serviceInstanceName are not empty', () => {
      const bindingRequests: ServiceBindingRequests[] = [
        { serviceInstanceName: 'instanceName' },
        { serviceInstanceName: 'instanceName2' },
      ];
      expect(validateServiceBindingRequests(bindingRequests)).toBe(true);
    });
  });
});
