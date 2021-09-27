package com.mux.stats.sdk.kaltura.example;

import android.app.Application;
import com.kaltura.tvplayer.KalturaOvpPlayer;
import com.mux.stats.sdk.kaltura.example.mockup.http.SimpleHTTPServer;
import java.io.IOException;

public class ExampleApp extends Application {
  protected SimpleHTTPServer httpServer;
  protected int runHttpServerOnPort = 5000;
  protected int bandwidthLimitInBitsPerSecond = 1500000;
  public static final String URL_TO_PLAY = "http://localhost:5000/hls/google_glass/playlist.m3u8";

  @Override
  public void onCreate() {
    super.onCreate();

//    try {
//      httpServer = new SimpleHTTPServer(this, runHttpServerOnPort, bandwidthLimitInBitsPerSecond);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }

    KalturaOvpPlayer.initialize(this, Constants.OVP_PARTNER_ID, Constants.OVP_SERVER_URL);
  }
}
