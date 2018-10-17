package com.brightcove.player.samples.ima.exoplayer.vast;

import android.media.RemoteControlClient;
import  android.os.Bundle;
import android.util.Log;
import java.util.Map;
import java.util.HashMap;

import com.brightcove.player.model.CuePoint;
import com.brightcove.ima.GoogleIMAComponent;
import com.brightcove.ima.GoogleIMAEventType;
import com.brightcove.player.edge.Catalog;
import com.brightcove.player.edge.VideoListener;
import com.brightcove.player.event.Event;
import com.brightcove.player.event.EventEmitter;
import com.brightcove.player.event.EventListener;
import com.brightcove.player.event.EventType;
import com.brightcove.player.mediacontroller.BrightcoveMediaController;
import com.brightcove.player.mediacontroller.BrightcoveSeekBar;
import com.brightcove.player.model.Video;
import com.brightcove.player.view.BaseVideoView;
import com.brightcove.player.view.BrightcoveExoPlayerVideoView;
import com.brightcove.player.view.BrightcovePlayer;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This app illustrates how to use the Google IMA plugin with Vast
 * with the Brightcove Player for Android.
 *
 * Note: Ad cue points are not used with IMA Vast. The AdCuePoints referenced
 * in the setupAdMarkers method below are Video objects.
 *
 * @author Paul Matthew Reilly (original code)
 * @author Paul Michael Reilly (added explanatory comments)
 */
public class MainActivity extends BrightcovePlayer {

    private final String TAG = this.getClass().getSimpleName();

    private EventEmitter eventEmitter;
    private GoogleIMAComponent googleIMAComponent;
    private String vastAdTagURL = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=";
    private List<CuePoint> cuePoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // When extending the BrightcovePlayer, we must assign the BrightcoveExoPlayerVideoView before
        // entering the superclass. This allows for some stock video player lifecycle
        // management.
        setContentView(R.layout.ima_activity_main);
        brightcoveVideoView = (BrightcoveExoPlayerVideoView) findViewById(R.id.brightcove_video_view);

        super.onCreate(savedInstanceState);
        eventEmitter = brightcoveVideoView.getEventEmitter();

        eventEmitter.on(EventType.SET_CUE_POINT, new EventListener() {
            @Override
            public void processEvent(Event event) {
                Log.v(TAG, "Set Cuepoint: "+event.toString());
            }
        });

        eventEmitter.on(EventType.CUE_POINT, new EventListener() {
            @Override
            public void processEvent(Event event) {
                int playheadPosition = event.getIntegerProperty(Event.PLAYHEAD_POSITION);
                Log.v(TAG, "Got a CuePoint at " + playheadPosition);
            }
        });

        // Use a procedural abstraction to setup the Google IMA SDK via the plugin.
        setupGoogleIMA();

        Catalog catalog = new Catalog(eventEmitter, getString(R.string.account), getString(R.string.policy));
        catalog.findVideoByID(getString(R.string.videoId), new VideoListener() {
            public void onVideo(Video video) {
                setupAdMarkers(brightcoveVideoView,video);
                brightcoveVideoView.add(video);

                // Auto play: the GoogleIMAComponent will postpone
                // playback until the Ad Rules are loaded.
                brightcoveVideoView.start();
            }

            public void onError(String error) {
                Log.e(TAG, error);
            }
        });
    }

    /**
     * Setup the Brightcove IMA Plugin.
     */
    private void setupGoogleIMA() {
        // Establish the Google IMA SDK factory instance.
        final ImaSdkFactory sdkFactory = ImaSdkFactory.getInstance();


        // Enable logging up ad start.
        eventEmitter.on(EventType.AD_STARTED, new EventListener() {
            @Override
            public void processEvent(Event event) {
                Log.v(TAG, event.getType());
            }
        });

        // Enable logging any failed attempts to play an ad.
        eventEmitter.on(GoogleIMAEventType.DID_FAIL_TO_PLAY_AD, new EventListener() {
            @Override
            public void processEvent(Event event) {
                Log.v(TAG, event.getType());
            }
        });

        // Enable Logging upon ad completion.
        eventEmitter.on(EventType.AD_COMPLETED, new EventListener() {
            @Override
            public void processEvent(Event event) {
                Log.v(TAG, event.getType());
            }
        });


        // Set up a listener for initializing AdsRequests. The Google
        // IMA plugin emits an ad request event as a result of
        // initializeAdsRequests() being called.
        eventEmitter.on(GoogleIMAEventType.ADS_REQUEST_FOR_VIDEO, new EventListener() {
            @Override
            public void processEvent(Event event) {
                // Create a container object for the ads to be presented.
                AdDisplayContainer container = sdkFactory.createAdDisplayContainer();
                container.setPlayer(googleIMAComponent.getVideoAdPlayer());
                container.setAdContainer(brightcoveVideoView);

                // Build an ads request object and point it to the ad
                // display container created above.
                AdsRequest adsRequest = sdkFactory.createAdsRequest();
                adsRequest.setAdTagUrl(vastAdTagURL);
                adsRequest.setAdDisplayContainer(container);

                ArrayList<AdsRequest> adsRequests = new ArrayList<AdsRequest>(cuePoints.size());
                adsRequests.add(adsRequest);

                // Respond to the event with the new ad requests.
                event.properties.put(GoogleIMAComponent.ADS_REQUESTS, adsRequests);
                eventEmitter.respond(event);
            }
        });

        // Create the Brightcove IMA Plugin and pass in the event
        // emitter so that the plugin can integrate with the SDK.
        googleIMAComponent = new GoogleIMAComponent(brightcoveVideoView, eventEmitter, false);
    }


    /*
      This methods show how to the the Google IMA AdsManager, get the cue points and add the markers
      to the Brightcove Seek Bar.
     */
    private void setupAdMarkers(BaseVideoView videoView,Video video) {
        final BrightcoveMediaController mediaController = new BrightcoveMediaController(brightcoveVideoView);
        Map<String,Object> details = new HashMap<>();
        Map<String,Object> properties = new HashMap<>();

        // Add "Ad Markers" where the video says ads will appear.
        cuePoints = video.getCuePoints();
        CuePoint cuepoint;
        for (int i = 0; i < cuePoints.size(); i++ ) {
            int position = cuePoints.get(i).getPosition();

            Log.v(TAG, " Video Cuepoint position : " + position + " Video CuePoint type: " + cuePoints.get(i).getType());

            if (position == 0) {
                cuepoint = new CuePoint(CuePoint.PositionType.BEFORE, "AD", properties);
            } else if (position == video.getDuration()) {
                cuepoint = new CuePoint(CuePoint.PositionType.AFTER, "AD", properties);
            } else {
                cuepoint = new CuePoint(position, "AD", properties);
            }

            BrightcoveSeekBar brightcoveSeekBar = mediaController.getBrightcoveSeekBar();
            // If cuepoint is negative it means it is a POST ROLL.
            int markerTime = position < 0 ? brightcoveSeekBar.getMax() : (position);
            mediaController.getBrightcoveSeekBar().addMarker(markerTime);
            details.put(EventType.CUE_POINT, cuepoint);
            eventEmitter.emit(EventType.SET_CUE_POINT, details);
        }

        videoView.setMediaController(mediaController);
       }

}
