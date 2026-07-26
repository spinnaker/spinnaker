import { GceImageReader } from './image.reader';

describe('GceImageReader', () => {
  it('returns a native Promise that resolves to null for regional image lookups', async () => {
    const result = GceImageReader.getImage();

    expect(result instanceof Promise).toBe(true);
    await expectAsync(result).toBeResolvedTo(null);
  });
});
