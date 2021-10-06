package com.mux.stats.sdk.kaltura.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.kaltura.netkit.utils.ErrorElement;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKEvent.Listener;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.PlayerState;
import com.kaltura.playkit.providers.ovp.OVPMediaAsset;
import com.kaltura.tvplayer.KalturaOvpPlayer;
import com.kaltura.tvplayer.KalturaPlayer;
import com.kaltura.tvplayer.KalturaPlayer.OnEntryLoadListener;
import com.kaltura.tvplayer.MediaOptions;
import com.kaltura.tvplayer.OVPMediaOptions;
import com.kaltura.tvplayer.PlayerInitOptions;
import com.mux.stats.sdk.kaltura.R;
import com.mux.stats.sdk.muxkalturasdk.MuxStatsKaltura;

public class MainActivity extends Activity {
  private String entryId = "1_w9zx2eti";
  private KalturaPlayer player;
  private PlayerState playerState;

  private ImageButton playButton;

  private PKEvent.Listener eventListener = new Listener() {
    @Override
    public void onEvent(PKEvent event) {
      updateUI();
    }
  };

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    initPlaykitPlayer();

    playButton = findViewById(R.id.activity_main_play_button);
    playButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            if(player != null) {
              if(player.isPlaying()) {
                player.pause();
              } else {
                player.play();
              }
            }
          }
        });

    updateUI();
  }

  private void updateUI() {
    if(player != null) {
      if(player.isPlaying()) {
        playButton.setImageResource(R.drawable.exo_controls_pause);
      } else {
        playButton.setImageResource(R.drawable.exo_controls_play);
      }
    }
  }

  private void initPlaykitPlayer() {
    PlayerInitOptions playerInitOptions = new PlayerInitOptions(Constants.OVP_PARTNER_ID);
    playerInitOptions.setAutoPlay(true);

    KalturaPlayer player = KalturaOvpPlayer.create(this, playerInitOptions);
    player.setPlayerView(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

    ((ViewGroup)findViewById(R.id.activity_main_content_view)).addView(player.getPlayerView());

    OVPMediaAsset ovpMediaAsset = new OVPMediaAsset();
    ovpMediaAsset.setEntryId(entryId);
    ovpMediaAsset.setKs(null);
    OVPMediaOptions ovpMediaOptions = new OVPMediaOptions(ovpMediaAsset);

    player.loadMedia(ovpMediaOptions, new OnEntryLoadListener() {
      @Override
      public void onEntryLoadComplete(MediaOptions mediaOptions, PKMediaEntry entry, ErrorElement error) {
        if (error != null) {
          Toast.makeText(MainActivity.this, error.getMessage(), 10).show();
        }
      }
    });

    this.player = player;

    player.addListener(this, PlayerEvent.stateChanged, event -> {
      playerState = event.newState;
      updateUI();
    });

    player.addListener(this, PlayerEvent.canPlay, eventListener);
    player.addListener(this, PlayerEvent.ended, eventListener);
    player.addListener(this, PlayerEvent.loadedMetadata, eventListener);
    player.addListener(this, PlayerEvent.pause, eventListener);
    player.addListener(this, PlayerEvent.play, eventListener);
    player.addListener(this, PlayerEvent.playing, eventListener);
    player.addListener(this, PlayerEvent.seeked, eventListener);
    player.addListener(this, PlayerEvent.replay, eventListener);
    player.addListener(this, PlayerEvent.stopped, eventListener);
  }
}
