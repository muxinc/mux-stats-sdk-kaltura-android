package com.mux.stats.sdk.kaltura.tests.ui;

import android.graphics.Point;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKEvent.Listener;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaEntry.MediaEntryType;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PKPluginConfigs;
import com.kaltura.playkit.PKRequestConfig;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.plugins.ads.AdEvent;
import com.kaltura.playkit.plugins.ima.IMAConfig;
import com.kaltura.playkit.plugins.ima.IMAPlugin;
import com.kaltura.tvplayer.KalturaBasicPlayer;
import com.kaltura.tvplayer.KalturaPlayer;
import com.kaltura.tvplayer.PlayerInitOptions;
import com.mux.stats.sdk.core.CustomOptions;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.kaltura.BuildConfig;
import com.mux.stats.sdk.kaltura.R;
import com.mux.stats.sdk.kaltura.example.Constants;
import com.mux.stats.sdk.kaltura.tests.MockNetworkRequest;
import com.mux.stats.sdk.muxkalturasdk.MuxStatsKaltura;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimplePlayerActivity extends AppCompatActivity {
  static final String TAG = "SimplePlayerActivity";

  protected static final String PLAYBACK_CHANNEL_ID = "playback_channel";
  protected static final int PLAYBACK_NOTIFICATION_ID = 1;
  protected static final String ARG_URI = "uri_string";
  protected static final String ARG_TITLE = "title";
  protected static final String ARG_START_POSITION = "start_position";

  String videoTitle = "Test Video";
  String urlToPlay;
  KalturaPlayer player;
  MuxStatsKaltura muxStats;
  String loadedAdTag;
  boolean playWhenReady = true;
  MockNetworkRequest mockNetwork;
  AtomicBoolean onResumedCalled = new AtomicBoolean(false);
  MediaSessionCompat mediaSessionCompat;
  long playbackStartPosition = 0;

  Lock activityLock = new ReentrantLock();
  Condition playbackEnded = activityLock.newCondition();
  Condition playbackStarted = activityLock.newCondition();
  Condition playbackBuffering = activityLock.newCondition();
  Condition activityClosed = activityLock.newCondition();
  Condition activityInitialized = activityLock.newCondition();
  ArrayList<String> addAllowedHeaders = new ArrayList<>();

  ArrayList<PKEvent.Listener> listeners;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    listeners = new ArrayList<>();

    // Enter fullscreen
    hideSystemUI();
    setContentView(R.layout.activity_simple_test);
    disableUserActions();

    findViewById(R.id.activity_simple_test_play_button).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if(player != null) {
          player.play();
        }
      }
    });

    findViewById(R.id.activity_simple_test_pause_button).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if(player != null) {
          player.pause();
        }
      }
    });
  }

  private <E extends PKEvent> void addPlayerListener(Object groupId, Class<E> type, PKEvent.Listener<E> listener) {
    player.addListener(groupId, type, listener);
    listeners.add(listener);
  }

  private void addPlayerListener(Object groupId, Enum type, PKEvent.Listener listener) {
    player.addListener(groupId, type, listener);
    listeners.add(listener);
  }

  private void removeListeners() {
    if(player != null) {
      for(Listener l: listeners) {
        player.removeListener(l);
      }
    }

    listeners.clear();
  }

  @Override
  protected void onResume() {
    super.onResume();
    onResumedCalled.set(true);
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    removeListeners();

    if (muxStats != null) {
      muxStats.release();
    }

    signalActivityClosed();
    super.onDestroy();
  }

  public void initKalturaPlayer() {
    PlayerInitOptions playerInitOptions = new PlayerInitOptions(Constants.OVP_PARTNER_ID);
    playerInitOptions.setAutoPlay(false);
    playerInitOptions.setAllowCrossProtocolEnabled(true);
    playerInitOptions.setAllowClearLead(true);


    if(loadedAdTag != null) {
      PKPluginConfigs pkPluginConfigs = new PKPluginConfigs();
      IMAConfig adsConfig = getAdsConfig(loadedAdTag);
      pkPluginConfigs.setPluginConfig(IMAPlugin.factory.getName(), adsConfig);

      playerInitOptions.setPluginConfigs(pkPluginConfigs);
    }

    KalturaPlayer player = KalturaBasicPlayer.create(this, playerInitOptions);
    player.setPlayerView(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

    ((ViewGroup)findViewById(R.id.activity_simple_test_content_view)).addView(player.getPlayerView());

    this.player = player;

    addPlayerListener(this, PlayerEvent.stateChanged, event -> {
      switch (event.newState) {
        case LOADING:
        case BUFFERING:
          signalPlaybackBuffering();
          break;
        case READY:
          break;
        default:
          break;
      }
    });

    addPlayerListener(this, PlayerEvent.playing, event -> {
      signalPlaybackStarted();
    });

    addPlayerListener(this, PlayerEvent.ended, event -> {
      signalPlaybackEnded();
    });

    addPlayerListener(this, AdEvent.started, event -> {
      signalPlaybackStarted();
    });
  }

  private IMAConfig getAdsConfig(String loadedAdTagUri) {
    ArrayList<String> videoMimeTypes = new ArrayList<>();
    videoMimeTypes.add("video/mp4");
    videoMimeTypes.add("application/x-mpegURL");
    videoMimeTypes.add("application/dash+xml");
    return new IMAConfig().setAdTagUrl(loadedAdTagUri).setVideoMimeTypes(videoMimeTypes).enableDebugMode(true).setAlwaysStartWithPreroll(true).setAdLoadTimeOut(8);
  }

  public void startPlayback() {
//    player.setAutoPlay(false);
//
//    boolean wasPlaying = false;
//    if(player.isPlaying()) {
//      wasPlaying = true;
//      player.pause();
//    }

    PKMediaEntry entry = new PKMediaEntry();
    entry.setId("testvid_"+urlToPlay);
    entry.setMediaType(MediaEntryType.Vod);
    LinkedList<PKMediaSource> sources = new LinkedList<>();
    PKMediaSource source = new PKMediaSource();
    source.setId("testvid_"+urlToPlay);
    source.setUrl(urlToPlay);
    sources.add(source);
    entry.setSources(sources);

    player.setMedia(entry, playbackStartPosition);
    player.setPreload(true);
    player.setAutoPlay(playWhenReady);
//    if(wasPlaying && !playWhenReady) {
//      player.play();
//    }
  }

  public KalturaPlayer getPlayer() {
    return player;
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;

    if(getPlayer() != null) {
      getPlayer().setAutoPlay(this.playWhenReady);
    }
  }

  public void setVideoTitle(String title) {
    videoTitle = title;
  }

  public void setAdTag(String tag) {
    loadedAdTag = tag;
  }

  public void setUrlToPlay(String url) {
    urlToPlay = url;
  }

  public void setPlaybackStartPosition(long position) {
    playbackStartPosition = position;
  }

  public void hideSystemUI() {
    // Enables regular immersive mode.
    // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
    // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      View decorView = getWindow().getDecorView();
      decorView.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              // Set the content to appear under the system bars so that the
              // content doesn't resize when the system bars hide and show.
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              // Hide the nav bar and status bar
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  // Shows the system bars by removing all the flags
  // except for the ones that make the content appear under the system bars.
  public void showSystemUI() {
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  public MuxStatsKaltura getMuxStats() {
    return muxStats;
  }

  public void initMuxStats() {
    // Mux details
    CustomerPlayerData customerPlayerData = new CustomerPlayerData();
    if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
      customerPlayerData.setEnvironmentKey(BuildConfig.INSTRUMENTATION_TEST_ENVIRONMENT_KEY);
    } else {
      customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
    }
    CustomerVideoData customerVideoData = new CustomerVideoData();
    customerVideoData.setVideoTitle(videoTitle);
    mockNetwork = new MockNetworkRequest();
    CustomerData customerData = new CustomerData(customerPlayerData, customerVideoData, null);
    muxStats = new MuxStatsKaltura(this, player, "demo-player", customerData, new CustomOptions().setSentryEnabled(false), mockNetwork);
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    muxStats.setScreenSize(size.x, size.y);
//    muxStats.setPlayerView(findViewById(R.id.activity_main_content_view));
    muxStats.enableMuxCoreDebug(true, false);
  }

  public MockNetworkRequest getMockNetwork() {
    return mockNetwork;
  }

  public boolean waitForPlaybackToFinish(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackEnded.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForActivityToInitialize() {
    if (!onResumedCalled.get()) {
      try {
        activityLock.lock();
        activityInitialized.await();
        activityLock.unlock();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean waitForPlaybackToStart(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForPlaybackToStartBuffering() {
    if (!muxStats.getPlayerAdapter().isBuffering()) {
      try {
        activityLock.lock();
        playbackBuffering.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        activityLock.unlock();
      }
    }
  }

  public void waitForActivityToClose() {
    try {
      activityLock.lock();
      activityClosed.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      activityLock.unlock();
    }
  }

  public void signalPlaybackStarted() {
    activityLock.lock();
    playbackStarted.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackBuffering() {
    activityLock.lock();
    playbackBuffering.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackEnded() {
    activityLock.lock();
    playbackEnded.signalAll();
    activityLock.unlock();
  }

  public void signalActivityClosed() {
    activityLock.lock();
    activityClosed.signalAll();
    activityLock.unlock();
  }

  private void disableUserActions() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }

  private void enableUserActions() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }
}
