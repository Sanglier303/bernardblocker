package com.local.focusfence.detector;

import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.local.focusfence.util.NodeWalker;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local classifier for distracting social surfaces.
 *
 * Instagram uses explicit positive signatures for infinite-consumption surfaces and explicit
 * exemptions for utility screens. Known Feed/Explore/Reels/Stories are controlled, while unknown
 * utility screens fail open to avoid blocking messages/settings after an upstream UI rename.
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
    private int latchedBrowserWindow = -1, facebookWindow = -1;
    private final java.util.Map<String,Surface> wholeAppPackages = new java.util.HashMap<>();
    private boolean facebookFeedLatched;

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
        if (surface == null) return "écran utilitaire ou non reconnu · non compté";
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
        return NodeWalker.any(root, 1400, n -> {
            if (!n.isVisibleToUser() || !"direct_tab".equals(suffix(n.getViewIdResourceName()))) return false;
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            AccessibilityNodeInfo parent = n.getParent();
            for (int depth = 0; parent != null && depth < 4; depth++) {
                AccessibilityNodeInfo next = null;
                try {
                    if (parent.isVisibleToUser() && parent.isClickable()
                            && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                    next = parent.getParent();
                } finally { parent.recycle(); }
                parent = next;
            }
            if (parent != null) parent.recycle();
            return false;
        });
    }

    private Surface detectInstagram(AccessibilityNodeInfo root, boolean includeStories) {
        return classifyInstagram(facts(root), includeStories);
    }

    private static Surface classifyInstagram(Facts f, boolean includeStories) {
        // Utility overlays must win over their underlying feed/reel tree. Otherwise comments and
        // share sheets are classified as the content underneath them.
        if (f.hasAny(IG_OVERLAY_SAFE_IDS)) return null;
        if (f.hasAny(IG_CREATION_IDS) || f.selected("creation_tab")) return null;
        if (f.hasAny(IG_ACTIVITY_IDS) || f.selected("notification_tab")) return null;

        // Full-screen viewers win over DM/profile markers: a Reel opened from a DM/profile still
        // consumes the quota, while the conversation/profile itself remains exempt.
        if (includeStories && f.hasAny(IG_STORY_IDS) && !f.has("main_feed_action_bar")) {
            return Surface.INSTAGRAM_STORIES;
        }

        boolean feedMarker = f.hasAny(IG_HOME_IDS) || f.has("reels_tray_container");
        boolean storyMarker = f.hasAny(IG_STORY_IDS);
        boolean fullReelViewer = f.has("clips_viewer_root") || f.has("clips_viewer_view_pager");
        boolean reelContent = f.hasAny(IG_REEL_IDS);
        boolean reelTab = f.selected("clips_tab") || f.selectedDescriptionEquals("reels");
        if (!storyMarker && (fullReelViewer || (reelContent && !feedMarker)))
            return Surface.INSTAGRAM_REELS;

        // Explicit safe zones take precedence over stale/underlying Home markers.
        if (f.hasAny(IG_DM_IDS) || f.selected("direct_tab")) return null;
        if (f.hasAny(IG_PROFILE_IDS)
                || f.selected("profile_tab") || f.selected("tab_avatar") || f.selected("avatar_tab")) return null;

        // A single-post detail often keeps the previously selected bottom tab. The back button +
        // post chrome distinguishes it from the infinite feed itself.
        if (f.has("action_bar_button_back") && f.hasAny(IG_POST_DETAIL_IDS)) return null;

        // A selected Reels tab is weaker evidence than an actual conversation/profile/post.
        // Fullscreen viewers above still count when deliberately opened from a DM.
        if (reelTab && !feedMarker && !storyMarker) return Surface.INSTAGRAM_REELS;

        if (f.hasAny(IG_EXPLORE_IDS) || f.selected("search_tab") || f.selected("explore_tab")) {
            return Surface.INSTAGRAM_EXPLORE;
        }

        if (f.hasAny(IG_HOME_IDS) || f.selected("feed_tab")) {
            return Surface.INSTAGRAM_FEED;
        }

        // Generic back-navigation is a useful final utility signal after the controlled viewers
        // above have already been checked. This keeps account/settings-style screens usable.
        if (f.has("action_bar_button_back") || f.has("header_left_button")) return null;

        // Unknown is NOT proof of a feed. Blanket fallback was blocking renamed DM/utility
        // screens and contradicted selective blocking. Known viewers/feed/explore remain enforced.
        return null;
    }

    private Surface detectSocialWeb(String pkg, AccessibilityNodeInfo root) {
        String rawUrl = findBrowserUrl(pkg, root);
        if (rawUrl != null) {
            Uri uri = parseBrowserUri(rawUrl);
            String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri == null || uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            Surface result = null;
            boolean recognizedSocial = false;

            if (hostIs(host, "instagram.com")) {
                recognizedSocial = true;
                if (path.startsWith("/direct")) result = null;
                else if (path.startsWith("/reel") || path.startsWith("/reels")) result = Surface.INSTAGRAM_REELS;
                else if (path.startsWith("/stories")) result = Surface.INSTAGRAM_STORIES;
                else if (path.startsWith("/explore") || path.startsWith("/tags/") || path.startsWith("/locations/")) result = Surface.INSTAGRAM_EXPLORE;
                else if (path.isEmpty() || path.equals("/") || path.equals("/home") || path.equals("/home/")) result = Surface.INSTAGRAM_FEED;
                // Single posts (/p/...), profiles and account/utility routes remain accessible.
            } else if (hostIs(host, "facebook.com")) {
                recognizedSocial = true;
                if (path.startsWith("/messages")) result = null;
                else if (path.startsWith("/reel") || path.startsWith("/reels") || path.startsWith("/watch")) result = Surface.FACEBOOK_REELS;
                else if (path.startsWith("/stories")) result = Surface.FACEBOOK_STORIES;
                else if (path.isEmpty() || path.equals("/") || path.equals("/home.php")) result = Surface.FACEBOOK_FEED;
            } else if (hostIs(host, "messenger.com")) {
                recognizedSocial = true;
                result = null;
            } else if (hostIs(host, "youtube.com") && path.startsWith("/shorts/")) {
                recognizedSocial = true;
                result = Surface.YOUTUBE_SHORTS;
            } else if (hostIs(host, "tiktok.com")) {
                recognizedSocial = true;
                result = Surface.TIKTOK_FEED;
            } else if (hostIs(host, "threads.net") || hostIs(host, "threads.com")) {
                recognizedSocial = true;
                result = Surface.THREADS_FEED;
            }

            if (result != null) {
                latchedBrowserPackage = pkg;
                latchedBrowserSurface = result;
                latchedBrowserAt = SystemClock.elapsedRealtime();
                latchedBrowserWindow = root.getWindowId();
                return result;
            }

            // Any readable non-controlled URL (including DMs) proves the browser has left the
            // latched scroll surface. Clear the latch immediately instead of letting an old social
            // tab poison later browsing.
            if (!recognizedSocial || result == null) clearBrowserLatch();
            return null;
        }

        // Browsers hide their address bar while scrolling. Once a social URL has been positively
        // observed, stay fail-closed for that browser until a readable URL proves the user left it.
        // There is deliberately no wall-clock expiry that can be waited out or bypassed by changing
        // the device clock.
        if (pkg.equals(latchedBrowserPackage) && root.getWindowId() == latchedBrowserWindow && latchedBrowserSurface != null) {
            latchedBrowserAt = SystemClock.elapsedRealtime();
            return latchedBrowserSurface;
        }
        return null;
    }

    private void clearBrowserLatch() {
        latchedBrowserPackage = "";
        latchedBrowserSurface = null;
        latchedBrowserAt = 0L;latchedBrowserWindow = -1;
    }

    private Uri parseBrowserUri(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        try {
            if (!value.contains("://")) value = "https://" + value;
            return Uri.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hostIs(String host, String domain) {
        if (host == null || domain == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        String d = domain.toLowerCase(Locale.ROOT);
        return h.equals(d) || h.endsWith("." + d);
    }

    private String findBrowserUrl(String pkg, AccessibilityNodeInfo root) {
        final String[] found = { null };
        NodeWalker.any(root, 700, n -> {
            String full = n.getViewIdResourceName();
            if (full == null || !full.startsWith(pkg + ":id/") || !n.isVisibleToUser()) return false;
            String id = suffix(full).toLowerCase(Locale.ROOT);
            if (!(id.equals("url_bar") || id.equals("urlbar_view") || id.equals("urlbar_edit_text")
                    || id.equals("mozac_browser_toolbar_url_view") || id.equals("location_bar")
                    || id.equals("address_bar") || id.equals("omnibar_text_input"))) return false;
            // Text being typed in the omnibox is not the URL of the page behind it.
            if (n.isEditable() && n.isFocused()) return false;
            CharSequence value = n.getText();
            if (value == null || value.length() == 0) value = n.getContentDescription();
            if (value == null) return false;
            String text = value.toString().trim();
            if (!text.contains(".") || text.length() >= 2048) return false;
            found[0] = text;return true;
        });
        return found[0];
    }

    private Surface detectYouTube(String pkg, AccessibilityNodeInfo root) {
        Facts f = facts(root);
        if (f.has("reel_player_page_container")
                || f.has("reel_recycler")
                || f.has("reel_watch_fragment_root")
                || f.has("shorts_container")) {
            return Surface.YOUTUBE_SHORTS;
        }
        if (NodeWalker.any(root, 1200, n -> n.isVisibleToUser() && n.getText() != null
                && "Shorts".contentEquals(n.getText())
                && (n.isSelected() || ancestorSelected(n) || selectedChild(n)))) return Surface.YOUTUBE_SHORTS;
        return null;
    }

    private Surface detectFacebook(AccessibilityNodeInfo root, CharSequence className, boolean includeStories) {
        if (facebookWindow != root.getWindowId()) { facebookFeedLatched = false; facebookWindow = root.getWindowId(); }
        Facts f = facts(root);
        // Comment/share/composer overlays win over the underlying Reel tree.
        if (f.has("comments_container") || f.has("composer_text_view") || f.has("share_sheet")
                || f.has("thread_composer") || f.has("message_list")) {
            facebookFeedLatched = false;return null;
        }
        if (includeStories && className != null && className.toString().contains("StoryViewerActivity")) {
            facebookFeedLatched=false;
            return Surface.FACEBOOK_STORIES;
        }
        Set<String> exact = new HashSet<>(Arrays.asList(
                "FbShortsComposerAttachmentComponentSpec_STICKER",
                "FbShortsComposerAttachmentComponentSpec_GIF"
        ));
        if (hasContentDescription(root, exact)
                || hasSelectedDescriptionPrefix(root, "Reels,")
                || matchesFacebookReelStructure(root)) {
            facebookFeedLatched=false;
            return Surface.FACEBOOK_REELS;
        }



        // Explicit utility surfaces must clear a previously latched Home feed. Facebook often
        // hides its bottom navigation while scrolling, so a latch is necessary, but it must never
        // leak into comments, profiles or another selected tab.
        if (f.has("comments_container")
                || f.has("composer_text_view")
                || f.has("search_results_recyclerview")
                || f.has("unified_search_results")
                || hasSelectedDescriptionPrefix(root,"Marketplace,")
                || hasSelectedDescriptionPrefix(root,"Groups,")
                || hasSelectedDescriptionPrefix(root,"Groupes,")
                || hasSelectedDescriptionPrefix(root,"Friends,")
                || hasSelectedDescriptionPrefix(root,"Amis,")
                || hasSelectedDescriptionPrefix(root,"Notifications,")
                || hasSelectedDescriptionPrefix(root,"Menu,")
                || hasVisibleDescriptionPrefix(root,"Back")
                || hasVisibleDescriptionPrefix(root,"Retour")) {
            facebookFeedLatched=false;
            return null;
        }

        if (f.selected("watch_tab")
                || hasSelectedDescriptionPrefix(root,"Watch,")
                || hasSelectedDescriptionPrefix(root,"Videos,")
                || hasSelectedDescriptionPrefix(root,"Video,")
                || hasSelectedDescriptionPrefix(root,"Vidéos,")
                || hasSelectedDescriptionPrefix(root,"Vidéo,")) {
            facebookFeedLatched=false;
            return Surface.FACEBOOK_REELS;
        }

        if (f.has("newsfeed_view_pager")
                || f.has("feed_composer_launcher")
                || f.selected("feed_tab")
                || hasSelectedDescriptionPrefix(root,"Home,")
                || hasSelectedDescriptionPrefix(root,"Accueil,")) {
            facebookFeedLatched=true;
            return Surface.FACEBOOK_FEED;
        }

        // The Home navigation disappears during a fling on current Facebook builds. Once Home has
        // been positively identified, an unlabelled continuation of that same screen remains feed
        // until an explicit utility/reel surface above proves otherwise.
        if (facebookFeedLatched) return Surface.FACEBOOK_FEED;
        return null;
    }

    static Surface classifyInstagramForTest(Set<String> ids, Set<String> selectedIds,
                                            Set<String> selectedDescriptions, boolean includeStories) {
        Facts f = new Facts();
        f.ids.addAll(ids);
        f.selectedIds.addAll(selectedIds);
        for (String d : selectedDescriptions) f.selectedDescriptions.add(d.toLowerCase(Locale.ROOT));
        return classifyInstagram(f, includeStories);
    }

    /** Native Instagram resource names only. Text, descriptions, account names and URLs are excluded. */
    public org.json.JSONObject diagnosticEvidence(String pkg,AccessibilityNodeInfo root,Surface surface){
        if(!"com.instagram.android".equals(pkg)||root==null)return null;
        Facts f=facts(root);
        try{return new org.json.JSONObject().put("package",pkg).put("format","instagram-resource-facts-v1")
                .put("ids",safeIds(f.ids)).put("selectedIds",safeIds(f.selectedIds))
                .put("selectedReelsLabel",f.selectedDescriptionEquals("reels"))
                .put("surface",surface==null?"UNKNOWN_OR_UTILITY":surface.name());}
        catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    private static org.json.JSONArray safeIds(Set<String> names){
        org.json.JSONArray result=new org.json.JSONArray();
        for(String id:new java.util.TreeSet<>(names))
            if(id.length()<=96&&id.matches("[A-Za-z_][A-Za-z0-9_]*")){result.put(id);if(result.length()==160)break;}
        return result;
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
        NodeWalker.visit(root, 1600, n -> {
            if (!n.isVisibleToUser()) return;
            String id = suffix(n.getViewIdResourceName());
            if (id != null) {
                out.ids.add(id);
                if (n.isSelected() || n.isChecked() || selectedChild(n)) out.selectedIds.add(id);
            }
            CharSequence d = n.getContentDescription();
            if (d != null && (n.isSelected() || n.isChecked()))
                out.selectedDescriptions.add(d.toString().trim().toLowerCase(Locale.ROOT));
        });
        return out;
    }

    private String suffix(String id) {
        if (id == null || id.isEmpty()) return null;
        int slash = id.lastIndexOf('/');
        return slash >= 0 && slash + 1 < id.length() ? id.substring(slash + 1) : id;
    }

    private boolean selectedChild(AccessibilityNodeInfo node) {
        if (node == null) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (child.isVisibleToUser() && (child.isSelected() || child.isChecked())) return true; }
            finally { child.recycle(); }
        }
        return false;
    }

    private boolean ancestorSelected(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node == null ? null : node.getParent();
        for (int depth = 0; p != null && depth < 5; depth++) {
            AccessibilityNodeInfo next = null;
            try {
                if (p.isVisibleToUser() && (p.isSelected() || p.isChecked())) return true;
                next = p.getParent();
            } finally { p.recycle(); }
            p = next;
        }
        if (p != null) p.recycle();
        return false;
    }

    private boolean hasContentDescription(AccessibilityNodeInfo root, Set<String> exact) {
        return NodeWalker.any(root, 900, n -> {
            if (!n.isVisibleToUser() || n.getContentDescription() == null) return false;
            for (String e : exact) if (e.equalsIgnoreCase(n.getContentDescription().toString())) return true;
            return false;
        });
    }
    private boolean hasVisibleDescriptionPrefix(AccessibilityNodeInfo root, String prefix) {
        return NodeWalker.any(root, 900, n -> n.isVisibleToUser() && n.getContentDescription() != null
                && n.getContentDescription().toString().regionMatches(true,0,prefix,0,prefix.length()));
    }
    private boolean hasSelectedDescriptionPrefix(AccessibilityNodeInfo root, String prefix) {
        return NodeWalker.any(root, 900, n -> n.isVisibleToUser() && n.getContentDescription() != null
                && (n.isSelected() || n.isChecked())
                && n.getContentDescription().toString().regionMatches(true,0,prefix,0,prefix.length()));
    }

    /** Structural fallback for Facebook, which often lacks stable IDs. */
    private boolean matchesFacebookReelStructure(AccessibilityNodeInfo root) {
        Rect rb = new Rect();
        root.getBoundsInScreen(rb);
        if (rb.width() <= 0 || rb.height() <= 0) return false;
        return NodeWalker.any(root, 1200, n -> isLarge(n, rb, 0.90f, 0.75f)
                && "androidx.recyclerview.widget.RecyclerView".contentEquals(safeClass(n))
                && n.isScrollable() && hasLargeLongClickableButtonWithSurface(n, rb));
    }
    private boolean hasLargeLongClickableButtonWithSurface(AccessibilityNodeInfo parent, Rect rb) {
        return NodeWalker.any(parent, 500, n -> "android.widget.Button".contentEquals(safeClass(n))
                && n.isLongClickable() && isLarge(n, rb, 0.90f, 0.75f) && hasLargeSurface(n, rb));
    }
    private boolean hasLargeSurface(AccessibilityNodeInfo parent, Rect rb) {
        return NodeWalker.any(parent, 300, n -> "android.view.SurfaceView".contentEquals(safeClass(n))
                && isLarge(n, rb, 0.90f, 0.75f));
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
