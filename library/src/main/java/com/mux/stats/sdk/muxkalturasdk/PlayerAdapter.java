package com.mux.stats.sdk.muxkalturasdk;

import android.util.DisplayMetrics;
import android.view.View;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKEvent.Listener;
import com.kaltura.playkit.Player;
import com.mux.stats.sdk.muxstats.IPlayerListener;
import java.util.ArrayList;

public abstract class PlayerAdapter implements IPlayerListener {
  protected int sourceWidth;
  protected int sourceHeight;
  protected long sourceDuration;

  protected long bitrate;

  protected boolean paused;
  protected boolean buffering;
  protected boolean ready;

  private long playbackPosition;

  private ArrayList<PKEvent.Listener> listeners;

  protected PlayerAdapter() {
    listeners = new ArrayList<>();
  }

  abstract <E extends PKEvent> PKEvent.Listener addListener(Object groupId, Class<E> type, PKEvent.Listener<E> listener);
  abstract PKEvent.Listener addListener(Object groupId, Enum type, PKEvent.Listener listener);

  abstract void removeListener(PKEvent.Listener listener);

  protected final void attachListener(PKEvent.Listener listener) {
    listeners.add(listener);
  }

  public final void detach() {
    for(Listener l: listeners) {
      removeListener(l);
    }

    listeners.clear();
  }

  public final void setSourceHeight(int sourceHeight) {
    this.sourceHeight = sourceHeight;
  }

  public final void setSourceWidth(int sourceWidth) {
    this.sourceWidth = sourceWidth;
  }

  @Override
  public final Integer getSourceWidth() {
    return sourceWidth;
  }

  @Override
  public final Integer getSourceHeight() {
    return sourceHeight;
  }

  void setSourceDuration(long sourceDuration) {
    this.sourceDuration = sourceDuration;
  }

  @Override
  public final Long getSourceDuration() {
    return sourceDuration;
  }

  public void setBitrate(long bitrate) {
    this.bitrate = bitrate;
  }

  @Override
  public final Integer getSourceAdvertisedBitrate() {
    return (int) this.bitrate;
  }

  protected abstract View getPlayerView();

  private static final int pxToDp(View view, int px) {
    DisplayMetrics displayMetrics = view.getResources().getDisplayMetrics();
    return (int) Math.ceil(px / displayMetrics.density);
  }

  @Override
  public final int getPlayerViewWidth() {
    View v = getPlayerView();
    if(v != null) {
      return pxToDp(v, v.getWidth());
    }

    return 0;
  }

  @Override
  public final int getPlayerViewHeight() {
    View v = getPlayerView();
    if(v != null) {
      return pxToDp(v, v.getHeight());
    }

    return 0;
  }

  public void setCurrentPosition(long playbackPosition) {
    this.playbackPosition = playbackPosition;
  }

  @Override
  public final long getCurrentPosition() {
    return playbackPosition;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getPlayerProgramTime() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getPlayerManifestNewestTime() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getVideoHoldback() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getVideoPartHoldback() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getVideoPartTargetDuration() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  /**
   * This method is not supported for Kaltura at this time
   * @return null
   * @deprecated This method is not supported for Kaltura at this time
   */
  @Deprecated
  @Override
  public Long getVideoTargetDuration() {
    // TODO: Kaltura is also based on ExoPlayer so we should be able to get at latency metrics
    //   if we can get past all the abstraction
    return null;
  }

  // Actually query the underlying player - must be called on main thread
  public abstract long getInnerCurrentPosition();

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public void setBuffering(boolean buffering) {
    this.buffering = buffering;
  }

  // Note this follows the slightly esoteric IPlayerListener concept of paused
  @Override
  public final boolean isPaused() {
    return paused || buffering;
  }

  public final boolean isInnerPaused() {
    return paused;
  }

  @Override
  public final boolean isBuffering() {
    return buffering;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  public boolean isReady() {
    return ready;
  }

  public abstract boolean isPlaying();

  public abstract void release();

  public abstract boolean getAutoPlay();
}
