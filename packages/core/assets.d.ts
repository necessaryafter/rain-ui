// Asset imports are turned into AssetRefs by rain build; these declarations let the editor type them. This file has no
// top-level import on purpose: a wildcard module declaration only works in a global declaration file.

declare module "*.png" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}

declare module "*.jpg" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}

declare module "*.jpeg" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}

declare module "*.gif" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}

declare module "*.ttf" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}

declare module "*.otf" {
  const asset: import("./src/types").AssetRef;
  export default asset;
}
