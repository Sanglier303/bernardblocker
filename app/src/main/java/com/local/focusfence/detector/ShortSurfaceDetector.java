package com.local.focusfence.detector;

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Best-effort detector for infinite short-video / story surfaces.
 * Detection is local and only inspects accessibility metadata.
 *
 * Resource IDs and structural patterns are intentionally centralized here because
 * social apps change them over time. Diagnostic mode logs visible IDs to make
 * maintenance possible without rewriting the service.
 */
public final class ShortSurfaceDetector {
    public enum Surface {
        INSTAGRAM_REELS,
        INSTAGRAM_STORIES,
        FACEBOOK_REELS,
        FACEBOOK_STORIES,
        YOUTUBE_SHORTS
    }

    private static final String TAG = "FocusFenceDetector";
    private long lastDiagnosticAt = 0L;

    public Surface detect(String pkg, AccessibilityNodeInfo root, CharSequence className, boolean includeStories, boolean diagnostic) {
        if (pkg == null || root == null) return null;
        Surface surface = null;
        if (pkg.equals("com.instagram.android")) {
            surface = detectInstagram(root, includeStories);
        } else if (pkg.equals("com.facebook.katana")) {
            surface = detectFacebook(root, className, includeStories);
        } else if (pkg.equals("com.google.android.youtube")) {
            surface = detectYouTube(pkg, root);
        }
        if (surface == null && diagnostic && isSupported(pkg)) dumpIds(pkg, root);
        return surface;
    }

    public boolean isSupported(String pkg) {
        return "com.instagram.android".equals(pkg)
                || "com.facebook.katana".equals(pkg)
                || "com.google.android.youtube".equals(pkg)
                ;
    }

    private Surface detectInstagram(AccessibilityNodeInfo root, boolean includeStories) {
        List<String> reels = Arrays.asList(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/clips_media_component"
        );
        if (hasAnyVisibleId(root, reels)) return Surface.INSTAGRAM_REELS;
        if (includeStories && hasVisibleId(root, "com.instagram.android:id/reel_viewer_root")) {
            return Surface.INSTAGRAM_STORIES;
        }
        if (isSelectedTab(root, "com.instagram.android:id/clips_tab")) return Surface.INSTAGRAM_REELS;
        return null;
    }

    private Surface detectYouTube(String pkg, AccessibilityNodeInfo root) {
        if (hasVisibleId(root, pkg + ":id/reel_player_page_container")
                || hasVisibleId(root, pkg + ":id/reel_recycler")) {
            return Surface.YOUTUBE_SHORTS;
        }
        List<AccessibilityNodeInfo> shorts = root.findAccessibilityNodeInfosByText("Shorts");
        if (shorts != null) {
            for (AccessibilityNodeInfo n : shorts) {
                if (n != null && (n.isSelected() || ancestorSelected(n) || selectedChild(n))) {
                    return Surface.YOUTUBE_SHORTS;
                }
            }
        }
        return null;
    }

    private Surface detectFacebook(AccessibilityNodeInfo root, CharSequence className, boolean includeStories) {
        if (includeStories && className != null && className.toString().contains("StoryViewerActivity")) {
            return Surface.FACEBOOK_STORIES;
        }
        Set<String> exact = new HashSet<>(Arrays.asList(
                "FbShortsComposerAttachmentComponentSpec_STICKER",
                "FbShortsComposerAttachmentComponentSpec_GIF"
        ));
        if (hasContentDescription(root, exact)) return Surface.FACEBOOK_REELS;
        if (hasSelectedDescriptionPrefix(root, "Reels,")) return Surface.FACEBOOK_REELS;
        if (matchesFacebookReelStructure(root)) return Surface.FACEBOOK_REELS;
        return null;
    }

    private boolean hasAnyVisibleId(AccessibilityNodeInfo root, List<String> ids) {
        for (String id : ids) if (hasVisibleId(root, id)) return true;
        return false;
    }

