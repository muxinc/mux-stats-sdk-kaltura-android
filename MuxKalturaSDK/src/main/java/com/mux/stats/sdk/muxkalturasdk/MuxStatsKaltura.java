package com.mux.stats.sdk.muxkalturasdk;

import static android.os.SystemClock.elapsedRealtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import com.kaltura.playkit.PKEvent.Listener;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.PlayerEvent.DurationChanged;
import com.kaltura.playkit.PlayerEvent.PlaybackInfoUpdated;
import com.kaltura.playkit.PlayerEvent.PlayheadUpdated;
import com.kaltura.playkit.PlayerEvent.VideoTrackChanged;
import com.kaltura.playkit.plugins.ads.AdEvent;
import com.kaltura.tvplayer.KalturaPlayer;
import com.mux.stats.sdk.core.CustomOptions;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakEndEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakStartEvent;
import com.mux.stats.sdk.core.events.playback.AdEndedEvent;
import com.mux.stats.sdk.core.events.playback.AdErrorEvent;
import com.mux.stats.sdk.core.events.playback.AdFirstQuartileEvent;
import com.mux.stats.sdk.core.events.playback.AdMidpointEvent;
import com.mux.stats.sdk.core.events.playback.AdPauseEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayingEvent;
import com.mux.stats.sdk.core.events.playback.AdThirdQuartileEvent;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.ErrorEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlaybackEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.PlayerData;
import com.mux.stats.sdk.core.model.ViewData;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.IDevice;
import com.mux.stats.sdk.muxstats.INetworkRequest;
import com.mux.stats.sdk.muxstats.MuxErrorException;
import com.mux.stats.sdk.muxstats.MuxStats;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;

public class MuxStatsKaltura extends EventBus {
  protected static final String TAG = "MuxStatsListener";

  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  public enum PlayerState {
    BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
    FINISHED_PLAYING_ADS, INIT, ENDED
  }

  protected PlayerState state;

  private PlayerAdapter playerAdapter;
  private WeakReference<Context> context;

  private boolean hasEverStartedPlaying;

  private int numberOfEventsSent = 0;
  /** Number of {@link PlayingEvent} sent since the View started. */
  private int numberOfPlayEventsSent = 0;
  /** Number of {@link PauseEvent} sent since the View started. */
  private int numberOfPauseEventsSent = 0;

  /** This value is used to detect if the user pressed the pause button when an ad was playing */
  private boolean sendPlayOnStarted = false;
  /**
   * This value is used in the special case of pre roll ads playing. This value will be set to
   * true when a pre roll is detected, and will be reverted back to false after dispatching the
   * AdBreakStart event.
   */
  private boolean missingAdBreakStartEvent = false;

  private String adId;
  private String adCreativeId;

  private MuxStats muxStats;

  public MuxStatsKaltura(Context context, Player player, String playerName,
      CustomerData customerData, CustomOptions options, INetworkRequest networkRequest) {
    this(context, new PlaykitAdapter(player), playerName, customerData, options, networkRequest);
  }

  public MuxStatsKaltura(Context context, KalturaPlayer player, String playerName,
      CustomerData customerData, CustomOptions options, INetworkRequest networkRequest) {
    this(context, new KalturaPlayerAdapter(player), playerName, customerData, options,
        networkRequest);
  }

