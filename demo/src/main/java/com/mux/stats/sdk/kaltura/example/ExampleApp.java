package com.mux.stats.sdk.kaltura.example;

import android.app.Application;
import com.kaltura.tvplayer.KalturaOvpPlayer;

public class ExampleApp extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    KalturaOvpPlayer.initialize(this, Constants.OVP_PARTNER_ID, Constants.OVP_SERVER_URL);
  }
}
