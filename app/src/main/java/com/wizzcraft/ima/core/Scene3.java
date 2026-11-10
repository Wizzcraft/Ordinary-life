package com.wizzcraft.ima.core;

/**
 * A small software 3D renderer that draws through {@link Gfx}: flat-shaded convex polygons, painter's
 * ordering, no depth buffer. The world is built once (items made of faces); every frame the visible
 * part is culled, ordered, lit, clipped and projected into flat arrays, and then flushed to the
 * surface. Nothing is allocated after {@link #freeze()}.
 *
 * Ordering. Three layers are drawn in turn: FAR (the skyline: sorted by distance), GROUND (flat
 * things at height zero, drawn in the order they were built, because the camera is always above the
 * ground so nothing on it can hide anything standing on it), and OBJECT. Objects are "items": small
 * convex clusters with a tight axis-aligned box. Two items whose boxes do not overlap are separated
 * by an axis-aligned plane, and the one on the eye's side of that plane can never be hidden by the
 * other. That relation is exact, so it does not pop as the camera moves: it only flips at the moment
 * the eye crosses the separating plane, when neither can hide the other. The frame's order is a
 * topological sort of that relation over the items whose screen bounds overlap. Inside one item the
 * faces are drawn in building order after back-face culling, which is right for a convex shape with
 * flat decals on its faces; anything that sticks out far enough to matter is its own item.
 *
 * Seams. Anti-aliased polygon fills leave hairlines where two polygons share an edge, so every opaque
 * polygon is grown by a quarter of a unit in screen space.
 */
final class Scene3 {
    /** Receives the two callbacks that let the owner mix live geometry and 2D sprites into a frame. */
    interface Painter {
        /** A CUSTOM item's turn in the depth order: add world polygons, discs, lines or sprites. */
        void emitItem(int tag);

        /** Called from {@link Scene3#flush}: draw sprite {@code id} with its feet at (sx, sy). */
        void paintSprite(Gfx c, int id, float sx, float sy, float scale);
    }

    static final int FAR = 0, GROUND = 1, OBJECT = 2;
    static final int MESH = 0, CUSTOM = 1;
    /** Face flags. */
    static final int TWO_SIDED = 1, UNLIT = 2, NOFOG = 4, NO_GROW = 8;

    private static final int MAXV = 24;
    private static final int MAX_OUT = 9000, MAX_XY = 120000;
    /** Screen-space growth of opaque polygons, in units. */
    private static final float GROW = 0.25f;

    // ---- Static geometry -------------------------------------------------------------------
    float[] vx = new float[4096], vy = new float[4096], vz = new float[4096];
    int nv;
    int[] fStart = new int[1024], fCol = new int[1024];
    byte[] fCnt = new byte[1024], fFlag = new byte[1024];
    short[] fSlot = new short[1024];
    float[] fnx = new float[1024], fny = new float[1024], fnz = new float[1024];
    float[] fcx = new float[1024], fcy = new float[1024], fcz = new float[1024];
    float[] fLod = new float[1024];
    int nf;

    int[] iF0 = new int[256], iF1 = new int[256], iTag = new int[256];
    byte[] iKind = new byte[256], iLayer = new byte[256];
    boolean[] iDyn = new boolean[256], iHidden = new boolean[256];
    float[] aMinX = new float[256], aMinY = new float[256], aMinZ = new float[256];
    float[] aMaxX = new float[256], aMaxY = new float[256], aMaxZ = new float[256];
    float[] iLod = new float[256];
    float[] iTx = new float[256], iTy = new float[256], iTz = new float[256], iCos = new float[256], iSin = new float[256];
    int ni;

    /** Colours that change at run time (neon, lit windows, open and closed fronts). Slot 0 is unused. */
    final int[] slotCol = new int[256];
    /** 0 = lit like any surface, 1 = glows with its own colour. */
    final float[] slotEmis = new float[256];

    // Build state.
    private int open = -1, curFlags, curSlot;
    private float curLod;

    // ---- Light, set by the owner every frame ---------------------------------------------------
    /** Unit vector towards the sun (or the moon). */
    float sunX = 0.4f, sunY = 0.8f, sunZ = 0.45f;
    float sunR = 0.6f, sunG = 0.58f, sunB = 0.5f;
    float skyR = 0.55f, skyG = 0.6f, skyB = 0.7f;
    float gndR = 0.4f, gndG = 0.38f, gndB = 0.36f;
    float fillR = 0.1f, fillG = 0.1f, fillB = 0.12f;
    float fogR = 200, fogG = 215, fogB = 235, fogNear = 150, fogFar = 1100, fogMax = 0.92f;

    // ---- Per-frame scratch -----------------------------------------------------------------
    private Cam3 cam;
    private Painter painter;
    private float ex, ey, ez, rx, ry, rz, ux, uy, uz, fx, fy, fz, focal, pcx, pcy, tl, tr, tt, tb;
    private float fillX, fillY, fillZ;

