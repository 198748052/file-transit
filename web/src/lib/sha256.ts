const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

const INIT = new Uint32Array([
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
]);

function rotr(value: number, bits: number): number {
  return (value >>> bits) | (value << (32 - bits));
}

/** Incremental SHA-256, so large files can be hashed without loading them into memory. */
export class Sha256 {
  private state = new Uint32Array(INIT);
  private buffer = new Uint8Array(64);
  private bufferLength = 0;
  private length = 0;
  private finished = false;

  update(data: Uint8Array): this {
    if (this.finished) throw new Error('sha256_already_finished');
    this.length += data.length;
    let offset = 0;

    if (this.bufferLength > 0) {
      const take = Math.min(64 - this.bufferLength, data.length);
      this.buffer.set(data.subarray(0, take), this.bufferLength);
      this.bufferLength += take;
      offset += take;
      if (this.bufferLength === 64) {
        this.transform(this.buffer, 0);
        this.bufferLength = 0;
      }
    }

    while (offset + 64 <= data.length) {
      this.transform(data, offset);
      offset += 64;
    }

    if (offset < data.length) {
      this.buffer.set(data.subarray(offset), 0);
      this.bufferLength = data.length - offset;
    }
    return this;
  }

  digest(): Uint8Array {
    if (this.finished) throw new Error('sha256_already_finished');
    const bitLenHi = Math.floor(this.length / 0x20000000);
    const bitLenLo = (this.length * 8) >>> 0;
    const padLength = this.bufferLength < 56 ? 56 - this.bufferLength : 120 - this.bufferLength;
    const final = new Uint8Array(padLength + 8);
    final[0] = 0x80;
    final[final.length - 8] = (bitLenHi >>> 24) & 0xff;
    final[final.length - 7] = (bitLenHi >>> 16) & 0xff;
    final[final.length - 6] = (bitLenHi >>> 8) & 0xff;
    final[final.length - 5] = bitLenHi & 0xff;
    final[final.length - 4] = (bitLenLo >>> 24) & 0xff;
    final[final.length - 3] = (bitLenLo >>> 16) & 0xff;
    final[final.length - 2] = (bitLenLo >>> 8) & 0xff;
    final[final.length - 1] = bitLenLo & 0xff;
    this.update(final);
    this.finished = true;

    const out = new Uint8Array(32);
    for (let i = 0; i < 8; i++) {
      const value = this.state[i]!;
      out[i * 4] = (value >>> 24) & 0xff;
      out[i * 4 + 1] = (value >>> 16) & 0xff;
      out[i * 4 + 2] = (value >>> 8) & 0xff;
      out[i * 4 + 3] = value & 0xff;
    }
    return out;
  }

  hex(): string {
    const bytes = this.digest();
    let out = '';
    for (const byte of bytes) out += byte.toString(16).padStart(2, '0');
    return out;
  }

  private transform(data: Uint8Array, offset: number): void {
    const w = new Uint32Array(64);
    for (let i = 0; i < 16; i++) {
      const j = offset + i * 4;
      w[i] = ((data[j]! << 24) | (data[j + 1]! << 16) | (data[j + 2]! << 8) | data[j + 3]!) >>> 0;
    }
    for (let i = 16; i < 64; i++) {
      const x = w[i - 15]!;
      const y = w[i - 2]!;
      const s0 = rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3);
      const s1 = rotr(y, 17) ^ rotr(y, 19) ^ (y >>> 10);
      w[i] = (w[i - 16]! + s0 + w[i - 7]! + s1) >>> 0;
    }

    let a = this.state[0]!;
    let b = this.state[1]!;
    let c = this.state[2]!;
    let d = this.state[3]!;
    let e = this.state[4]!;
    let f = this.state[5]!;
    let g = this.state[6]!;
    let h = this.state[7]!;

    for (let i = 0; i < 64; i++) {
      const s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + s1 + ch + K[i]! + w[i]!) >>> 0;
      const s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (s0 + maj) >>> 0;
      h = g;
      g = f;
      f = e;
      e = (d + t1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) >>> 0;
    }

    this.state[0] = (this.state[0]! + a) >>> 0;
    this.state[1] = (this.state[1]! + b) >>> 0;
    this.state[2] = (this.state[2]! + c) >>> 0;
    this.state[3] = (this.state[3]! + d) >>> 0;
    this.state[4] = (this.state[4]! + e) >>> 0;
    this.state[5] = (this.state[5]! + f) >>> 0;
    this.state[6] = (this.state[6]! + g) >>> 0;
    this.state[7] = (this.state[7]! + h) >>> 0;
  }
}
