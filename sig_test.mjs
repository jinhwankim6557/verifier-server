import { p256 } from '@noble/curves/p256';
const msg = new Uint8Array(32);
const priv = p256.utils.randomPrivateKey();
const sig = p256.sign(msg, priv);
console.log("sig.r type:", typeof sig.r);
console.log("Array.from(sig.r):", Array.from(sig.r));