    private int[] layerFar, layerGround, layerObj;
    private int[] vis, ord, stk, stkQ;
    private byte[] state;
    private float[] key, bx0, by0, bx1, by1, bMinD, bMaxD;
    private int nVis, nOrd;

    private final float[] ca = new float[MAXV * 3], cb = new float[MAXV * 3];
    private final float[] tX = new float[MAXV], tY = new float[MAXV], tZ = new float[MAXV];
    private final float[] sxy = new float[MAXV * 2], gxy = new float[MAXV * 2];

    // ---- Output: flat arrays, one entry per primitive ------------------------------------------
    /** Entry kinds stored in oCnt: a count >= 3 is a polygon; the others are below. */
    static final int CIRCLE = -1, LINE = -2, SPRITE = -3;
    final float[] oXY = new float[MAX_XY];
    final int[] oOff = new int[MAX_OUT], oCnt = new int[MAX_OUT], oCol = new int[MAX_OUT];
    int nOut, nXY;
    private final float[][] polyBuf = new float[MAXV + 1][];

    /** Counters of the last frame. */
    int statPolys, statItems, statFaces, statDropped;

    Scene3() {
        for (int n = 3; n <= MAXV; n++) polyBuf[n] = new float[n * 2];
    }

    // =========================================================================================
    // Building
    // =========================================================================================

    /** Starts a mesh item in a layer; faces added until {@link #end()} belong to it. */
    int begin(int layer) {
        if (open >= 0) throw new IllegalStateException("item still open");
        growItems();
        int it = ni++;
        iF0[it] = nf;
        iKind[it] = MESH;
        iLayer[it] = (byte) layer;
        open = it;
        curFlags = 0;
        curSlot = 0;
        curLod = 0;
        return it;
    }

    /** Closes the open item and computes its bounds from its faces. */
    int end() {
        int it = open;
        if (it < 0) throw new IllegalStateException("no open item");
        iF1[it] = nf;
        float x0 = Float.MAX_VALUE, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
        for (int f = iF0[it]; f < nf; f++) {
            for (int k = fStart[f], e = k + fCnt[f]; k < e; k++) {
                x0 = Math.min(x0, vx[k]);
                y0 = Math.min(y0, vy[k]);
                z0 = Math.min(z0, vz[k]);
                x1 = Math.max(x1, vx[k]);
                y1 = Math.max(y1, vy[k]);
                z1 = Math.max(z1, vz[k]);
            }
        }
        if (iF0[it] == nf) x0 = y0 = z0 = x1 = y1 = z1 = 0;
        bounds(it, x0, y0, z0, x1, y1, z1);
        open = -1;
        return it;
    }

    /** An item with no stored faces: the painter is asked to emit it when its turn comes. */
    int custom(int tag, float x0, float y0, float z0, float x1, float y1, float z1) {
        if (open >= 0) throw new IllegalStateException("item still open");
        growItems();
        int it = ni++;
        iF0[it] = iF1[it] = nf;
        iKind[it] = CUSTOM;
        iLayer[it] = OBJECT;
        iTag[it] = tag;
        bounds(it, x0, y0, z0, x1, y1, z1);
        return it;
    }

    void bounds(int it, float x0, float y0, float z0, float x1, float y1, float z1) {
        aMinX[it] = x0;
        aMinY[it] = y0;
        aMinZ[it] = z0;
        aMaxX[it] = x1;
        aMaxY[it] = y1;
        aMaxZ[it] = z1;
    }

    /** Makes an item movable: its faces are turned by (cos, sin) about the y axis and shifted. */
    void place(int it, float x, float y, float z, float cos, float sin) {
        iDyn[it] = true;
        iTx[it] = x;
        iTy[it] = y;
        iTz[it] = z;
        iCos[it] = cos;
        iSin[it] = sin;
    }

    /** The item is skipped when farther than this from the eye. */
    void itemLod(float maxDist) {
        iLod[open] = maxDist;
    }

    /** Faces added from now on are skipped when the item is farther than this (0 = never). */
    void lod(float maxDist) {
        curLod = maxDist;
    }

    void flags(int f) {
        curFlags = f;
    }

    /** Faces added from now on take their colour from a run-time slot (0 = their own colour). */
    void slot(int s) {
        curSlot = s;
    }

    private void growItems() {
        if (ni < iF0.length) return;
        int n = ni * 2;
        iF0 = java.util.Arrays.copyOf(iF0, n);
        iF1 = java.util.Arrays.copyOf(iF1, n);
        iTag = java.util.Arrays.copyOf(iTag, n);
        iKind = java.util.Arrays.copyOf(iKind, n);
        iLayer = java.util.Arrays.copyOf(iLayer, n);
        iDyn = java.util.Arrays.copyOf(iDyn, n);
        iHidden = java.util.Arrays.copyOf(iHidden, n);
        aMinX = java.util.Arrays.copyOf(aMinX, n);
        aMinY = java.util.Arrays.copyOf(aMinY, n);
        aMinZ = java.util.Arrays.copyOf(aMinZ, n);
        aMaxX = java.util.Arrays.copyOf(aMaxX, n);
        aMaxY = java.util.Arrays.copyOf(aMaxY, n);
        aMaxZ = java.util.Arrays.copyOf(aMaxZ, n);
        iLod = java.util.Arrays.copyOf(iLod, n);
        iTx = java.util.Arrays.copyOf(iTx, n);
        iTy = java.util.Arrays.copyOf(iTy, n);
        iTz = java.util.Arrays.copyOf(iTz, n);
        iCos = java.util.Arrays.copyOf(iCos, n);
        iSin = java.util.Arrays.copyOf(iSin, n);
    }

