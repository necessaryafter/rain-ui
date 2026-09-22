export function PriceTag({ price }: { price: string | { $bind: string } }) {
  return <text value={price} />;
}
