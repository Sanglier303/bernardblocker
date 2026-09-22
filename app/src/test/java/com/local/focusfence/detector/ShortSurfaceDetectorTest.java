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


    @Test public void visibleBottomNavUtilityButtonsDoNotExemptHomeFeed(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("sticky_header_list","direct_tab","profile_tab","creation_tab","search_tab"),
                        ids("feed_tab"), ids(), true));
    }

    @Test public void onlySelectedDirectTabExemptsInboxNavigation(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("sticky_header_list","direct_tab"), ids("feed_tab"), ids(), true));
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("direct_tab"), ids("direct_tab"), ids(), true));
    }

    @Test public void instagramExploreIsDetected(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_EXPLORE,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("explore_action_bar","action_bar_search_edit_text"), ids("search_tab"), ids(), true));
    }

    @Test public void instagramInboxWinsOverStaleFeedMarkers(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("inbox_refreshable_thread_list_recyclerview","sticky_header_list"),
                ids("direct_tab"), ids(), true));
    }

    @Test public void instagramConversationIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("row_thread_composer_edittext","message_list"), ids(), ids(), true));
    }

    @Test public void instagramCommentsAreExemptEvenOverFeed(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("comments_bottom_sheet","layout_comment_thread_edittext","sticky_header_list"),
                ids("feed_tab"), ids(), true));
    }

    @Test public void instagramShareSheetIsExemptEvenOverReel(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("direct_private_share_container_view","clips_viewer_view_pager"),
                ids("clips_tab"), ids(), true));
    }

    @Test public void instagramCreationIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("gallery_grid_item_thumbnail","creation_next_button"), ids("creation_tab"), ids(), true));
    }

    @Test public void instagramNotificationsAreExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("activity_feed_list","activity_feed_newsfeed_story_row"), ids(), ids(), true));
    }

    @Test public void instagramProfileIsExemptEvenIfHomeTabStateLingers(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("profile_header_container","sticky_header_list"), ids("profile_tab"), ids(), true));
    }

    @Test public void instagramFollowersListIsExempt(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("follow_list_username","follow_list_container"), ids(), ids(), true));
    }

    @Test public void instagramSinglePostDetailRemainsAnAllowedUtility(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                        ids("action_bar_button_back","row_feed_profile_header","row_feed_photo_imageview"),
                        ids("feed_tab"), ids(), true));
    }

    @Test public void instagramHashtagSearchCountsAsExplore(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_EXPLORE,
                ShortSurfaceDetector.classifyInstagramForTest(
                        ids("search_results_list","row_hashtag_container"), ids("search_tab"), ids(), true));
    }

    @Test public void unknownInstagramSurfaceIsNotEvidenceOfFeed(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                        ids("some_future_meta_surface_id"), ids(), ids(), true));
    }

    @Test public void genericBackNavigationStillExemptsUtilityScreen(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(
                ids("action_bar_button_back","some_future_settings_id"), ids(), ids(), true));
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

    @Test public void notificationButtonAloneCannotExemptFeed(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,ShortSurfaceDetector.classifyInstagramForTest(
                ids("notification_tab","sticky_header_list"),ids("feed_tab"),ids(),true));
    }
    @Test public void selectedNotificationTabRemainsSafe(){
        assertNull(ShortSurfaceDetector.classifyInstagramForTest(ids("notification_tab"),ids("notification_tab"),ids(),true));
    }
    @Test public void genericCaptionDoesNotDisableFeedDetection(){
        assertEquals(ShortSurfaceDetector.Surface.INSTAGRAM_FEED,ShortSurfaceDetector.classifyInstagramForTest(
                ids("caption_text_view","sticky_header_list"),ids("feed_tab"),ids(),true));
    }
}
