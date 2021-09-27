package com.mux.stats.sdk.kaltura.example.mockup.http;

public interface ConnectionListener {

  void segmentServed(String requestUuid, SegmentStatistics segmentStat);

}
