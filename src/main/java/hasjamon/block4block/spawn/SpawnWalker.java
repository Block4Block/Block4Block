package hasjamon.block4block.spawn;

/**
 * The square spiral of claim cells around a centre cell, as one numbered path: step 0 is the centre, then ring 1 (8
 * cells), ring 2 (16 cells) and so on. Ring r has exactly 8r cells. Walking outward is step + 1, inward step - 1, so a
 * walk back follows the same path in reverse.
 */
public final class SpawnWalker {
    private SpawnWalker() {}

    /** Ring that a step belongs to: ring r starts at step (2r - 1)^2. */
    public static int ringOf(long step) {
        if (step <= 0) return 0;
        int r = (int) Math.ceil((Math.sqrt(step + 1.0) - 1) / 2);
        while ((2L * r - 1) * (2L * r - 1) > step) r--;     // guard against rounding at ring edges
        while ((2L * r + 1) * (2L * r + 1) <= step) r++;
        return r;
    }

    /** Position inside its ring, 0 to 8r - 1. */
    public static int indexOf(long step) {
        int r = ringOf(step);
        return r == 0 ? 0 : (int) (step - (2L * r - 1) * (2L * r - 1));
    }

    /** {dx, dz} of a step relative to the centre cell. Along the north edge, then east, south, west. */
    public static int[] cellOf(long step) {
        int r = ringOf(step);
        if (r == 0) return new int[]{0, 0};
        int i = indexOf(step);
        int side = 2 * r;
        if (i <= side) return new int[]{-r + i, -r};                    // north edge, west to east (2r + 1 cells)
        i -= side;
        if (i <= side) return new int[]{r, -r + i};                     // east edge, north to south
        i -= side;
        if (i <= side) return new int[]{r - i, r};                      // south edge, east to west
        i -= side;
        return new int[]{-r, r - i};                                    // west edge, south to north (stops short of the corner)
    }

    /** The step whose cell is (dx, dz). */
    public static long stepOf(int dx, int dz) {
        int r = Math.max(Math.abs(dx), Math.abs(dz));
        if (r == 0) return 0;
        long base = (2L * r - 1) * (2L * r - 1);
        for (int i = 0; i < 8 * r; i++) {
            int[] c = cellOf(base + i);
            if (c[0] == dx && c[1] == dz) return base + i;
        }
        return 0;
    }

    /** Number of cells in ring r. */
    public static int cellsInRing(int r) {
        return r == 0 ? 1 : 8 * r;
    }
}
