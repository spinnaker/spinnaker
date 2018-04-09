import { isString, range } from 'lodash';
import { UUIDGenerator } from './uuid.service';

describe('UUID Service', () => {
  describe('verify uuid generation format', () => {
    let uuid: string;
    beforeEach(() => {
      uuid = UUIDGenerator.generateUuid();
    });

    it('should generate a non-empty string', () => {
      expect(uuid).toBeTruthy();
      expect(isString(uuid)).toBe(true);
      expect(uuid.length > 0).toBe(true);
    });

    it('should have a length of 36 characters', () => {
      expect(uuid.length).toBe(36);
    });

    it('should have the number 4 as the 15th character', () => {
      expect(uuid[14]).toBe('4');
    });

    it('should have an "8", "9", "a", or "b" as the 20th character', () => {
      const chars: string[] = ['8', '9', 'a', 'b'];
      expect(chars.includes(uuid[19])).toBe(true);
    });

    it('should have a "-" as the 9th, 14th, 19th, and 24th characters', () => {
      expect(uuid[8]).toBe('-');
      expect(uuid[13]).toBe('-');
      expect(uuid[18]).toBe('-');
      expect(uuid[23]).toBe('-');
    });

    it('should have a hex character everywhere else', () => {
      const hexPos: number[] = [...range(0, 8), ...range(9, 13), ...range(15, 18), ...range(20, 23), ...range(24, 36)];
      const chars: string[] = [...[...range(0, 10)].map(String), 'a', 'b', 'c', 'd', 'e', 'f'];
      hexPos.forEach((pos: number) => {
        expect(chars.includes(uuid[pos])).toBe(true);
      });
    });
  });
});
