export const version = "1.0.0";

// Re-export all M1 Phase B implementation
export { t, defineProperties, defineActions, defineScreen, isScreenDefinition } from "./builder";
export type { ScreenDefinition } from "./builder";

export { createPropertyProxy, createActionProxy } from "./binding";
export type { Binding, ActionRef } from "./binding";

export { validateContract } from "./validate";
export type { ValidationResult } from "./validate";

export { compileScreen } from "./serialize";
export type { CompiledScreen } from "./serialize";

export type { AssetInfo, AssetRef, AssetType, ComponentNode, Contract, TypeSchema } from "./types";

export { resolvePath } from "./path";
export { findUnusedProperties } from "./unused";
export type { ResolvedPath } from "./path";

// Validation error codes
export type ValidationErrorCode =
    | "UNKNOWN_SCHEMA_VERSION"
    | "UNKNOWN_COMPONENT"
    | "UNKNOWN_PROP"
    | "INVALID_PROP_TYPE"
    | "UNDECLARED_BINDING"
    | "BINDING_TYPE_MISMATCH"
    | "UNDECLARED_ACTION"
    | "PAYLOAD_SCHEMA_MISMATCH"
    | "DUPLICATE_SCREEN_ID"
    | "INVALID_ID"
    | "LIMIT_EXCEEDED"
    | "INVALID_DEFAULT"
    | "MISPLACED_COMPONENT"
    | "DUPLICATE_CASE"
    | "UNDECLARED_ASSET"
    | "ASSET_TYPE_MISMATCH"
    | "UNSUPPORTED_ASSET"
    | "INVALID_ASSET_HASH";

// Limit constants (per spec §5.5)
export const Limits = {
    MAX_CONTRACT_BYTES: 256 * 1024,
    MAX_NODES: 2000,
    MAX_DEPTH: 32,
    MAX_ACTIONS: 256,
    MAX_STRING_LENGTH: 1024,
    MAX_PROPERTIES_BYTES: 256 * 1024,
    MAX_LIST_ELEMENTS: 1000,
    MAX_PAYLOAD_BYTES: 8 * 1024,
    MAX_PAYLOAD_DEPTH: 8,
    MAX_JSON_DEPTH: 128,
    MAX_ASSET_BYTES: 8 * 1024 * 1024,
    MAX_ASSETS: 256,
    MAX_TOTAL_ASSET_BYTES: 64 * 1024 * 1024,
    MAX_IMAGE_DIMENSION: 4096,
    MAX_GIF_FRAMES: 512,
    MAX_GIF_DECODED_BYTES: 128 * 1024 * 1024,
};