    private boolean hasVisibleId(AccessibilityNodeInfo root, String id) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) return false;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser()) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isSelectedTab(AccessibilityNodeInfo root, String id) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) return false;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && (n.isSelected() || selectedChild(n))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean selectedChild(AccessibilityNodeInfo node) {
        if (node == null) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null && c.isSelected()) return true;
        }
        return false;
    }

    private boolean ancestorSelected(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node == null ? null : node.getParent();
        int depth = 0;
        while (p != null && depth++ < 5) {
            if (p.isSelected()) return true;
            p = p.getParent();
        }
        return false;
    }

    private boolean hasContentDescription(AccessibilityNodeInfo root, Set<String> exact) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 900) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence d = n.getContentDescription();
            if (d != null) {
                for (String e : exact) if (d.toString().equalsIgnoreCase(e)) return true;
            }
            enqueueChildren(n, q);
        }
        return false;
    }

    private boolean hasSelectedDescriptionPrefix(AccessibilityNodeInfo root, String prefix) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 900) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence d = n.getContentDescription();
            if (d != null && d.toString().regionMatches(true, 0, prefix, 0, prefix.length()) && n.isSelected()) return true;
            enqueueChildren(n, q);
        }
        return false;
    }

    /** Structural fallback for Facebook, which often lacks stable IDs. */
    private boolean matchesFacebookReelStructure(AccessibilityNodeInfo root) {
        Rect rb = new Rect();
        root.getBoundsInScreen(rb);
        if (rb.width() <= 0 || rb.height() <= 0) return false;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 1200) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (isLarge(n, rb, 0.90f, 0.75f)
                    && "androidx.recyclerview.widget.RecyclerView".contentEquals(safeClass(n))
                    && n.isScrollable()
                    && hasLargeLongClickableButtonWithSurface(n, rb)) {
                return true;
            }
            enqueueChildren(n, q);
        }
        return false;
    }

    private boolean hasLargeLongClickableButtonWithSurface(AccessibilityNodeInfo parent, Rect rootBounds) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        enqueueChildren(parent, q);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 500) {
            AccessibilityNodeInfo n = q.removeFirst();
            if ("android.widget.Button".contentEquals(safeClass(n))
                    && n.isLongClickable()
                    && isLarge(n, rootBounds, 0.90f, 0.75f)
                    && hasLargeSurface(n, rootBounds)) return true;
            enqueueChildren(n, q);
        }
        return false;
    }

    private boolean hasLargeSurface(AccessibilityNodeInfo parent, Rect rootBounds) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        enqueueChildren(parent, q);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 300) {
            AccessibilityNodeInfo n = q.removeFirst();
            if ("android.view.SurfaceView".contentEquals(safeClass(n)) && isLarge(n, rootBounds, 0.90f, 0.75f)) return true;
            enqueueChildren(n, q);
        }
        return false;
    }

    private CharSequence safeClass(AccessibilityNodeInfo n) {
        return n.getClassName() == null ? "" : n.getClassName();
    }

    private boolean isLarge(AccessibilityNodeInfo n, Rect root, float minW, float minH) {
        if (n == null || !n.isVisibleToUser()) return false;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r.width() >= root.width() * minW && r.height() >= root.height() * minH;
    }

    private void enqueueChildren(AccessibilityNodeInfo n, ArrayDeque<AccessibilityNodeInfo> q) {
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) q.addLast(c);
        }
    }

    private void dumpIds(String pkg, AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticAt < 5000L) return;
        lastDiagnosticAt = now;
        StringBuilder b = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        Set<String> ids = new HashSet<>();
        while (!q.isEmpty() && visited++ < 700) {
            AccessibilityNodeInfo n = q.removeFirst();
            String id = n.getViewIdResourceName();
            if (id != null) ids.add(id);
            enqueueChildren(n, q);
        }
        for (String id : ids) b.append(id).append(',');
        Log.d(TAG, "Unknown surface pkg=" + pkg + " ids=" + b);
    }
}
