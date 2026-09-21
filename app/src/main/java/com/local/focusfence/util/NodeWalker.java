package com.local.focusfence.util;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Bounded traversal. Own and recycle all obtained nodes, including on early return. */
public final class NodeWalker {
    private NodeWalker() {}
    public static boolean any(AccessibilityNodeInfo root, int max, Predicate<AccessibilityNodeInfo> predicate) {
        if (root == null) return false;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        int seen = 0;
        try {
            while (!q.isEmpty() && seen++ < max) {
                AccessibilityNodeInfo n = q.removeFirst();
                try {
                    if (predicate.test(n)) return true;
                    for (int i = 0; i < n.getChildCount() && q.size() < max; i++) {
                        AccessibilityNodeInfo child = n.getChild(i);
                        if (child != null) q.addLast(child);
                    }
                } finally { n.recycle(); }
            }
            return false;
        } finally { while (!q.isEmpty()) q.removeFirst().recycle(); }
    }
    public static void visit(AccessibilityNodeInfo root, int max, Consumer<AccessibilityNodeInfo> visitor) {
        any(root, max, n -> { visitor.accept(n); return false; });
    }
}
