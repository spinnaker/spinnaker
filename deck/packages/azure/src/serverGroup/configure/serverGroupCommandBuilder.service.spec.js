import { AzureImageReader } from '../../image/image.reader';
import { AzureServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

describe('AzureServerGroupCommandBuilder', function () {
  function serverGroup(overrides = {}) {
    return {
      name: 'fnord-web-v001',
      account: 'test',
      region: 'westus',
      loadBalancers: [],
      selectedVnetSubnets: [],
      selectedVnet: { name: 'vnet-a' },
      securityGroups: [],
      zones: [],
      dataDisks: [],
      sku: { name: 'Standard_DS1_v2', capacity: 1 },
      capacity: { min: 1, max: 1, desired: 1 },
      ...overrides,
    };
  }

  it('builds clone commands with image options and the current marketplace image selected', async function () {
    const images = [
      { imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } },
      { imageName: 'ubuntu-east', amis: { eastus: ['ami-east'] } },
    ];
    spyOn(AzureImageReader.prototype, 'findImages').and.returnValue(Promise.resolve(images));

    const command = await new AzureServerGroupCommandBuilder(null).buildServerGroupCommandFromExisting(
      { name: 'fnord' },
      serverGroup({ image: { imageName: 'ubuntu-west' } }),
    );

    expect(AzureImageReader.prototype.findImages).toHaveBeenCalledWith({ provider: 'azure' });
    expect(command.images).toBe(images);
    expect(command.imageName).toBe('ubuntu-west');
    expect(command.selectedImage).toBe(images[0]);
  });

  it('builds clone commands with image options and the current custom image preserved', async function () {
    const images = [{ imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } }];
    spyOn(AzureImageReader.prototype, 'findImages').and.returnValue(Promise.resolve(images));

    const command = await new AzureServerGroupCommandBuilder(null).buildServerGroupCommandFromExisting(
      { name: 'fnord' },
      serverGroup({
        image: { isCustom: true, imageName: 'custom-image', ostype: 'Linux', uri: 'https://image.vhd' },
      }),
    );

    expect(command.images).toBe(images);
    expect(command.image).toEqual({
      isCustom: true,
      imageName: 'custom-image',
      ostype: 'Linux',
      uri: 'https://image.vhd',
      region: 'westus',
    });
    expect(command.selectedImage).toBeUndefined();
  });
});
