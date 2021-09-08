package com.mux.stats.sdk.kaltura.example;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.mux.stats.sdk.kaltura.R;
import com.mux.stats.sdk.muxkalturasdk.MuxKaltura;

public class MainActivity extends Activity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MuxKaltura.hello();
    setContentView(R.layout.activity_main);
  }
}
