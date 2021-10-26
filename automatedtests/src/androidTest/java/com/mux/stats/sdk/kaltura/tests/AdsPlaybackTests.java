package com.mux.stats.sdk.kaltura.tests;


import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public class AdsPlaybackTests extends TestBase {

  static final int PREROLL_AD_PERIOD = 10000;
  static final int BUMPER_AD_PERIOD = 5000;
  static final int CAN_SKIP_AD_AFTER = 5000;


  @Before
  public void init() {
    if (currentTestName.getMethodName()
        .equalsIgnoreCase("testPreRollAdsWhenPlayWhenReadyIsFalse")) {
      playWhenReady = false;
      adUrlToPlay = "http://localhost:5000/ten_sec_ad_vast.xml";
    }
    if (currentTestName.getMethodName()
        .equalsIgnoreCase("testPreRollAndBumperAds")) {
      playWhenReady = true;
      adUrlToPlay = "http://localhost:5000/preroll_and_bumper_vmap.xml";
    }
    super.init();
  }

  @Test
  public void testPreRollAdsWhenPlayWhenReadyIsFalse() {
    try {
      // Wait for ads to finish playing, wait for X seconds
      Thread.sleep(PREROLL_AD_PERIOD);

      // same as start play
      resumePlayer();
      // Wait for preroll to finish
      Thread.sleep(PREROLL_AD_PERIOD);
      // Play X seconds
      Thread.sleep(PLAY_PERIOD_IN_MS);

      MuxStatsEventSequence expected = new MuxStatsEventSequence();
      expected
          .add("playerready", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("viewstart", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("play", 0)
          .add("pause", 0)
          .add("adbreakstart", 0)
          .add("adplay", 0)
          .add("adplaying", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("adended", 10000)
          .add("adbreakend", 0)
          .add("play", 0)
          .add("playing", 0);

      // We need to ignore all the quartile stuff
      MuxStatsEventSequence actual = networkRequest
          .getEventsAsSequence()
          .filterNameOut("renditionchange")
          .filterNameOut("adfirstquartile")
          .filterNameOut("admidpoint")
          .filterNameOut("adthirdquartile");

      MuxStatsEventSequence.compare(expected, actual);
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  @Test
  public void testPreRollAndBumperAds() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      // First ad is 10 second
      Thread.sleep(PREROLL_AD_PERIOD / 2);
      // Pause the ad for 5 seconds
      pausePlayer();
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      // resume the ad playback
      resumePlayer();
      Thread.sleep((PREROLL_AD_PERIOD / 2) + BUMPER_AD_PERIOD + 10000);

      MuxStatsEventSequence expected = new MuxStatsEventSequence();
      expected
          .add("playerready", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("viewstart", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("play", 0)
          .add("pause", 0)
          .add("adbreakstart", 0)
          .add("adplay", 0)
          .add("adplaying", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("adpause", 5000)
          .add("adplay", 3000)
          .add("adplaying", 0)
          .add("adended", 5000)
          .add("play", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("pause", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("adplay", 0)
          .add("adplaying", MuxStatsEventSequence.DELTA_DONT_CARE)
          .add("adended", 5000)
          .add("adbreakend", 0)
          .add("play", 0)
          .add("playing", 0);

      // We need to ignore all the quartile stuff
      MuxStatsEventSequence actual = networkRequest
          .getEventsAsSequence()
          .filterNameOut("renditionchange")
          .filterNameOut("adfirstquartile")
          .filterNameOut("admidpoint")
          .filterNameOut("adthirdquartile");

      MuxStatsEventSequence.compare(expected, actual);
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }
}
