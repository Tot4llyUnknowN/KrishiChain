package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Quick Sort (in place, not stable) and Merge Sort (stable), plus ready-made comparators. */
public final class Sorts {
    private static final Random RNG = new Random(42);

    private Sorts() { }

    // ---------------- Quick Sort ----------------

    /** In-place randomized Quick Sort. Average O(n log n), worst O(n^2) (very unlikely). */
    public static <T> void quickSort(List<T> list, Comparator<? super T> cmp) {
        quickSort(list, cmp, 0, list.size() - 1);
    }

    private static <T> void quickSort(List<T> a, Comparator<? super T> cmp, int lo, int hi) {
        // Recurse on the smaller side, loop on the larger: stack depth stays O(log n).
        while (lo < hi) {
            int p = partition(a, cmp, lo, hi);
            if (p - lo < hi - p) {
                quickSort(a, cmp, lo, p - 1);
                lo = p + 1;
            } else {
                quickSort(a, cmp, p + 1, hi);
                hi = p - 1;
            }
        }
    }

    private static <T> int partition(List<T> a, Comparator<? super T> cmp, int lo, int hi) {
        swap(a, lo + RNG.nextInt(hi - lo + 1), hi); // random pivot moved to the end
        T pivot = a.get(hi);
        int i = lo;
        for (int j = lo; j < hi; j++) {
            if (cmp.compare(a.get(j), pivot) < 0) {
                swap(a, i, j);
                i++;
            }
        }
        swap(a, i, hi);
        return i;
    }

    private static <T> void swap(List<T> a, int i, int j) {
        T tmp = a.get(i);
        a.set(i, a.get(j));
        a.set(j, tmp);
    }

    // ---------------- Merge Sort ----------------

    /** Stable Merge Sort. Always O(n log n), O(n) extra space. Sorts the list in place. */
    public static <T> void mergeSort(List<T> list, Comparator<? super T> cmp) {
        if (list.size() < 2) {
            return;
        }
        List<T> copy = new ArrayList<>(list);
        mergeSort(copy, list, 0, list.size() - 1, cmp);
    }

    private static <T> void mergeSort(List<T> src, List<T> dst, int lo, int hi,
                                      Comparator<? super T> cmp) {
        if (lo >= hi) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(src, dst, lo, mid, cmp);
        mergeSort(src, dst, mid + 1, hi, cmp);
        merge(src, dst, lo, mid, hi, cmp);
    }

    private static <T> void merge(List<T> src, List<T> dst, int lo, int mid, int hi,
                                  Comparator<? super T> cmp) {
        for (int k = lo; k <= hi; k++) {
            src.set(k, dst.get(k)); // snapshot current range
        }
        int i = lo;
        int j = mid + 1;
        int k = lo;
        while (i <= mid && j <= hi) {
            // "<= 0" takes the LEFT element on ties: this is what makes it stable.
            if (cmp.compare(src.get(i), src.get(j)) <= 0) {
                dst.set(k++, src.get(i++));
            } else {
                dst.set(k++, src.get(j++));
            }
        }
        while (i <= mid) {
            dst.set(k++, src.get(i++));
        }
        while (j <= hi) {
            dst.set(k++, src.get(j++));
        }
    }

    // ---------------- Comparators ----------------

    /** Highest net profit first. */
    public static Comparator<MarketOption> byNetProfitDesc() {
        return new Comparator<MarketOption>() {
            @Override
            public int compare(MarketOption a, MarketOption b) {
                return Double.compare(b.netProfit, a.netProfit);
            }
        };
    }

    /** Crop name A-Z, then price low to high, then oldest date first. */
    public static Comparator<PriceReport> byCropPriceDate() {
        return new Comparator<PriceReport>() {
            @Override
            public int compare(PriceReport a, PriceReport b) {
                int c = a.crop.compareTo(b.crop);
                if (c != 0) {
                    return c;
                }
                c = Double.compare(a.price, b.price);
                if (c != 0) {
                    return c;
                }
                return Long.compare(a.dateMillis, b.dateMillis);
            }
        };
    }

    /** Crop only. Used to demonstrate stability. */
    public static Comparator<PriceReport> byCropOnly() {
        return new Comparator<PriceReport>() {
            @Override
            public int compare(PriceReport a, PriceReport b) {
                return a.crop.compareTo(b.crop);
            }
        };
    }
}