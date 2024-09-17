package com.mux.stats.sdk.muxkalturasdk;

import android.view.View;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKEvent.Listener;
import com.kaltura.playkit.Player;
import com.kaltura.tvplayer.KalturaPlayer;
import java.lang.ref.WeakReference;

class KalturaPlayerAdapter extends PlayerAdapter {

  private WeakReference<KalturaPlayer> player;

  KalturaPlayerAdapter(KalturaPlayer player) {
    this.player = new WeakReference<>(player);

    long bufferedPos = player.getBufferedPosition();
    long currentPos = player.getCurrentPosition();

    if(bufferedPos < currentPos) {
      buffering = true;
    } else if (bufferedPos > 0) {
      ready = true;
    }

    paused = !player.isPlaying();
  }

  @Override
  public String getMimeType() {
    KalturaPlayer p = player.get();
    if(p != null && p.getMediaFormat() != null) {
      return p.getMediaFormat().mimeType;
    }

    return null;
  }

  @Override
  protected View getPlayerView() {
    KalturaPlayer p = player.get();
    if(p != null) {
      return p.getPlayerView();
    }

    return null;
  }

  @Override
  public Float getSourceAdvertisedFramerate() {
    return null;
  }

  @Override
  <E extends PKEvent> Listener addListener(Object groupId, Class<E> type, Listener<E> listener) {
    KalturaPlayer p = player.get();
    if(p != null) {
      p.addListener(groupId, type, listener);
    }

    attachListener(listener);

    return listener;
  }

  @Override
  public Listener addListener(Object groupId, Enum type, Listener listener) {
    KalturaPlayer p = player.get();
    if(p != null) {
      p.addListener(groupId, type, listener);
    }
    attachListener(listener);
    return listener;
  }

  @Override
  public void removeListener(Listener listener) {
    KalturaPlayer p = player.get();
    if(p != null) {
      p.removeListener(listener);
    }
  }

  @Override
  public boolean isPlaying() {
    KalturaPlayer p = player.get();
    if(p != null) {
      return p.isPlaying();
    }
    return false;
  }

  @Override
  public void release() {
    player.clear();
  }

  @Override
  public long getInnerCurrentPosition() {
    KalturaPlayer p = player.get();
    if(p != null) {
      return p.getCurrentPosition();
    }
    return 0;
  }

  @Override
  public boolean getAutoPlay() {
    KalturaPlayer p = player.get();
    if(p != null) {
      return p.isAutoPlay();
    }
    return false;
  }
}
