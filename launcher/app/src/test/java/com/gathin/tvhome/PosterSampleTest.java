package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PosterSampleTest {
    @Test public void fullHdPosterDownsamplesToAtLeastTheTarget() {
        assertEquals(4, PosterLoader.sampleSize(1920, 1080, 320, 180));
    }

    @Test public void imageAlreadyAtOrNearTargetIsNotSampled() {
        assertEquals(1, PosterLoader.sampleSize(320, 180, 320, 180));
        assertEquals(1, PosterLoader.sampleSize(500, 300, 320, 180));
    }

    @Test public void tallPosterIsLimitedByItsShortSide() {
        assertEquals(2, PosterLoader.sampleSize(800, 1200, 320, 180));
        assertEquals(4, PosterLoader.sampleSize(1000, 1500, 250, 250));
    }

    @Test public void badDimensionsGiveOne() {
        assertEquals(1, PosterLoader.sampleSize(0, 100, 320, 180));
        assertEquals(1, PosterLoader.sampleSize(100, -1, 320, 180));
        assertEquals(1, PosterLoader.sampleSize(1000, 1000, 0, 180));
    }
}
