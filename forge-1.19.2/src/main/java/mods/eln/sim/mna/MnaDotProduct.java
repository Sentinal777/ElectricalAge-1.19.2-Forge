package mods.eln.sim.mna;

import org.apache.commons.numbers.core.DD;

/** Ordered double-double products without allocating intermediate DD objects. */
final class MnaDotProduct {
    private MnaDotProduct() {}

    static double sum(DD[] row, double[] rhs) {
        return sum(row, rhs, isSafeRow(row));
    }

    static boolean isSafeRow(DD[] row) {
        for (DD value : row) {
            double magnitude = Math.abs(value.hi());
            if (!Double.isFinite(magnitude) || !Double.isFinite(value.lo()) ||
                    magnitude > 0x1.0p450 || (magnitude != 0.0 && magnitude < 0x1.0p-450)) return false;
        }
        return true;
    }

    static double sum(DD[] row, double[] rhs, boolean safeRow) {
        if (!safeRow) return legacySum(row, rhs);
        double high = 0.0;
        double low = 0.0;
        for (int i = 0; i < row.length; i++) {
            double b = rhs[i];
            if (b == 0.0) continue;
            double magnitudeB = Math.abs(b);
            if (!Double.isFinite(b) || magnitudeB > 0x1.0p450 ||
                    (magnitudeB != 0.0 && magnitudeB < 0x1.0p-450)) {
                return legacySum(row, rhs);
            }
            double a = row[i].hi();
            double aa = row[i].lo();

            double product = a * b;
            double residual = Math.fma(a, b, -product) + aa * b;
            double productHigh = product + residual;
            double productLow = residual - (productHigh - product);

            // Two-sum and the same two-stage normalization used by Commons Numbers DD.add.
            double leading = high + productHigh;
            double leadingError = sumError(high, productHigh, leading);
            double trailing = low + productLow;
            double trailingError = sumError(low, productLow, trailing);
            double correction = leadingError + trailing;
            double middleHigh = leading + correction;
            double middleLow = correction - (middleHigh - leading);
            double last = middleLow + trailingError;
            high = middleHigh + last;
            low = last - (high - middleHigh);
            if (!Double.isFinite(high) || !Double.isFinite(low)) return legacySum(row, rhs);
        }
        return high + low;
    }

    private static double sumError(double a, double b, double sum) {
        double virtualB = sum - a;
        return (a - (sum - virtualB)) + (b - virtualB);
    }

    private static double legacySum(DD[] row, double[] rhs) {
        DD result = DD.ZERO;
        for (int i = 0; i < row.length; i++) result = result.add(row[i].multiply(rhs[i]));
        return result.doubleValue();
    }
}
