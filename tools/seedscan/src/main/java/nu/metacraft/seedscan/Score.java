package nu.metacraft.seedscan;

/** Score terms. Everything is weighted by stage: start 1.0, stage 2 0.6, stage 3 0.25. */
final class Score {
    /** stage[c] = first stage (0..2) in which category c is present, 3 = not within stage 3. */
    static double biomes(int[] stage) {
        double score = 0;
        for (Cat c : Cat.ALL) {
            score += c.weight * Cat.STAGE_FACTOR[stage[c.ordinal()]];
        }
        return score;
    }

    /** Ocean share of the start area and of stage 2. Penalised above 25% (start) and 40% (stage 2). */
    static double ocean(double start, double stage2) {
        return -8 * Math.max(0, start - 0.25) - 4 * Math.max(0, stage2 - 0.40);
    }

    /** Chebyshev distance from the centre to the nearest stronghold. */
    static double stronghold(double d) {
        if (d <= 500) {
            return 1.5;
        } else if (d <= 750) {
            return 0.9;
        } else if (d <= 1000) {
            return 0.4;
        } else if (d <= 1500) {
            return 0.15;
        }
        return 0;
    }
}
