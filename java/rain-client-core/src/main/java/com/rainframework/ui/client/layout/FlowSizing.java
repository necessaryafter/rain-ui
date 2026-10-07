package com.rainframework.ui.client.layout;

final class FlowSizing {
    private FlowSizing() {
    }

    static void grow(int[] sizes, int[] maxima, double[] weights, int available) {
        distribute(sizes, maxima, weights, available, true);
    }

    static void shrink(int[] sizes, int[] minima, double[] weights, int shortage) {
        distribute(sizes, minima, weights, shortage, false);
    }

    private static void distribute(
            int[] sizes,
            int[] limits,
            double[] weights,
            int amount,
            boolean growing
    ) {
        var remaining = amount;

        while (remaining > 0) {
            double totalWeight = 0;

            for (int i = 0; i < sizes.length; i++) {
                if (weights[i] > 0 && room(sizes[i], limits[i], growing) > 0) {
                    totalWeight += weights[i];
                }
            }

            if (totalWeight == 0) {
                return;
            }

            var assigned = 0;
            final var shares = new int[sizes.length];

            for (int i = 0; i < sizes.length; i++) {
                if (weights[i] <= 0 || room(sizes[i], limits[i], growing) <= 0) {
                    continue;
                }

                final var proportional = (int) Math.floor(remaining * weights[i] / totalWeight);
                shares[i] = Math.min(proportional, room(sizes[i], limits[i], growing));
                assigned += shares[i];
            }

            var remainder = remaining - assigned;

            for (int i = 0; i < sizes.length && remainder > 0; i++) {
                if (weights[i] <= 0 || shares[i] >= room(sizes[i], limits[i], growing)) {
                    continue;
                }

                shares[i]++;
                assigned++;
                remainder--;
            }

            if (assigned == 0) {
                return;
            }

            for (int i = 0; i < sizes.length; i++) {
                sizes[i] += growing ? shares[i] : -shares[i];
            }

            remaining -= assigned;
        }
    }

    private static int room(int size, int limit, boolean growing) {
        return Math.max(0, growing ? limit - size : size - limit);
    }
}
