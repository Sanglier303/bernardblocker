package com.local.focusfence.security;

import org.junit.Test;

import static org.junit.Assert.*;

public class BypassAppDetectorTest {
    @Test public void commonCloneLabelsAreDetected() {
        assertTrue(BypassAppDetector.looksLikeBypassLabel("Parallel Space"));
        assertTrue(BypassAppDetector.looksLikeBypassLabel("Dossier sécurisé"));
        assertTrue(BypassAppDetector.looksLikeBypassLabel("2Accounts"));
        assertTrue(BypassAppDetector.looksLikeBypassLabel("Shelter"));
    }

    @Test public void ordinaryAppsAreNotCloneContainers() {
        assertFalse(BypassAppDetector.looksLikeBypassLabel("Spotify"));
        assertFalse(BypassAppDetector.looksLikeBypassLabel("Google Maps"));
        assertFalse(BypassAppDetector.looksLikeBypassLabel("Bernard Bloqueur"));
    }
}
