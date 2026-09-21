package com.local.focusfence.detector;

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local classifier for distracting social surfaces.
 *
 * Instagram deliberately fails closed: known DM/profile surfaces are exempt, while an
 * unrecognised Instagram screen is counted as feed. This prevents an Instagram UI update from
 * silently disabling both the quota and the schedule, which was the failure mode in v0.3.0.
 */
public final class ShortSurfaceDetector {
    public enum Surface {
        INSTAGRAM_FEED,
        INSTAGRAM_EXPLORE,
        INSTAGRAM_REELS,
        INSTAGRAM_STORIES,
        FACEBOOK_FEED,
        FACEBOOK_REELS,
        FACEBOOK_STORIES,
        YOUTUBE_SHORTS,
        TIKTOK_FEED,
        THREADS_FEED
    }

    private static final String TAG = "FocusFenceDetector";
    private long lastDiagnosticAt = 0L;
    private String latchedBrowserPackage = "";
    private Surface latchedBrowserSurface;
    private long latchedBrowserAt;
    private final java.util.Map<String,Surface> wholeAppPackages = new java.util.HashMap<>();

    private final Set<String> browserPackages = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.brave.browser",
            "com.microsoft.emmx",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.vivaldi.browser",
            "com.duckduckgo.mobile.android"
    ));

    private static final Set<String> IG_REEL_IDS = new HashSet<>(Arrays.asList(
            "clips_viewer_view_pager",
            "clips_video_container",
            "clips_viewer_media_container",
            "clips_media_component",
            "clips_viewer_root"
    ));
    private static final Set<String> IG_STORY_IDS = new HashSet<>(Arrays.asList(
            "reel_viewer_root",
            "reel_viewer_media_container",
            "reel_viewer_header",
            "reel_viewer_content_layout"
    ));
    private static final Set<String> IG_HOME_IDS = new HashSet<>(Arrays.asList(
            "main_feed_action_bar",
            "sticky_header_list",
            "feed_recycler_view",
            "main_feed_recycler_view"
    ));
    private static final Set<String> IG_EXPLORE_IDS = new HashSet<>(Arrays.asList(
            "explore_action_bar",
            "explore_action_bar_container",
            "action_bar_search_edit_text",
            "explore_grid",
            "search_grid",
            "serp_grid",
            "search_results_list",
            "tag_result_list",
            "row_hashtag_container"
    ));
    private static final Set<String> IG_DM_IDS = new HashSet<>(Arrays.asList(
            "direct_inbox_container",
            "direct_inbox_action_bar",
            "inbox_refreshable_thread_list_recyclerview",
            "row_inbox_container",
            "row_inbox_username",
            "row_thread_composer_edittext",
            "row_thread_composer_send_button_container",
            "direct_text_message_text_view",
            "direct_thread_header",
            "thread_title_username",
            "message_list",
            "message_content",
            "reply_bar_edittext"
    ));
    private static final Set<String> IG_OVERLAY_SAFE_IDS = new HashSet<>(Arrays.asList(
            "comments_bottom_sheet",
            "layout_comment_thread_edittext",
            "comment_box_text",
            "inline_compose_box",
            "direct_private_share_container_view",
            "share_to_container",
            "direct_share_sheet",
            "reshare_bottom_sheet",
            "share_sheet_recipient_list",
            "recipient_chooser_row",
            "direct_multi_select_message_composer"
    ));
    private static final Set<String> IG_CREATION_IDS = new HashSet<>(Arrays.asList(
            "gallery_grid_item_thumbnail",
            "gallery_preview_button",
            "media_picker_grid_view",
            "multi_select_slide_button_alt",
            "creation_next_button",
            "next_button_textview",
            "caption_text_view",
            "caption_input_text_view",
            "cam_dest_feed",
            "cam_dest_clips",
            "cam_dest_story",
            "creation_camera_viewfinder",
            "camera_capture_button",
            "creation_shutter_button",
            "camera_view_placeholder",
            "camera_settings_gear"
    ));
    private static final Set<String> IG_PROFILE_IDS = new HashSet<>(Arrays.asList(
            "profile_header_container",
            "profile_user_info_compose_view",
            "profile_tab_layout",
            "profile_tab_icon_view",
            "row_profile_header_posts_container",
            "profile_header_post_count_front_familiar",
            "row_profile_header_followers_container",
            "profile_header_followers_stacked_familiar",
            "profile_header_following_stacked_familiar",
            "profile_header_bio_text",
            "private_profile_empty_state",
            "follow_list_username",
            "follow_list_container"
    ));
    private static final Set<String> IG_ACTIVITY_IDS = new HashSet<>(Arrays.asList(
            "activity_feed_list",
            "activity_feed_root",
            "activity_feed_newsfeed_story_row",
            "activity_feed_header_row",
            "row_news_text",
            "row_news_container",
            "notification_tab",
            "row_requested_user_accept_secondary",
            "row_requested_user_ignore"
    ));
    private static final Set<String> IG_POST_DETAIL_IDS = new HashSet<>(Arrays.asList(
            "row_feed_profile_header",
            "row_feed_photo_profile_name",
            "row_feed_photo_imageview",
            "row_feed_view_group_buttons"
    ));

    public void registerBrowserPackage(String pkg) {
        if (pkg != null && !pkg.trim().isEmpty()) browserPackages.add(pkg);
    }

    public void registerWholeAppPackage(String pkg, Surface surface) {
        if (pkg != null && !pkg.trim().isEmpty() && surface != null) wholeAppPackages.put(pkg, surface);
    }

    public Surface detect(String pkg, AccessibilityNodeInfo root, CharSequence className,
                          boolean includeStories, boolean diagnostic) {
        if (pkg == null || root == null) return null;
        Surface surface = wholeAppPackages.get(pkg);
        if (surface != null) {
            // Alternative/clone clients are treated as one social surface so a renamed package
            // cannot bypass the quota. The official app remains selectively usable for DMs.
        } else if (pkg.equals("com.instagram.android")) {
            surface = detectInstagram(root, includeStories);
        } else if (pkg.equals("com.instagram.lite")) {
            // Lite is treated as one social surface: selective DM exemptions are only guaranteed
            // in the full Instagram app, so Lite cannot be used as an easy quota bypass.
            surface = Surface.INSTAGRAM_FEED;
        } else if (pkg.equals("com.facebook.katana")) {
            surface = detectFacebook(root, className, includeStories);
        } else if (pkg.equals("com.facebook.lite")) {
            surface = Surface.FACEBOOK_FEED;
        } else if (pkg.equals("com.google.android.youtube")) {
            surface = detectYouTube(pkg, root);
        } else if (pkg.equals("com.zhiliaoapp.musically") || pkg.equals("com.ss.android.ugc.trill")) {
            surface = Surface.TIKTOK_FEED;
        } else if (pkg.equals("com.instagram.barcelona")) {
            surface = Surface.THREADS_FEED;
        } else if (browserPackages.contains(pkg)) {
            surface = detectSocialWeb(pkg, root);
        }
        if (diagnostic && isSupported(pkg)) dumpIds(pkg, root, surface);
        return surface;
    }

    public boolean isSupported(String pkg) {
        return "com.instagram.android".equals(pkg)
                || "com.instagram.lite".equals(pkg)
                || "com.facebook.katana".equals(pkg)
                || "com.facebook.lite".equals(pkg)
                || "com.google.android.youtube".equals(pkg)
                || "com.zhiliaoapp.musically".equals(pkg)
                || "com.ss.android.ugc.trill".equals(pkg)
                || "com.instagram.barcelona".equals(pkg)
                || browserPackages.contains(pkg)
                || wholeAppPackages.containsKey(pkg);
    }

    public String surfaceLabel(Surface surface) {
        if (surface == null) return "zone autorisée";
        switch (surface) {
            case INSTAGRAM_FEED: return "Instagram · Fil";
            case INSTAGRAM_EXPLORE: return "Instagram · Explore";
            case INSTAGRAM_REELS: return "Instagram · Reels";
            case INSTAGRAM_STORIES: return "Instagram · Stories";
            case FACEBOOK_FEED: return "Facebook · Fil";
            case FACEBOOK_REELS: return "Facebook · Reels";
            case FACEBOOK_STORIES: return "Facebook · Stories";
            case YOUTUBE_SHORTS: return "YouTube · Shorts";
            case TIKTOK_FEED: return "TikTok";
            case THREADS_FEED: return "Threads";
            default: return surface.name();
        }
    }

    /**
     * Tries to move Instagram to Direct Messages before Bernard covers a blocked distracting
     * surface. This keeps DMs usable after the social quota is exhausted.
     */
    public boolean openInstagramMessages(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findBySuffix(root, "direct_tab", 0);
        int depth = 0;
        while (node != null && depth++ < 4) {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            node = node.getParent();
        }
        return false;
    }

    private Surface detectInstagram(AccessibilityNodeInfo root, boolean includeStories) {
        Facts f = facts(root);

        // Utility overlays must win over their underlying feed/reel tree. Otherwise comments and
        // share sheets are classified as the content underneath them.
        if (f.hasAny(IG_OVERLAY_SAFE_IDS)) return null;
        if (f.hasAny(IG_CREATION_IDS) || f.selected("creation_tab")) return null;
        if (f.hasAny(IG_ACTIVITY_IDS)) return null;

        // Full-screen viewers win over DM/profile markers: a Reel opened from a DM/profile still
        // consumes the quota, while the conversation/profile itself remains exempt.
        if (includeStories && f.hasAny(IG_STORY_IDS) && !f.has("main_feed_action_bar")) {
            return Surface.INSTAGRAM_STORIES;
        }

        boolean feedMarker = f.hasAny(IG_HOME_IDS) || f.has("reels_tray_container");
        boolean storyMarker = f.hasAny(IG_STORY_IDS);
        boolean reelViewer = f.hasAny(IG_REEL_IDS)
                || f.selected("clips_tab")
                || f.selectedDescriptionEquals("reels");
        if (reelViewer && !feedMarker && !storyMarker) return Surface.INSTAGRAM_REELS;

        // Explicit safe zones take precedence over stale/underlying Home markers.
        if (f.hasAny(IG_DM_IDS) || f.selected("direct_tab")) return null;
        if (f.hasAny(IG_PROFILE_IDS)
                || f.selected("profile_tab") || f.selected("tab_avatar") || f.selected("avatar_tab")) return null;

        // A single-post detail often keeps the previously selected bottom tab. The back button +
        // post chrome distinguishes it from the infinite feed itself.
        if (f.has("action_bar_button_back") && f.hasAny(IG_POST_DETAIL_IDS)) return Surface.INSTAGRAM_FEED;

        if (f.hasAny(IG_EXPLORE_IDS) || f.selected("search_tab") || f.selected("explore_tab")) {
            return Surface.INSTAGRAM_EXPLORE;
        }

        if (f.hasAny(IG_HOME_IDS) || f.selected("feed_tab")) {
            return Surface.INSTAGRAM_FEED;
        }

        // Unknown Instagram screens are allowed but exposed by the diagnostic. This avoids
        // blocking settings, account tools and future utility screens just because Meta renamed
        // an internal id. The three infinite-consumption surfaces above still have redundant
        // tab + view-id signatures.
        return null;
    }

    private Surface detectSocialWeb(String pkg, AccessibilityNodeInfo root) {
        String url = findBrowserUrl(root);
        if (url != null) {
            String u = url.toLowerCase(Locale.ROOT);
            Surface result = null;
            if (u.contains("instagram.com")) {
                if (u.contains("instagram.com/direct") || u.contains("/direct/inbox")) result = null;
                else if (u.contains("/reel") || u.contains("/reels")) result = Surface.INSTAGRAM_REELS;
                else if (u.contains("/stories")) result = Surface.INSTAGRAM_STORIES;
                else if (u.contains("/explore") || u.contains("/tags/") || u.contains("/locations/")) result = Surface.INSTAGRAM_EXPLORE;
                else result = Surface.INSTAGRAM_FEED;
            } else if (u.contains("facebook.com")) {
                if (u.contains("/messages") || u.contains("messenger.com/")) result = null;
                else if (u.contains("/reel") || u.contains("/reels") || u.contains("/watch")) result = Surface.FACEBOOK_REELS;
                else if (u.contains("/stories")) result = Surface.FACEBOOK_STORIES;
                else result = Surface.FACEBOOK_FEED;
            } else if (u.contains("youtube.com/shorts/") || u.contains("m.youtube.com/shorts/")) {
                result = Surface.YOUTUBE_SHORTS;
            } else if (u.contains("tiktok.com")) {
                result = Surface.TIKTOK_FEED;
            } else if (u.contains("threads.net") || u.contains("threads.com")) {
                result = Surface.THREADS_FEED;
            }
            if (result != null) {
                latchedBrowserPackage = pkg;
                latchedBrowserSurface = result;
                latchedBrowserAt = System.currentTimeMillis();
                return result;
            }
            if (u.contains("instagram.com") || u.contains("facebook.com") || u.contains("youtube.com")
                    || u.contains("tiktok.com") || u.contains("threads.net") || u.contains("threads.com")) {
                latchedBrowserPackage = "";
                latchedBrowserSurface = null;
                latchedBrowserAt = 0L;
            }
            return null;
        }
        // Address bars can disappear while scrolling. Keep a short local latch so simply hiding the
        // toolbar does not become a bypass, but release it quickly enough to avoid trapping normal
        // browsing after the user leaves the social site.
        if (pkg.equals(latchedBrowserPackage)
                && latchedBrowserSurface != null
                && System.currentTimeMillis() - latchedBrowserAt < 30L * 60_000L) {
            return latchedBrowserSurface;
        }
        return null;
    }

    private String findBrowserUrl(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 700) {
            AccessibilityNodeInfo n = q.removeFirst();
            String id = suffix(n.getViewIdResourceName());
            String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
            if (n.isVisibleToUser()
                    && (lower.contains("url") || lower.contains("address") || lower.contains("location"))) {
                CharSequence value = n.getText();
                if (value == null || value.length() == 0) value = n.getContentDescription();
                if (value != null) {
                    String text = value.toString().trim();
                    if (text.contains(".") && text.length() < 2048) return text;
                }
            }
            enqueueChildren(n, q);
        }
        return null;
    }

    private Surface detectYouTube(String pkg, AccessibilityNodeInfo root) {
        Facts f = facts(root);
        if (f.has("reel_player_page_container")
                || f.has("reel_recycler")
                || f.has("reel_watch_fragment_root")
                || f.has("shorts_container")) {
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

        Facts f = facts(root);
        // Do not treat every Facebook screen as the feed. Marketplace, Groups, profiles,
        // notifications and settings are utility surfaces and must stay reachable.
        if (f.has("newsfeed_view_pager")
                || f.has("feed_composer_launcher")
                || f.selected("feed_tab")
                || hasSelectedDescriptionPrefix(root,"Home,")
                || hasSelectedDescriptionPrefix(root,"Accueil,")) return Surface.FACEBOOK_FEED;
        if (f.selected("watch_tab")
                || hasSelectedDescriptionPrefix(root,"Watch,")
                || hasSelectedDescriptionPrefix(root,"Videos,")
                || hasSelectedDescriptionPrefix(root,"Video,")
                || hasSelectedDescriptionPrefix(root,"Vidéos,")
                || hasSelectedDescriptionPrefix(root,"Vidéo,")) return Surface.FACEBOOK_REELS;
        return null;
    }

    static Surface classifyInstagramForTest(Set<String> ids, Set<String> selectedIds,
                                            Set<String> selectedDescriptions, boolean includeStories) {
        Facts f = new Facts();
        f.ids.addAll(ids);
        f.selectedIds.addAll(selectedIds);
        for (String d : selectedDescriptions) f.selectedDescriptions.add(d.toLowerCase(Locale.ROOT));
        if (f.hasAny(IG_OVERLAY_SAFE_IDS) || f.hasAny(IG_CREATION_IDS) || f.selected("creation_tab") || f.hasAny(IG_ACTIVITY_IDS)) return null;
        boolean feedMarker = f.hasAny(IG_HOME_IDS) || f.has("reels_tray_container");
        boolean storyMarker = f.hasAny(IG_STORY_IDS);
        if (includeStories && storyMarker && !f.has("main_feed_action_bar")) return Surface.INSTAGRAM_STORIES;
        if ((f.hasAny(IG_REEL_IDS) || f.selected("clips_tab") || f.selectedDescriptionEquals("reels"))
                && !feedMarker && !storyMarker) return Surface.INSTAGRAM_REELS;
        if (f.hasAny(IG_DM_IDS) || f.selected("direct_tab")) return null;
        if (f.hasAny(IG_PROFILE_IDS) || f.selected("profile_tab") || f.selected("tab_avatar") || f.selected("avatar_tab")) return null;
        if (f.has("action_bar_button_back") && f.hasAny(IG_POST_DETAIL_IDS)) return Surface.INSTAGRAM_FEED;
        if (f.hasAny(IG_EXPLORE_IDS) || f.selected("search_tab") || f.selected("explore_tab")) return Surface.INSTAGRAM_EXPLORE;
        if (f.hasAny(IG_HOME_IDS) || f.selected("feed_tab")) return Surface.INSTAGRAM_FEED;
        return null;
    }

    private static final class Facts {
        final Set<String> ids = new HashSet<>();
        final Set<String> selectedIds = new HashSet<>();
        final Set<String> selectedDescriptions = new HashSet<>();

        boolean has(String suffix) { return ids.contains(suffix); }
        boolean hasAny(Set<String> suffixes) {
            for (String s : suffixes) if (ids.contains(s)) return true;
            return false;
        }
        boolean selected(String suffix) { return selectedIds.contains(suffix); }
        boolean selectedDescriptionEquals(String value) {
            return selectedDescriptions.contains(value.toLowerCase(Locale.ROOT));
        }
    }

    private Facts facts(AccessibilityNodeInfo root) {
        Facts out = new Facts();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 1600) {
            AccessibilityNodeInfo n = q.removeFirst();
            String id = suffix(n.getViewIdResourceName());
            if (id != null && n.isVisibleToUser()) {
                out.ids.add(id);
                if (n.isSelected() || n.isChecked() || selectedChild(n)) out.selectedIds.add(id);
            }
            CharSequence d = n.getContentDescription();
            if (d != null && (n.isSelected() || n.isChecked())) {
                out.selectedDescriptions.add(d.toString().trim().toLowerCase(Locale.ROOT));
            }
            enqueueChildren(n, q);
        }
        return out;
    }

    private String suffix(String id) {
        if (id == null || id.isEmpty()) return null;
        int slash = id.lastIndexOf('/');
        return slash >= 0 && slash + 1 < id.length() ? id.substring(slash + 1) : id;
    }

    private AccessibilityNodeInfo findBySuffix(AccessibilityNodeInfo node, String wanted, int depth) {
        if (node == null || depth > 30) return null;
        if (wanted.equals(suffix(node.getViewIdResourceName()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findBySuffix(child, wanted, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean selectedChild(AccessibilityNodeInfo node) {
        if (node == null) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null && (c.isSelected() || c.isChecked())) return true;
        }
        return false;
    }

    private boolean ancestorSelected(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node == null ? null : node.getParent();
        int depth = 0;
        while (p != null && depth++ < 5) {
            if (p.isSelected() || p.isChecked()) return true;
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
            if (d != null && d.toString().regionMatches(true, 0, prefix, 0, prefix.length())
                    && (n.isSelected() || n.isChecked())) return true;
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
            if ("android.view.SurfaceView".contentEquals(safeClass(n))
                    && isLarge(n, rootBounds, 0.90f, 0.75f)) return true;
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

    private void dumpIds(String pkg, AccessibilityNodeInfo root, Surface surface) {
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticAt < 5000L) return;
        lastDiagnosticAt = now;
        StringBuilder b = new StringBuilder();
        Facts f = facts(root);
        for (String id : f.ids) b.append(id).append(',');
        Log.d(TAG, "pkg=" + pkg + " surface=" + (surface == null ? "SAFE" : surface.name()) + " ids=" + b);
    }
}
