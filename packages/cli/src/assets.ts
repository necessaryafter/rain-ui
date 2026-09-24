import * as crypto from "crypto";
import * as fs from "fs";

import { Limits, type AssetInfo, type AssetRef, type AssetType } from "@rain-ui/core";

type AssetErrorCode = "UNSUPPORTED_ASSET" | "LIMIT_EXCEEDED";

export class AssetError extends Error {
  constructor(
    readonly code: AssetErrorCode,
    readonly file: string,
    detail: string,
  ) {
    super(`${code}: ${file}: ${detail}`);
  }
}

// The files behind every asset imported in this process, by hash, so the build can copy them into dist/assets.
export const assetFiles = new Map<string, string>();

type Detected = Omit<AssetInfo, "bytes">;

// The type comes from the file's content, not its extension, the way a browser sniffs an image; the extension only
// routes the import here.
export function readAsset(file: string): AssetRef {
  if (fs.statSync(file).size > Limits.MAX_ASSET_BYTES) {
    throw new AssetError("LIMIT_EXCEEDED", file, `larger than ${Limits.MAX_ASSET_BYTES} bytes`);
  }

  const bytes = fs.readFileSync(file);
  const detected = detect(bytes);
  if (!detected) {
    throw new AssetError("UNSUPPORTED_ASSET", file, "not a readable PNG, JPEG, GIF, TTF or OTF file");
  }

  checkLimits(detected, file);

  const hash = crypto.createHash("sha256").update(bytes).digest("hex");
  assetFiles.set(hash, file);

  return { $asset: hash, ...detected, bytes: bytes.length };
}

function checkLimits(asset: Detected, file: string): void {
  const { width = 0, height = 0, frames = 1 } = asset;

  if (width > Limits.MAX_IMAGE_DIMENSION || height > Limits.MAX_IMAGE_DIMENSION) {
    throw new AssetError("LIMIT_EXCEEDED", file, `${width}x${height} is over ${Limits.MAX_IMAGE_DIMENSION} px per side`);
  }

  if (frames > Limits.MAX_GIF_FRAMES) {
    throw new AssetError("LIMIT_EXCEEDED", file, `${frames} frames is over ${Limits.MAX_GIF_FRAMES}`);
  }

  if (width * height * 4 * frames > Limits.MAX_GIF_DECODED_BYTES) {
    throw new AssetError("LIMIT_EXCEEDED", file, `decodes to more than ${Limits.MAX_GIF_DECODED_BYTES} bytes`);
  }
}

function detect(bytes: Buffer): Detected | undefined {
  return readPng(bytes) ?? readJpeg(bytes) ?? readGif(bytes) ?? readFont(bytes);
}

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// The first chunk of a PNG is always IHDR, which starts with the width and height.
function readPng(bytes: Buffer): Detected | undefined {
  if (bytes.length < 24 || !bytes.subarray(0, 8).equals(PNG_SIGNATURE)) return undefined;
  if (bytes.toString("ascii", 12, 16) !== "IHDR") return undefined;

  return image("image/png", bytes.readUInt32BE(16), bytes.readUInt32BE(20));
}

// Walks the marker segments until a start-of-frame, which carries the dimensions. C4, C8 and CC share the SOF range
// but are other segments (Huffman tables, reserved, arithmetic coding).
function readJpeg(bytes: Buffer): Detected | undefined {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return undefined;

  let offset = 2;
  while (offset + 4 <= bytes.length) {
    if (bytes[offset] !== 0xff) return undefined;

    const marker = bytes[offset + 1];
    if (marker === 0xff) {
      offset++;
      continue;
    }

    const isStandalone = marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8);
    if (isStandalone) {
      offset += 2;
      continue;
    }

    const isStartOfFrame = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isStartOfFrame) {
      if (offset + 9 > bytes.length) return undefined;

      return image("image/jpeg", bytes.readUInt16BE(offset + 7), bytes.readUInt16BE(offset + 5));
    }

    // Image data starts at start-of-scan, so a JPEG without a frame header before it is not usable.
    if (marker === 0xda) return undefined;

    offset += 2 + bytes.readUInt16BE(offset + 2);
  }

  return undefined;
}

// Counts frames by walking the blocks to the trailer: one image descriptor (0x2C) per frame, extensions (0x21) and
// image data skipped as sub-block chains.
function readGif(bytes: Buffer): Detected | undefined {
  const version = bytes.toString("ascii", 0, 6);
  if (bytes.length < 13 || (version !== "GIF87a" && version !== "GIF89a")) return undefined;

  const screen = image("image/gif", bytes.readUInt16LE(6), bytes.readUInt16LE(8));
  if (!screen) return undefined;

  let offset = 13 + colorTableSize(bytes[10]);
  let frames = 0;

  while (offset < bytes.length) {
    switch (bytes[offset]) {
      case 0x3b:
        return frames > 0 ? { ...screen, frames } : undefined;
      case 0x2c:
        if (offset + 10 > bytes.length) return undefined;

        frames++;
        offset += 10 + colorTableSize(bytes[offset + 9]) + 1;
        break;
      case 0x21:
        offset += 2;
        break;
      default:
        return undefined;
    }

    const next = skipSubBlocks(bytes, offset);
    if (next === undefined) return undefined;

    offset = next;
  }

  return undefined;
}

function colorTableSize(flags: number): number {
  return flags & 0x80 ? 3 * 2 ** ((flags & 0x07) + 1) : 0;
}

function skipSubBlocks(bytes: Buffer, offset: number): number | undefined {
  while (offset < bytes.length) {
    const size = bytes[offset];
    if (size === 0) return offset + 1;

    offset += size + 1;
  }

  return undefined;
}

// An sfnt font starts with its version tag: 0x00010000 or "true" for TrueType outlines, "OTTO" for CFF.
function readFont(bytes: Buffer): Detected | undefined {
  if (bytes.length < 12) return undefined;

  const tag = bytes.toString("latin1", 0, 4);
  if (tag === "OTTO") return { type: "font/otf" };
  if (tag === "true" || bytes.readUInt32BE(0) === 0x00010000) return { type: "font/ttf" };

  return undefined;
}

function image(type: AssetType, width: number, height: number): Detected | undefined {
  if (width === 0 || height === 0) return undefined;

  return { type, width, height };
}
