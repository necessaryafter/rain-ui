export const version = "1.0.0";

export { t, defineProperties, defineActions, defineScreen, isScreenDefinition } from "./authoring/builder";
export type { ScreenDefinition } from "./authoring/builder";

export { createPropertyProxy, createActionProxy, inputValue } from "./authoring/binding";
export type { Binding, ActionRef, InputValue } from "./authoring/binding";

export { validateContract } from "./validate";
export type { ValidationResult } from "./validate";

export { compileScreen } from "./contract/serialize";
export type { CompiledScreen } from "./contract/serialize";

export type { AssetInfo, AssetRef, AssetType, ComponentNode, Contract, TypeSchema } from "./contract/types";
export { polygon } from "./authoring/polygon";
export type { Polygon } from "./authoring/polygon";

export { resolvePath } from "./contract/path";
export { findUnusedProperties } from "./contract/unused";
export type { ResolvedPath } from "./contract/path";

export type { ValidationErrorCode } from "./validation/shared";
export { Limits } from "./validation/limits";
