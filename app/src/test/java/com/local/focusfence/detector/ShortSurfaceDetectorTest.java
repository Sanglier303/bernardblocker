package com.local.focusfence.detector;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ShortSurfaceDetectorTest {
    private static Set<String> ids(String... values){return new HashSet<>(Arrays.asList(values));}

    @Test public void instagramReelsCurrentIdsAreDetected(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_REELS,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("clips_viewer_root"), ids(), ids(), true));
    }

    @Test public void instagram444HomeIsFeedNotStory(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("sticky_header_list","reels_tray_container"), ids("feed_tab"), ids(), true));
    }

    @Test public void instagramExploreIsDetected(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_EXPLORE,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("explore_action_bar","action_bar_search_edit_text"), ids("search_tab"), ids(), true));
    }

    @Test public void instagramInboxIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("inbox_refreshable_thread_list_recyclerview"), ids("direct_tab"), ids(), true));
    }

    @Test public void instagramConversationIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("row_thread_composer_edittext"), ids(), ids(), true));
    }

    @Test public void instagramProfileIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids(), ids("profile_tab"), ids(), true));
    }

    @Test public void unknownInstagramFailsClosedToFeed(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,
                ShortSurfaceDetector.classifyInstagramForTest(ids("some_future_meta_id"), ids(), ids(), true));
    }

    @Test public void reelOpenedFromDmStillCounts(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_REELS,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("clips_viewer_view_pager","row_thread_composer_edittext"), ids(), ids(), true));
    }

    @Test public void fullscreenStoryIsDetected(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_STORIES,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("reel_viewer_root","reel_viewer_content_layout"), ids(), ids(), true));
    }
}