    private int newFace(int n, float nx, float ny, float nz, int col) {
        if (open < 0) throw new IllegalStateException("face outside an item");
        if (nf == fStart.length) {
            int m = nf * 2;
            fStart = java.util.Arrays.copyOf(fStart, m);
            fCol = java.util.Arrays.copyOf(fCol, m);
            fCnt = java.util.Arrays.copyOf(fCnt, m);
            fFlag = java.util.Arrays.copyOf(fFlag, m);
            fSlot = java.util.Arrays.copyOf(fSlot, m);
            fnx = java.util.Arrays.copyOf(fnx, m);
            fny = java.util.Arrays.copyOf(fny, m);
            fnz = java.util.Arrays.copyOf(fnz, m);
            fcx = java.util.Arrays.copyOf(fcx, m);
            fcy = java.util.Arrays.copyOf(fcy, m);
            fcz = java.util.Arrays.copyOf(fcz, m);
            fLod = java.util.Arrays.copyOf(fLod, m);
        }
        while (nv + n > vx.length) {
            int m = vx.length * 2;
            vx = java.util.Arrays.copyOf(vx, m);
            vy = java.util.Arrays.copyOf(vy, m);
            vz = java.util.Arrays.copyOf(vz, m);
        }
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) {
            nx = 0;
            ny = 1;
            nz = 0;
            l = 1;
        }
        int f = nf++;
        fStart[f] = nv;
        fCnt[f] = (byte) n;
        fnx[f] = nx / l;
        fny[f] = ny / l;
        fnz[f] = nz / l;
        fCol[f] = col;
        fFlag[f] = (byte) curFlags;
        fSlot[f] = (short) curSlot;
        fLod[f] = curLod;
        return f;
    }

    private void vert(float x, float y, float z) {
        vx[nv] = x;
        vy[nv] = y;
        vz[nv] = z;
        nv++;
    }

    private void closeFace(int f) {
        float sx = 0, sy = 0, sz = 0;
        int n = fCnt[f];
        for (int k = fStart[f], e = k + n; k < e; k++) {
            sx += vx[k];
            sy += vy[k];
            sz += vz[k];
        }
        fcx[f] = sx / n;
        fcy[f] = sy / n;
        fcz[f] = sz / n;
    }

    /** A convex polygon from n points in xyz[0 .. 3n), with an explicit normal. */
    void face(float[] xyz, int n, float nx, float ny, float nz, int col) {
        int f = newFace(n, nx, ny, nz, col);
        for (int i = 0; i < n; i++) vert(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
        closeFace(f);
    }

    void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
              float dx, float dy, float dz, float nx, float ny, float nz, int col) {
        int f = newFace(4, nx, ny, nz, col);
        vert(ax, ay, az);
        vert(bx, by, bz);
        vert(cx, cy, cz);
        vert(dx, dy, dz);
        closeFace(f);
    }

    /** A quad whose normal is taken from its corners; {@code flip} turns it around. */
    void quadN(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
               float dx, float dy, float dz, boolean flip, int col) {
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = dx - ax, e2y = dy - ay, e2z = dz - az;
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        if (flip) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nx, ny, nz, col);
    }

    void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
             float nx, float ny, float nz, int col) {
        int f = newFace(3, nx, ny, nz, col);
        vert(ax, ay, az);
        vert(bx, by, bz);
        vert(cx, cy, cz);
        closeFace(f);
    }

    /** Allocates everything a frame needs. Call once, after the world is built. */
    void freeze() {
        if (open >= 0) throw new IllegalStateException("item still open");
        int nFar = 0, nGr = 0, nOb = 0;
        for (int i = 0; i < ni; i++) {
            if (iLayer[i] == FAR) nFar++;
            else if (iLayer[i] == GROUND) nGr++;
            else nOb++;
        }
        layerFar = new int[nFar];
        layerGround = new int[nGr];
        layerObj = new int[nOb];
        nFar = nGr = nOb = 0;
        for (int i = 0; i < ni; i++) {
            if (iLayer[i] == FAR) layerFar[nFar++] = i;
            else if (iLayer[i] == GROUND) layerGround[nGr++] = i;
            else layerObj[nOb++] = i;
        }
        int m = Math.max(1, Math.max(nOb, nFar));
        vis = new int[m];
        ord = new int[m];
        stk = new int[m];
        stkQ = new int[m];
        key = new float[ni];
        state = new byte[ni];
        bx0 = new float[ni];
        by0 = new float[ni];
        bx1 = new float[ni];
        by1 = new float[ni];
        bMinD = new float[ni];
        bMaxD = new float[ni];
    }

    // =========================================================================================
    // Frame
    // =========================================================================================

    /** Starts a frame: copies the camera and clears the output. */
    void beginFrame(Cam3 c, Painter p) {
        cam = c;
        painter = p;
        ex = c.ex;
        ey = c.ey;
        ez = c.ez;
        rx = c.rx;
        ry = c.ry;
        rz = c.rz;
        ux = c.ux;
        uy = c.uy;
        uz = c.uz;
        fx = c.fx;
        fy = c.fy;
        fz = c.fz;
        focal = c.focal;
        pcx = c.cx;
        pcy = c.cy;
        tl = c.tl;
        tr = c.tr;
        tt = c.tt;
        tb = c.tb;
        // The fill light comes from the side opposite the sun, a little from above.
        float l = (float) Math.sqrt(sunX * sunX + sunZ * sunZ) + 1e-6f;
        fillX = -sunX / l * 0.92f;
        fillZ = -sunZ / l * 0.92f;
        fillY = 0.39f;
        nOut = 0;
        nXY = 0;
        statPolys = statItems = statFaces = statDropped = 0;
    }

    /** True when the item's box is at least partly inside the view frustum. */
    private boolean inFrustum(int it) {
        float x0 = aMinX[it] - ex, y0 = aMinY[it] - ey, z0 = aMinZ[it] - ez;
        float x1 = aMaxX[it] - ex, y1 = aMaxY[it] - ey, z1 = aMaxZ[it] - ez;
        // Near plane: the corner farthest along the view axis must be in front of it.
        if ((fx > 0 ? x1 : x0) * fx + (fy > 0 ? y1 : y0) * fy + (fz > 0 ? z1 : z0) * fz < Cam3.NEAR) return false;
        float nx = rx + fx * tl, ny = ry + fy * tl, nz = rz + fz * tl;
        if ((nx > 0 ? x1 : x0) * nx + (ny > 0 ? y1 : y0) * ny + (nz > 0 ? z1 : z0) * nz < 0) return false;
        nx = fx * tr - rx;
        ny = fy * tr - ry;
        nz = fz * tr - rz;
        if ((nx > 0 ? x1 : x0) * nx + (ny > 0 ? y1 : y0) * ny + (nz > 0 ? z1 : z0) * nz < 0) return false;
        nx = fx * tt - ux;
        ny = fy * tt - uy;
        nz = fz * tt - uz;
        if ((nx > 0 ? x1 : x0) * nx + (ny > 0 ? y1 : y0) * ny + (nz > 0 ? z1 : z0) * nz < 0) return false;
        nx = ux + fx * tb;
        ny = uy + fy * tb;
        nz = uz + fz * tb;
        return (nx > 0 ? x1 : x0) * nx + (ny > 0 ? y1 : y0) * ny + (nz > 0 ? z1 : z0) * nz >= 0;
    }

    /** Distance from the eye to the nearest point of the item's box. */
    private float boxDist(int it) {
        float dx = ex < aMinX[it] ? aMinX[it] - ex : (ex > aMaxX[it] ? ex - aMaxX[it] : 0);
        float dy = ey < aMinY[it] ? aMinY[it] - ey : (ey > aMaxY[it] ? ey - aMaxY[it] : 0);
        float dz = ez < aMinZ[it] ? aMinZ[it] - ez : (ez > aMaxZ[it] ? ez - aMaxZ[it] : 0);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Draws the skyline layer, farthest first. */
    void renderFar() {
        int n = 0;
        for (int it : layerFar) {
            if (iHidden[it] || !inFrustum(it)) continue;
            float cxw = (aMinX[it] + aMaxX[it]) * 0.5f - ex, czw = (aMinZ[it] + aMaxZ[it]) * 0.5f - ez;
            key[it] = cxw * cxw + czw * czw;
            vis[n++] = it;
        }
        sortByKeyDesc(vis, n);
        for (int i = 0; i < n; i++) emitMesh(vis[i], (float) Math.sqrt(key[vis[i]]));
    }

    /** Draws the ground layer in building order. */
    void renderGround() {
        for (int it : layerGround) {
            if (iHidden[it] || !inFrustum(it)) continue;
            float d = boxDist(it);
            if (iLod[it] > 0 && d > iLod[it]) continue;
            emitMesh(it, d);
        }
    }

    /** Draws everything that stands on the ground, in an order that hides what should be hidden. */
    void renderObjects() {
        nVis = 0;
        float vx0 = cam.x0, vy0 = cam.y0, vx1 = cam.x1, vy1 = cam.y1;
        for (int it : layerObj) {
            if (iHidden[it] || !inFrustum(it)) continue;
            // Screen bounds and depth range from the eight corners.
            float minD = Float.MAX_VALUE, maxD = -Float.MAX_VALUE;
            float sx0 = Float.MAX_VALUE, sy0 = sx0, sx1 = -sx0, sy1 = -sx0;
            boolean cut = false;
            for (int k = 0; k < 8; k++) {
                float dx = ((k & 1) == 0 ? aMinX[it] : aMaxX[it]) - ex;
                float dy = ((k & 2) == 0 ? aMinY[it] : aMaxY[it]) - ey;
                float dz = ((k & 4) == 0 ? aMinZ[it] : aMaxZ[it]) - ez;
                float d = dx * fx + dy * fy + dz * fz;
                if (d < minD) minD = d;
                if (d > maxD) maxD = d;
                if (d > Cam3.NEAR) {
                    float kk = focal / d;
                    float px = pcx + (dx * rx + dy * ry + dz * rz) * kk;
                    float py = pcy - (dx * ux + dy * uy + dz * uz) * kk;
                    if (px < sx0) sx0 = px;
                    if (px > sx1) sx1 = px;
                    if (py < sy0) sy0 = py;
                    if (py > sy1) sy1 = py;
                } else {
                    cut = true;
                }
            }
            if (cut) {
                sx0 = vx0;
                sy0 = vy0;
                sx1 = vx1;
                sy1 = vy1;
            } else if (sx1 < vx0 || sx0 > vx1 || sy1 < vy0 || sy0 > vy1) {
                continue;
            }
            if (iLod[it] > 0 && minD > iLod[it]) continue;
            bx0[it] = sx0;
            by0[it] = sy0;
            bx1[it] = sx1;
            by1[it] = sy1;
            bMinD[it] = minD;
            bMaxD[it] = maxD;
            key[it] = maxD;
            state[it] = 0;
            vis[nVis++] = it;
        }
        sortByKeyDesc(vis, nVis);

        // Topological order: an item is emitted only after every visible item that it may hide.
        nOrd = 0;
        for (int p = 0; p < nVis; p++) {
            int first = vis[p];
            if (state[first] != 0) continue;
            int sp = 0;
            stk[0] = first;
            stkQ[0] = p + 1;
            state[first] = 1;
            while (sp >= 0) {
                int n = stk[sp];
                float nMin = bMinD[n];
                boolean pushed = false;
                for (int q = stkQ[sp]; q < nVis; q++) {
                    int j = vis[q];
                    // Sorted by farthest depth: from here on everything lies wholly in front of n.
                    if (bMaxD[j] <= nMin) break;
                    if (state[j] != 0) continue;
                    if (bx1[j] <= bx0[n] || bx0[j] >= bx1[n] || by1[j] <= by0[n] || by0[j] >= by1[n]) continue;
                    if (!behind(j, n)) continue;
                    stkQ[sp] = q + 1;
                    sp++;
                    stk[sp] = j;
                    stkQ[sp] = p + 1;
                    state[j] = 1;
                    pushed = true;
                    break;
                }
                if (!pushed) {
                    ord[nOrd++] = n;
                    state[n] = 2;
                    sp--;
                }
            }
        }

        statItems = nOrd;
        for (int i = 0; i < nOrd; i++) {
            int it = ord[i];
            if (iKind[it] == CUSTOM) painter.emitItem(iTag[it]);
            else emitMesh(it, bMinD[it]);
        }
    }

    /**
     * True when item j must be drawn before item n: an axis-aligned plane separates their boxes and
     * the eye is on n's side of it, so n may hide j and j can never hide n.
     */
    private boolean behind(int j, int n) {
        final float e = 0.02f;
        if (aMaxX[j] <= aMinX[n] + e && ex > aMaxX[j]) return true;
        if (aMinX[j] >= aMaxX[n] - e && ex < aMinX[j]) return true;
        if (aMaxZ[j] <= aMinZ[n] + e && ez > aMaxZ[j]) return true;
        if (aMinZ[j] >= aMaxZ[n] - e && ez < aMinZ[j]) return true;
        if (aMaxY[j] <= aMinY[n] + e && ey > aMaxY[j]) return true;
        return aMinY[j] >= aMaxY[n] - e && ey < aMinY[j];
    }

    /** Shell sort of item indices by key, largest first. In place, no allocation. */
    private void sortByKeyDesc(int[] a, int n) {
        for (int gap = n / 2; gap > 0; gap = gap == 2 ? 1 : (int) (gap / 2.2f)) {
            for (int i = gap; i < n; i++) {
                int v = a[i];
                float kv = key[v];
                int j = i;
                while (j >= gap && key[a[j - gap]] < kv) {
                    a[j] = a[j - gap];
                    j -= gap;
                }
                a[j] = v;
            }
        }
    }

    private void emitMesh(int it, float dist) {
        boolean dyn = iDyn[it];
        float tx = 0, ty = 0, tz = 0, co = 1, si = 0;
        if (dyn) {
            tx = iTx[it];
            ty = iTy[it];
            tz = iTz[it];
            co = iCos[it];
            si = iSin[it];
        }
        for (int f = iF0[it], fe = iF1[it]; f < fe; f++) {
            float lod = fLod[f];
            if (lod > 0 && dist > lod) continue;
            int slot = fSlot[f] & 0xFFFF, col;
            float emis = 0;
            if (slot != 0) {
                col = slotCol[slot];
                if ((col >>> 24) == 0) continue;
                emis = slotEmis[slot];
            } else {
                col = fCol[f];
            }
            float nx = fnx[f], ny = fny[f], nz = fnz[f], qx = fcx[f], qy = fcy[f], qz = fcz[f];
            if (dyn) {
                float a = nx * co - nz * si;
                nz = nx * si + nz * co;
                nx = a;
                a = qx * co - qz * si + tx;
                qz = qx * si + qz * co + tz;
                qx = a;
                qy += ty;
            }
            int flag = fFlag[f];
            float wx = ex - qx, wy = ey - qy, wz = ez - qz;
            if (nx * wx + ny * wy + nz * wz <= 0) {
                if ((flag & TWO_SIDED) == 0) continue;
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float d = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            col = shade(col, nx, ny, nz, d, flag, emis);
            int n = fCnt[f], s = fStart[f];
            statFaces++;
            if (dyn) {
                for (int i = 0; i < n; i++) {
                    float x = vx[s + i], z = vz[s + i];
                    tX[i] = x * co - z * si + tx;
                    tY[i] = vy[s + i] + ty;
                    tZ[i] = x * si + z * co + tz;
                }
                poly(tX, tY, tZ, 0, n, col, (flag & NO_GROW) == 0);
            } else {
                poly(vx, vy, vz, s, n, col, (flag & NO_GROW) == 0);
            }
        }
    }

    /** Lights a surface colour for a normal, then tints it towards the horizon by distance. */
    int shade(int argb, float nx, float ny, float nz, float dist, int flag, float emis) {
        float r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if ((flag & UNLIT) == 0 && emis < 1) {
            float l = nx * sunX + ny * sunY + nz * sunZ;
            if (l < 0) l = 0;
            float fl = nx * fillX + ny * fillY + nz * fillZ;
            if (fl < 0) fl = 0;
            float h = ny * 0.5f + 0.5f;
            float lr = gndR + (skyR - gndR) * h + sunR * l + fillR * fl;
            float lg = gndG + (skyG - gndG) * h + sunG * l + fillG * fl;
            float lb = gndB + (skyB - gndB) * h + sunB * l + fillB * fl;
            if (emis > 0) {
                lr += (1 - lr) * emis;
                lg += (1 - lg) * emis;
                lb += (1 - lb) * emis;
            }
            r *= lr;
            g *= lg;
            b *= lb;
        }
        if ((flag & NOFOG) == 0 && dist > fogNear) {
            float k = (dist - fogNear) / (fogFar - fogNear);
            if (k > 1) k = 1;
            k = k * (2 - k) * fogMax;
            // Lights carry farther through haze than surfaces do.
            if (emis > 0) k *= 1 - 0.45f * emis;
            r += (fogR - r) * k;
            g += (fogG - g) * k;
            b += (fogB - b) * k;
        }
        int ir = r > 255 ? 255 : (r < 0 ? 0 : (int) r);
        int ig = g > 255 ? 255 : (g < 0 ? 0 : (int) g);
        int ib = b > 255 ? 255 : (b < 0 ? 0 : (int) b);
        return (argb & 0xFF000000) | (ir << 16) | (ig << 8) | ib;
    }

    /** Fog only, for colours that are already lit (sprites, glows). */
    int haze(int argb, float dist, float keep) {
        if (dist <= fogNear) return argb;
        float k = (dist - fogNear) / (fogFar - fogNear);
        if (k > 1) k = 1;
        k = k * (2 - k) * fogMax * keep;
        float r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r += (fogR - r) * k;
        g += (fogG - g) * k;
        b += (fogB - b) * k;
        return (argb & 0xFF000000) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    /**
     * Adds one convex world-space polygon to the frame: view transform, clipping against the near
     * plane and the viewport edges, projection. The colour is used as given.
     */
    void poly(float[] X, float[] Y, float[] Z, int off, int n, int argb, boolean grow) {
        if (n < 3 || n > MAXV - 6) return;
        float[] a = ca;
        int and = 31, or = 0;
        for (int i = 0; i < n; i++) {
            float dx = X[off + i] - ex, dy = Y[off + i] - ey, dz = Z[off + i] - ez;
            float x = dx * rx + dy * ry + dz * rz, y = dx * ux + dy * uy + dz * uz, z = dx * fx + dy * fy + dz * fz;
            a[i * 3] = x;
            a[i * 3 + 1] = y;
            a[i * 3 + 2] = z;
            int code = (z < Cam3.NEAR ? 1 : 0) | (x < -z * tl ? 2 : 0) | (x > z * tr ? 4 : 0)
                    | (y > z * tt ? 8 : 0) | (y < -z * tb ? 16 : 0);
            and &= code;
            or |= code;
        }
        if (and != 0) return;
        if (or != 0) {
            float[] b = cb;
            if ((or & 1) != 0) {
                n = clip(a, n, b, 0, 0, 1, -Cam3.NEAR);
                float[] t = a;
                a = b;
                b = t;
            }
            if ((or & 2) != 0 && n >= 3) {
                n = clip(a, n, b, 1, 0, tl, 0);
                float[] t = a;
                a = b;
                b = t;
            }
            if ((or & 4) != 0 && n >= 3) {
                n = clip(a, n, b, -1, 0, tr, 0);
                float[] t = a;
                a = b;
                b = t;
            }
            if ((or & 8) != 0 && n >= 3) {
                n = clip(a, n, b, 0, -1, tt, 0);
                float[] t = a;
                a = b;
                b = t;
            }
            if ((or & 16) != 0 && n >= 3) {
                n = clip(a, n, b, 0, 1, tb, 0);
                a = b;
            }
            if (n < 3) return;
        }
        float[] s = sxy;
        float area = 0;
        for (int i = 0; i < n; i++) {
            float k = focal / a[i * 3 + 2];
            s[i * 2] = pcx + a[i * 3] * k;
            s[i * 2 + 1] = pcy - a[i * 3 + 1] * k;
        }
        for (int i = 0, j = n - 1; i < n; j = i++) area += s[j * 2] * s[i * 2 + 1] - s[i * 2] * s[j * 2 + 1];
        // Edge-on or vanishing: nothing to draw (the test also throws out NaN).
        if (!(area > 0.02f || area < -0.02f)) return;
        if (grow && (argb >>> 24) == 255) {
            float sign = area > 0 ? GROW : -GROW;
            float[] g = gxy;
            for (int i = 0; i < n; i++) {
                int p = i == 0 ? n - 1 : i - 1, q = i == n - 1 ? 0 : i + 1;
                float x = s[i * 2], y = s[i * 2 + 1];
                float ax = x - s[p * 2], ay = y - s[p * 2 + 1], bx = s[q * 2] - x, by = s[q * 2 + 1] - y;
                float la = (float) Math.sqrt(ax * ax + ay * ay), lb = (float) Math.sqrt(bx * bx + by * by);
                if (la < 1e-4f || lb < 1e-4f) {
                    g[i * 2] = x;
                    g[i * 2 + 1] = y;
                    continue;
                }
                // Outward normals of the two edges meeting here, and their mitre.
                float n1x = ay / la, n1y = -ax / la, n2x = by / lb, n2y = -bx / lb;
                float den = 1 + n1x * n2x + n1y * n2y;
                if (den < 0.3f) den = 0.3f;
                g[i * 2] = x + (n1x + n2x) / den * sign;
                g[i * 2 + 1] = y + (n1y + n2y) / den * sign;
            }
            s = g;
        }
        if (nOut >= MAX_OUT || nXY + n * 2 > MAX_XY) {
            statDropped++;
            return;
        }
        System.arraycopy(s, 0, oXY, nXY, n * 2);
        oOff[nOut] = nXY;
        oCnt[nOut] = n;
        oCol[nOut] = argb;
        nOut++;
        nXY += n * 2;
        statPolys++;
    }

    /** Sutherland-Hodgman against the half-space ax + by + cz + d >= 0 in view space. */
    private static int clip(float[] in, int n, float[] out, float a, float b, float c, float d) {
        int m = 0;
        float px = in[(n - 1) * 3], py = in[(n - 1) * 3 + 1], pz = in[(n - 1) * 3 + 2];
        float pd = a * px + b * py + c * pz + d;
        for (int i = 0; i < n; i++) {
            float x = in[i * 3], y = in[i * 3 + 1], z = in[i * 3 + 2];
            float dd = a * x + b * y + c * z + d;
            if ((pd >= 0) != (dd >= 0)) {
                float t = pd / (pd - dd);
                out[m * 3] = px + (x - px) * t;
                out[m * 3 + 1] = py + (y - py) * t;
                out[m * 3 + 2] = pz + (z - pz) * t;
                m++;
            }
            if (dd >= 0) {
                out[m * 3] = x;
                out[m * 3 + 1] = y;
                out[m * 3 + 2] = z;
                m++;
            }
            px = x;
            py = y;
            pz = z;
            pd = dd;
        }
        return m;
    }

    /** Screen units per world unit at a world point, or 0 when it is behind the camera. */
    float scaleAt(float x, float y, float z) {
        float d = (x - ex) * fx + (y - ey) * fy + (z - ez) * fz;
        return d > Cam3.NEAR ? focal / d : 0;
    }

    /** Adds a disc that faces the camera: centre in world space, radius in world units. */
    void disc(float x, float y, float z, float radius, int argb) {
        float dx = x - ex, dy = y - ey, dz = z - ez;
        float d = dx * fx + dy * fy + dz * fz;
        if (!(d > Cam3.NEAR)) return;
        float k = focal / d;
        float sx = pcx + (dx * rx + dy * ry + dz * rz) * k, sy = pcy - (dx * ux + dy * uy + dz * uz) * k, r = radius * k;
        if (sx + r < cam.x0 || sx - r > cam.x1 || sy + r < cam.y0 || sy - r > cam.y1) return;
        if (nOut >= MAX_OUT || nXY + 3 > MAX_XY) return;
        oXY[nXY] = sx;
        oXY[nXY + 1] = sy;
        oXY[nXY + 2] = r;
        oOff[nOut] = nXY;
        oCnt[nOut] = CIRCLE;
        oCol[nOut] = argb;
        nOut++;
        nXY += 3;
        statPolys++;
    }

    /** Adds a line between two world points; width in world units (never thinner than minWidth on screen). */
    void line(float x1, float y1, float z1, float x2, float y2, float z2, float width, float minWidth, int argb) {
        float ax = x1 - ex, ay = y1 - ey, az = z1 - ez, bx = x2 - ex, by = y2 - ey, bz = z2 - ez;
        float da = ax * fx + ay * fy + az * fz, db = bx * fx + by * fy + bz * fz;
        if (da < Cam3.NEAR && db < Cam3.NEAR) return;
        if (da < Cam3.NEAR) {
            float t = (Cam3.NEAR - da) / (db - da);
            ax += (bx - ax) * t;
            ay += (by - ay) * t;
            az += (bz - az) * t;
            da = Cam3.NEAR;
        } else if (db < Cam3.NEAR) {
            float t = (Cam3.NEAR - db) / (da - db);
            bx += (ax - bx) * t;
            by += (ay - by) * t;
            bz += (az - bz) * t;
            db = Cam3.NEAR;
        }
        float ka = focal / da, kb = focal / db;
        float sx1 = pcx + (ax * rx + ay * ry + az * rz) * ka, sy1 = pcy - (ax * ux + ay * uy + az * uz) * ka;
        float sx2 = pcx + (bx * rx + by * ry + bz * rz) * kb, sy2 = pcy - (bx * ux + by * uy + bz * uz) * kb;
        if (Math.max(sx1, sx2) < cam.x0 || Math.min(sx1, sx2) > cam.x1
                || Math.max(sy1, sy2) < cam.y0 || Math.min(sy1, sy2) > cam.y1) return;
        // Keep absurd coordinates away from the surface: a line that starts next to the eye.
        float lim = 4000;
        if (Math.abs(sx1) > lim || Math.abs(sy1) > lim || Math.abs(sx2) > lim || Math.abs(sy2) > lim) return;
        if (nOut >= MAX_OUT || nXY + 5 > MAX_XY) return;
        float w = width * (ka + kb) * 0.5f;
        if (w < minWidth) w = minWidth;
        oXY[nXY] = sx1;
        oXY[nXY + 1] = sy1;
        oXY[nXY + 2] = sx2;
        oXY[nXY + 3] = sy2;
        oXY[nXY + 4] = w;
        oOff[nOut] = nXY;
        oCnt[nOut] = LINE;
        oCol[nOut] = argb;
        nOut++;
        nXY += 5;
        statPolys++;
    }

    /** Adds a 2D sprite whose feet stand on a world point; the painter draws it during the flush. */
    void sprite(int id, float x, float y, float z) {
        float dx = x - ex, dy = y - ey, dz = z - ez;
        float d = dx * fx + dy * fy + dz * fz;
        if (!(d > Cam3.NEAR * 2)) return;
        float k = focal / d;
        if (nOut >= MAX_OUT || nXY + 3 > MAX_XY) return;
        oXY[nXY] = pcx + (dx * rx + dy * ry + dz * rz) * k;
        oXY[nXY + 1] = pcy - (dx * ux + dy * uy + dz * uz) * k;
        oXY[nXY + 2] = k;
        oOff[nOut] = nXY;
        oCnt[nOut] = SPRITE;
        oCol[nOut] = id;
        nOut++;
        nXY += 3;
    }

    /** Sends the frame to the surface, in order. */
    void flush(Gfx c) {
        final float[] xy = oXY;
        for (int i = 0; i < nOut; i++) {
            int n = oCnt[i], o = oOff[i];
            if (n >= 3) {
                float[] p = polyBuf[n];
                System.arraycopy(xy, o, p, 0, n * 2);
                c.fillPoly(p, oCol[i]);
            } else if (n == CIRCLE) {
                c.fillCircle(xy[o], xy[o + 1], xy[o + 2], oCol[i]);
            } else if (n == LINE) {
                c.line(xy[o], xy[o + 1], xy[o + 2], xy[o + 3], xy[o + 4], oCol[i]);
            } else {
                painter.paintSprite(c, oCol[i], xy[o], xy[o + 1], xy[o + 2]);
            }
        }
    }

    /** True when every number in the frame's output is finite. */
    boolean outputFinite() {
        for (int i = 0; i < nXY; i++) {
            float v = oXY[i];
            if (v != v || v > 1e7f || v < -1e7f) return false;
        }
        return true;
    }
}