  private MuxStatsKaltura(Context context, PlayerAdapter playerAdapter, String playerName,
      CustomerData customerData, CustomOptions options, INetworkRequest networkRequest) {
    this.playerAdapter = playerAdapter;
    this.context = new WeakReference<>(context);

    MuxStats.setHostDevice(new MuxDevice(context));
    MuxStats.setHostNetworkApi(networkRequest);

    muxStats = new MuxStats(playerAdapter, playerName, customerData, options);
    addListener(muxStats);

    playerAdapter.addListener(this, PlayerEvent.videoTrackChanged,
        new Listener<VideoTrackChanged>() {
          @Override
          public void onEvent(VideoTrackChanged event) {
            // NOT SEEING THIS GUY GETTING CALLED . . .
            playerAdapter.setSourceWidth(event.newTrack.getWidth());
            playerAdapter.setSourceHeight(event.newTrack.getHeight());
            playerAdapter.setBitrate(event.newTrack.getBitrate());

            dispatch(new RenditionChangeEvent(null));
          }
        }
    );

    playerAdapter.addListener(this, PlayerEvent.playbackInfoUpdated,
        new Listener<PlaybackInfoUpdated>() {
          @Override
          public void onEvent(PlaybackInfoUpdated event) {
            playerAdapter.setSourceWidth((int) event.playbackInfo.getVideoWidth());
            playerAdapter.setSourceHeight((int) event.playbackInfo.getVideoHeight());
            playerAdapter.setBitrate(event.playbackInfo.getVideoBitrate());

            dispatch(new RenditionChangeEvent(null));
          }
        }
    );

    playerAdapter.addListener(this, PlayerEvent.durationChanged,
        new Listener<DurationChanged>() {
          @Override
          public void onEvent(DurationChanged event) {
            playerAdapter.setSourceDuration(event.duration);
          }
        }
    );

    playerAdapter.addListener(this, PlayerEvent.playheadUpdated,
        new Listener<PlayheadUpdated>() {
          @Override
          public void onEvent(PlayheadUpdated event) {
            playerAdapter.setCurrentPosition(event.position);
            dispatch(new TimeUpdateEvent(null));
          }
        }
    );

    playerAdapter.addListener(this, PlayerEvent.stateChanged, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());

      switch (event.newState) {
        case LOADING:
          playerAdapter.setReady(false);
          break;
        case BUFFERING:
          playerAdapter.setBuffering(true);
          playerAdapter.setReady(false);
          buffering();
          break;
        case READY:
          playerAdapter.setReady(true);
          break;
        default:
          playerAdapter.setBuffering(false);
          playerAdapter.setReady(false);
          break;
      }
    });

    playerAdapter.addListener(this, PlayerEvent.error, event -> {
      Log.e(TAG, "Playback error "+event.error.toString()+" "+event.error.message);

      if(event.error.isFatal()) {
        PlayerData pd = new PlayerData();
        pd.setPlayerErrorCode(event.error.errorType.toString());
        pd.setPlayerErrorMessage(event.error.message);
        dispatch(new ErrorEvent(pd));
      }
    });

    playerAdapter.addListener(this, PlayerEvent.pause, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      playerAdapter.setPaused(true);
      pause();
    });

    playerAdapter.addListener(this, PlayerEvent.play, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      playerAdapter.setPaused(false);
      play();
    });

    playerAdapter.addListener(this, PlayerEvent.playing, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      playerAdapter.setPaused(false);
      playing();
    });

    playerAdapter.addListener(this, PlayerEvent.seeking, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      seeking();
    });

    playerAdapter.addListener(this, PlayerEvent.seeked, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      seeked();
    });

    playerAdapter.addListener(this, PlayerEvent.ended, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      ended();
    });

    playerAdapter.addListener(this, PlayerEvent.stopped, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      ended();
    });

    playerAdapter.addListener(this, PlayerEvent.replay, event -> {
      playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
      playerAdapter.setPaused(false);
      play();
    });

    // Ads event listeners
    playerAdapter.addListener(this, AdEvent.loaded, event -> {
      if(event.adInfo != null) {
        adId = event.adInfo.getAdId();
        adCreativeId = event.adInfo.getCreativeId();
      } else {
        adId = null;
        adCreativeId = null;
      }
    });

    playerAdapter.addListener(this, AdEvent.error, event -> {
      PlaybackEvent e = new AdErrorEvent(null);
      setupAdViewData(e);
      dispatch(e);
    });

    playerAdapter.addListener(this, AdEvent.contentPauseRequested, event -> {
      // Send pause event if we are currently playing or preparing to play content
      if(state == PlayerState.INIT) {
        play();
      }

      if (state == PlayerState.PLAY || state == PlayerState.PLAYING) {
        pause();
      }

      sendPlayOnStarted = false;
      state = PlayerState.PLAYING_ADS;

      if (!playerAdapter.getAutoPlay() && playerAdapter.getCurrentPosition() == 0) {
        // This is preroll ads when play when ready is set to false, we need to ignore these events
        missingAdBreakStartEvent = true;
      } else {
        dispatchAdPlaybackEvent(new AdBreakStartEvent(null));
        dispatchAdPlaybackEvent(new AdPlayEvent(null));
      }
    });

    playerAdapter.addListener(this, AdEvent.started, event -> {
      if(state != PlayerState.PLAYING_ADS) {
        play();
        pause();
        state = PlayerState.PLAYING_ADS;
      }

      // On the first STARTED, do not send AdPlay, as it was handled in
      // CONTENT_PAUSE_REQUESTED
      if (sendPlayOnStarted) {
        dispatchAdPlaybackEvent(new AdPlayEvent(null));
      } else {
        sendPlayOnStarted = true;
      }
      dispatchAdPlaybackEvent(new AdPlayingEvent(null));
    });

    playerAdapter.addListener(this, AdEvent.firstQuartile, event -> {
      dispatchAdPlaybackEvent(new AdFirstQuartileEvent(null));
    });

    playerAdapter.addListener(this, AdEvent.midpoint, event -> {
      dispatchAdPlaybackEvent(new AdMidpointEvent(null));
    });

    playerAdapter.addListener(this, AdEvent.thirdQuartile, event -> {
      dispatchAdPlaybackEvent(new AdThirdQuartileEvent(null));
    });

    playerAdapter.addListener(this, AdEvent.completed, event -> {
      dispatchAdPlaybackEvent(new AdEndedEvent(null));
    });

    playerAdapter.addListener(this, AdEvent.contentResumeRequested, event -> {
      state = PlayerState.FINISHED_PLAYING_ADS;
      dispatchAdPlaybackEvent(new AdBreakEndEvent(null));
      playing();
    });

    playerAdapter.addListener(this, AdEvent.paused, event -> {
      if (!playerAdapter.getAutoPlay() && playerAdapter.getCurrentPosition() == 0) {
        // This is preroll ads when play when ready is set to false, we need to ignore these events
      } else {
        dispatchAdPlaybackEvent(new AdPauseEvent(null));
      }
    });

    playerAdapter.addListener(this, AdEvent.resumed, event -> {
      if (missingAdBreakStartEvent) {
        // This is special case when we have ad preroll and play when ready is set to false
        // in that case we need to dispatch AdBreakStartEvent first and resume the playback.
        dispatchAdPlaybackEvent(new AdBreakStartEvent(null));
        dispatchAdPlaybackEvent(new AdPlayEvent(null));
        missingAdBreakStartEvent = false;
      } else {
        dispatchAdPlaybackEvent(new AdPlayEvent(null));
        dispatchAdPlaybackEvent(new AdPlayingEvent(null));
      }
    });

    // In case it was playing before rigged up
    // We have to simulate all the events we expect to see here, even though not ideal
    playerAdapter.setCurrentPosition(playerAdapter.getInnerCurrentPosition());
    if(playerAdapter.isBuffering()) {
      play();
      buffering();
    } else if(playerAdapter.isReady()) {
      // This is "kind of" ugly, but it's not clear how we can distinguish between a video that
      // was already running when we connected to one that is seeking at the start
      hasEverStartedPlaying = true;
      play();
      buffering();
      if(!playerAdapter.isInnerPaused()) {
        playing();
      }
    } else {
      state = PlayerState.INIT;
    }
  }

  private void setupAdViewData(PlaybackEvent event) {
    ViewData viewData = new ViewData();
    if (playerAdapter.getCurrentPosition() == 0) {
      if (adId != null) {
        viewData.setViewPrerollAdId(adId);
        viewData.setViewPrerollCreativeId(adCreativeId);
      }
    }
    event.setViewData(viewData);
  }

  private void dispatchAdPlaybackEvent(PlaybackEvent event) {
    if(playerAdapter == null) {
      // This shouldn't happen once we properly detach listeners when tearing down
      return;
    }
    setupAdViewData(event);
    dispatch(event);
  }

  /**
   * Used by the tests to inspect the state
   * @return
   */
  public PlayerAdapter getPlayerAdapter() {
    return playerAdapter;
  }

  /**
   * If set to true the underlying {@link MuxStats} logs will be output in the logcat.
   *
   * @param enable if set to true the log will be  printed in logcat.
   * @param verbose if set to true each event will be printed with all stats, this output can be
   *                overwhelming
   */
  public void enableMuxCoreDebug(boolean enable, boolean verbose) {
    muxStats.allowLogcatOutput(enable, verbose);
  }

  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerData data) {
    muxStats.setCustomerData(data);
  }

  @SuppressWarnings("unused")
  public CustomerData getCustomerData() {
    return muxStats.getCustomerData();
  }

  @SuppressWarnings("unused")
  public void videoChange(CustomerVideoData customerVideoData) {
    // Reset the state to avoid unwanted rebuffering events
    state = PlayerState.INIT;
    resetInternalStats();
    muxStats.videoChange(customerVideoData);
  }

  @SuppressWarnings("unused")
  public void programChange(CustomerVideoData customerVideoData) {
    resetInternalStats();
    muxStats.programChange(customerVideoData);
  }

  @SuppressWarnings("unused")
  public void orientationChange(MuxSDKViewOrientation orientation) {
    muxStats.orientationChange(orientation);
  }

  @SuppressWarnings("unused")
  public void setPlayerSize(int width, int height) {
    muxStats.setPlayerSize(width, height);
  }

  /**
   * Convert given number of actual pixels to android's density independent pixels
   *
   * @param n
   * @return
   */
  private int pxToDp(int n) {
    Context c = context.get();
    if(c != null) {
      DisplayMetrics displayMetrics = c.getResources().getDisplayMetrics();
      return (int) Math.ceil(n / displayMetrics.density);
    }
    return 0;
  }

  @SuppressWarnings("unused")
  public void setScreenSize(int width, int height) {
    muxStats.setScreenSize(pxToDp(width), pxToDp(height));
  }

  @SuppressWarnings("unused")
  public void error(MuxErrorException e) {
    muxStats.error(e);
  }

  @SuppressWarnings("unused")
  public void setAutomaticErrorTracking(boolean enabled) {
    muxStats.setAutomaticErrorTracking(enabled);
  }

  public void release() {
    if(playerAdapter != null) {
      playerAdapter.detach();
    }

    muxStats.release();
    muxStats = null;
    playerAdapter.release();
    playerAdapter = null;
  }

  @Override
  public void dispatch(IEvent event) {
    if (playerAdapter != null && muxStats != null) {
      numberOfEventsSent++;
      if (event.getType().equalsIgnoreCase(PlayEvent.TYPE)) {
        numberOfPlayEventsSent++;
      }
      if (event.getType().equalsIgnoreCase(PauseEvent.TYPE)) {
        numberOfPauseEventsSent++;
      }
      super.dispatch(event);
    }
  }

  private void buffering() {
    if (state == PlayerState.REBUFFERING || state == PlayerState.SEEKING || state == PlayerState.SEEKED) {
      // ignore
      return;
    }
    // If we are going from playing to buffering then this is rebuffer event
    if (state == PlayerState.PLAYING) {
      rebufferingStarted();
      return;
    }
    // This is initial buffering event before playback starts
    state = PlayerState.BUFFERING;
    dispatch(new TimeUpdateEvent(null));
  }

  private void pause() {
    if (state == PlayerState.SEEKED && numberOfPauseEventsSent > 0) {
      // No pause event after seeked
      return;
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }
    if (state == PlayerState.SEEKING) {
      seeked();
      return;
    }

    if(state == PlayerState.PAUSED) {
      return;
    }

    state = PlayerState.PAUSED;
    dispatch(new PauseEvent(null));
  }

  private void play() {
    if(state == PlayerState.PLAY || state == PlayerState.PLAYING) {
      return;
    }

    state = PlayerState.PLAY;
    dispatch(new PlayEvent(null));
  }

  private void playing() {
    if (state == PlayerState.PAUSED || state == PlayerState.FINISHED_PLAYING_ADS) {
      play();
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }

    if(state == PlayerState.PLAYING) {
      return;
    }

    if(!hasEverStartedPlaying) {
      if(playerAdapter != null && playerAdapter.getCurrentPosition() > 250) {
        // Fake seeking at the start if our start position is not zero
        seeking();
        seeked();
      }
      hasEverStartedPlaying = true;
    }

    state = PlayerState.PLAYING;
    dispatch(new PlayingEvent(null));
  }

  protected void rebufferingStarted() {
    if(state == PlayerState.REBUFFERING || state == PlayerState.BUFFERING) {
      return;
    }
    state = PlayerState.REBUFFERING;
    dispatch(new RebufferStartEvent(null));
  }

  protected void rebufferingEnded() {
    if(state == PlayerState.REBUFFERING) {
      dispatch(new RebufferEndEvent(null));
    }
  }

  private void seeking() {
    if (state == PlayerState.PLAYING) {
      dispatch(new PauseEvent(null));
    }
    state = PlayerState.SEEKING;
    dispatch(new SeekingEvent(null));
  }

  private void seeked() {
    if (state == PlayerState.SEEKING) {
      dispatch(new SeekedEvent(null));
      state = PlayerState.SEEKED;
    }
  }

  private void ended() {
    if(state != PlayerState.PAUSED) {
      dispatch(new PauseEvent(null));
    }

    dispatch(new EndedEvent(null));
    state = PlayerState.ENDED;
  }

  private void internalError(Exception error) {
    if (error instanceof MuxErrorException) {
      MuxErrorException muxError = (MuxErrorException) error;
      dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
    } else {
      dispatch(new InternalErrorEvent(ERROR_UNKNOWN,error.getClass().getCanonicalName() + " - " + error.getMessage()));
    }
  }

  private void resetInternalStats() {
    numberOfPauseEventsSent = 0;
    numberOfPlayEventsSent = 0;
    numberOfEventsSent = 0;
  }

  // TODO this guy is copied from the Exoplayer one - should be in shared code
  // The one diff is the LIBRARY_ID string to ID the player type (Kaltura vs Exoplayer)
  // Would require MuxCore to have access to the android api

  /**
   * Basic device details such as OS version, vendor name and etc. Instances of this class are used
   * by {@link MuxStats} to interface with the device.
   */
  static class MuxDevice implements IDevice {
    private static final String LIBRARY_ID = "Kaltura";

    static final String CONNECTION_TYPE_CELLULAR = "cellular";
    static final String CONNECTION_TYPE_WIFI = "wifi";
    static final String CONNECTION_TYPE_WIRED = "wired";
    static final String CONNECTION_TYPE_OTHER = "other";

    static final String MUX_DEVICE_ID = "MUX_DEVICE_ID";

    protected WeakReference<Context> contextRef;
    private String deviceId;
    private String appName = "";
    private String appVersion = "";

    /**
     * Basic constructor.
     *
     * @param ctx activity context, we use this to access different system services, like {@link
     *            ConnectivityManager}, or {@link PackageInfo}.
     */
    MuxDevice(Context ctx) {
      SharedPreferences sharedPreferences = ctx
          .getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE);
      deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null);
      if (deviceId == null) {
        deviceId = UUID.randomUUID().toString();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(MUX_DEVICE_ID, deviceId);
        editor.commit();
      }
      contextRef = new WeakReference<>(ctx);
      try {
        PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        appName = pi.packageName;
        appVersion = pi.versionName;
      } catch (PackageManager.NameNotFoundException e) {
        MuxLogger.d(TAG, "could not get package info");
      }
    }

    @Override
    public String getHardwareArchitecture() {
      return Build.HARDWARE;
    }

    @Override
    public String getOSFamily() {
      return "Android";
    }

    @Override
    public String getOSVersion() {
      return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
    }

    @Override
    public String getManufacturer() {
      return Build.MANUFACTURER;
    }

    @Override
    public String getModelName() {
      return Build.MODEL;
    }

    @Override
    public String getPlayerVersion() {
      return com.kaltura.playkit.BuildConfig.VERSION_NAME;
    }

    @Override
    public String getDeviceId() {
      return deviceId;
    }

    @Override
    public String getAppName() {
      return appName;
    }

    @Override
    public String getAppVersion() {
      return appVersion;
    }

    @Override
    public String getPluginName() {
      return BuildConfig.MUX_PLUGIN_NAME;
    }

    @Override
    public String getPluginVersion() {
      return BuildConfig.MUX_PLUGIN_VERSION;
    }

    @Override
    public String getPlayerSoftware() {
      return LIBRARY_ID;
    }

    /**
     * Determine the correct network connection type.
     *
     * @return the connection type name.
     */
    @Override
    public String getNetworkConnectionType() {
      // Checking internet connectivity
      Context context = contextRef.get();
      if (context == null) {
        return null;
      }
      ConnectivityManager connectivityMgr = (ConnectivityManager) context
          .getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo activeNetwork = null;
      if (connectivityMgr != null) {
        activeNetwork = connectivityMgr.getActiveNetworkInfo();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          NetworkCapabilities nc = connectivityMgr
              .getNetworkCapabilities(connectivityMgr.getActiveNetwork());
          if (nc == null) {
            MuxLogger.d(TAG, "ERROR: Failed to obtain NetworkCapabilities manager !!!");
            return null;
          }
          if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return CONNECTION_TYPE_WIRED;
          } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return CONNECTION_TYPE_WIFI;
          } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return CONNECTION_TYPE_CELLULAR;
          } else {
            return CONNECTION_TYPE_OTHER;
          }
        } else {
          if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
            return CONNECTION_TYPE_WIRED;
          } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            return CONNECTION_TYPE_WIFI;
          } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            return CONNECTION_TYPE_CELLULAR;
          } else {
            return CONNECTION_TYPE_OTHER;
          }
        }
      }
      return null;
    }

    @Override
    public long getElapsedRealtime() {
      return elapsedRealtime();
    }

    /**
     * Print underlying {@link MuxStats} SDK messages on the logcat. This will only be called if
     * {@link #enableMuxCoreDebug(boolean, boolean)} is called with first argument as true
     *
     * @param tag tag to be used.
     * @param msg message to be printed.
     */
    @Override
    public void outputLog(String tag, String msg) {
      Log.v(tag, msg);
    }
  }
}
