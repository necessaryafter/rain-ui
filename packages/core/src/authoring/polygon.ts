export interface Polygon {
  readonly __rainPolygon: readonly (readonly [number, number])[];
}

const POINT = /^\s*(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%\s*$/;

export function polygon(...points: string[]): Polygon {
  if (points.length < 3 || points.length > 32) throw new Error("polygon requires 3 to 32 points");
  const coordinates = points.map((point) => {
    const match = POINT.exec(point);
    if (!match) throw new Error(`invalid polygon point: ${point}`);

    const x = Number(match[1]);
    const y = Number(match[2]);
    if (x < 0 || x > 100 || y < 0 || y > 100) throw new Error(`polygon point is outside 0% to 100%: ${point}`);

    return [Math.round(x * 100), Math.round(y * 100)] as const;
  });

  return { __rainPolygon: coordinates };
}
