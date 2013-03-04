package org.prx.android.playerhater.plugins;

import org.prx.android.playerhater.service.PlayerHaterService;
import org.prx.android.playerhater.util.BroadcastReceiver;
import org.prx.android.playerhater.util.OnAudioFocusChangeListener;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AudioFocusPlugin extends PlayerHaterPlugin {
	private final AudioManager mAudioService;
	private final OnAudioFocusChangeListener mAudioFocusChangeListener;
	private ComponentName mEventReceiver;
	private Context mContext;

	public AudioFocusPlugin(PlayerHaterService context) {
		mContext = context.getBaseContext();
		mAudioFocusChangeListener = new OnAudioFocusChangeListener(context);
		mAudioService = (AudioManager) mContext
				.getSystemService(Context.AUDIO_SERVICE);
	}

	@Override
	public void onPlay() {
		mAudioService.requestAudioFocus(mAudioFocusChangeListener,
				AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		mAudioService.registerMediaButtonEventReceiver(getEventReceiver());
	}

	@Override
	public void onStop() {
		mAudioService.abandonAudioFocus(mAudioFocusChangeListener);
		mAudioService.unregisterMediaButtonEventReceiver(getEventReceiver());
	}

	@Override
	public void onPause() {
		mAudioService.abandonAudioFocus(mAudioFocusChangeListener);
	}

	@Override
	public void onResume() {
		mAudioService.requestAudioFocus(mAudioFocusChangeListener,
				AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
	}

	private ComponentName getEventReceiver() {
		if (mEventReceiver == null) {
			mEventReceiver = new ComponentName(mContext,
					BroadcastReceiver.class);
		}
		return mEventReceiver;
	}
}