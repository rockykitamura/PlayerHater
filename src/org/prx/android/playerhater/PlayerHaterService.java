package org.prx.android.playerhater;

import java.io.FileDescriptor;
import java.io.IOException;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.RemoteViews;

public class PlayerHaterService extends Service implements OnErrorListener,
		OnPreparedListener {

	protected static final String TAG = "PlayerHater/Service";
	protected static final int PROGRESS_UPDATE = 9747244;
	protected static final int NOTIFICATION_NU = 9747245;

	protected NotificationManager mNotificationManager;
	protected Class<?> mNotificationIntentClass;
	protected RemoteViews mNotificationView;
	protected int mNotificationIcon;

	protected String nowPlayingString;
	protected String nowPlayingUrl;
	protected FileDescriptor nowPlayingFile;
	protected int nowPlayingType;
	protected static final int URL = 55;
	protected static final int FILE = 66;

	private MediaPlayerWrapper mediaPlayer;
	private UpdateProgressRunnable updateProgressRunner;
	private Thread updateProgressThread;
	private BroadcastReceiver mBroadcastReceiver;
	private PlayerListenerManager playerListenerManager;
	private OnErrorListener mOnErrorListener;
	private OnPreparedListener mOnPreparedListener;
	private AudioManager mAudioManager;
	private PlayerHaterListener mPlayerHaterListener;
	private OnAudioFocusChangeListener mAudioFocusChangeListener;

	private Bundle mBundle;
	
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message m) {

			switch (m.what) {
			case PROGRESS_UPDATE:
				if (mPlayerHaterListener != null)
					mPlayerHaterListener.onPlaying(m.arg1);
				break;
			default:
				onHandlerMessage(m);
			}
		}
	};
	

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

		if (playerListenerManager == null) {
			playerListenerManager = createPlayerListenerManager(this);
		}

		if (mediaPlayer == null) {
			mediaPlayer = createMediaPlayer();
			playerListenerManager.setMediaPlayer(mediaPlayer);
		}

		if (updateProgressRunner == null) {
			updateProgressRunner = createUpdateProgressRunner(mediaPlayer, mHandler);
		}
			
	
		if (mAudioManager == null) {
			mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		}
		
		if (mAudioFocusChangeListener == null) {
			mAudioFocusChangeListener = new OnAudioFocusChangeListener(this);
		}

		if (mBundle == null) {
			mBundle = new Bundle(10);
		}
		
		if (mBroadcastReceiver == null) {
			mBroadcastReceiver = new BroadcastReceiver(this);
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_HEADSET_PLUG);
			getBaseContext().registerReceiver(mBroadcastReceiver, filter);
		}
		
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/*
	 * We override a couple of methods in PlayerHaterBinder so that we can see
	 * MediaPlayer onError and onPrepared events
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		return new PlayerHaterBinder(this, playerListenerManager) {
			@Override
			public void setOnErrorListener(OnErrorListener listener) {
				mOnErrorListener = listener;
			}

			@Override
			public void setOnPreparedListener(OnPreparedListener listener) {
				mOnPreparedListener = listener;
			}
		};
	}

	/*
	 * Oh, snap � we're blowing up!
	 */
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, "Got MediaPlayer error: " + what + " / " + extra);
		if (updateProgressThread != null && updateProgressThread.isAlive()) {
			mHandler.removeCallbacks(updateProgressRunner);
			updateProgressThread.interrupt();
			updateProgressThread = null;
		}
		if (mOnErrorListener != null) {
			Log.e(TAG, "Passing error along.");
			return mOnErrorListener.onError(mp, what, extra);
		}
		return false;
	}

	/*
	 * We register ourselves to listen to the onPrepared event so we can start
	 * playing immediately.
	 */
	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d(TAG, "MediaPlayer is prepared, beginning playback of " + getNowPlaying());
		mediaPlayer.start();
		sendIsPlaying();
		
		mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		
		if (mOnPreparedListener != null) {
			Log.d(TAG, "Passing prepared along.");
			mOnPreparedListener.onPrepared(mp);
		}
	}

	public boolean pause() throws IllegalStateException {
		mediaPlayer.pause();
		return true;
	}

	public void setNotificationIntentActivity(Activity activity) {
		mNotificationIntentClass = activity.getClass();
	}

	public void setNotificationView(int view) {
		mNotificationView = new RemoteViews(getPackageName(), view);
	}

	public int getState() {
		return mediaPlayer.getState();
	}

	public String getNowPlaying() {
		if (nowPlayingString == null) {
			return "<#null>";
		}
		return nowPlayingString;
	}

	public boolean isPlaying() {
		return (mediaPlayer.getState() == MediaPlayerWrapper.STARTED);
	}

	public boolean play(String stream) throws IllegalStateException,
			IllegalArgumentException, SecurityException, IOException {
		nowPlayingType = URL;
		nowPlayingString = stream;
		nowPlayingUrl = stream;
		if (mediaPlayer.getState() != MediaPlayerWrapper.IDLE)
			reset();
		mediaPlayer.setDataSource(nowPlayingUrl);
		return play();

	}

	public boolean play(FileDescriptor fd) throws IllegalStateException,
			IllegalArgumentException, SecurityException, IOException {
		nowPlayingType = FILE;
		nowPlayingString = fd.toString();
		nowPlayingFile = fd;
		if (mediaPlayer.getState() != MediaPlayerWrapper.IDLE)
			reset();
		mediaPlayer.setDataSource(nowPlayingFile);
		return play();
	}
	
	public boolean play() throws IllegalStateException, IOException {
		
		switch(mediaPlayer.getState()) {
		case MediaPlayerWrapper.INITIALIZED:
		case MediaPlayerWrapper.STOPPED:
			performPrepare();
			break;
		case MediaPlayerWrapper.PREPARED:
		case MediaPlayerWrapper.PAUSED:
			mediaPlayer.start();
			sendIsPlaying();
			break;
		default:
			throw new IllegalStateException();
		}
		return true;
		
	}
	
	public void transientPlay(FileDescriptor file, boolean isDuckable) {
		TransientPlayer.play(this, file, isDuckable);
	}

	public void transientPlay(String url, boolean isDuckable) {
		TransientPlayer.play(this, url, isDuckable);
	}
	
	private void reset() {
		Log.d(TAG, "Resetting media player.");
		mediaPlayer.reset();
	}

	public int getDuration() {
		return this.mediaPlayer.getDuration();
	}

	public int getCurrentPosition() {
		return mediaPlayer.getCurrentPosition();
	}

	public void seekTo(int pos) {
		mediaPlayer.seekTo(pos);
	}


	private void performPrepare() {
		Log.d(TAG, "Starting preparation of: " + getNowPlaying());
		sendIsLoading();
		mediaPlayer.prepareAsync();
		
		if (updateProgressThread != null && updateProgressThread.isAlive()) {
			mHandler.removeCallbacks(updateProgressRunner);
			updateProgressThread.interrupt();
			updateProgressThread = null;
		}

		updateProgressThread = new Thread(updateProgressRunner);
		updateProgressThread.start();
	}

	public boolean stop() {
		mediaPlayer.stop();
		sendIsStopped();
		return true;
	}

	/*
	 * These methods concern the creation of notifications. They should be
	 * ignored.
	 */

	protected Notification buildNotification() {
		return buildNotification("Playing...", 0);
	}

	protected Notification buildNotification(int pendingFlag) {
		return buildNotification("Playing...", pendingFlag);
	}

	protected Notification buildNotification(String text) {
		return buildNotification(text, 0);
	}

	protected Notification buildNotification(String text, int pendingFlag) {
		Notification notification = new Notification(mNotificationIcon, text,
				System.currentTimeMillis());

		if (mNotificationIntentClass != null && mNotificationView != null) {
			notification.contentView = mNotificationView;
			notification.contentIntent = PendingIntent.getActivity(this, 0,
					new Intent(this, mNotificationIntentClass), pendingFlag);
			notification.flags |= Notification.FLAG_ONGOING_EVENT;
		}

		return notification;
	}

	/*
	 * creates a media player (wrapped, of course) and registers the listeners
	 * for all of the events.
	 */
	private static MediaPlayerWrapper createMediaPlayer() {
		return new MediaPlayerWrapper();		
	}

	/*
	 * creates a new update progress runner, which fires events back to this
	 * class' handler with the message we request and the duration which has
	 * passed
	 */
	private static UpdateProgressRunnable createUpdateProgressRunner(MediaPlayerWrapper mediaPlayer, Handler handler) {
		return  new UpdateProgressRunnable(mediaPlayer,
				handler, PROGRESS_UPDATE);
	}

	/*
	 * This class basically just makes sure that we never need to re-bind
	 * ourselves.
	 */
	private static PlayerListenerManager createPlayerListenerManager(PlayerHaterService svc) {
		PlayerListenerManager mgr = new PlayerListenerManager();
		mgr.setOnErrorListener(svc);
		mgr.setOnPreparedListener(svc);
		return mgr;
	}

	/*
	 * This should be overridden by subclasses which wish to handle messages
	 * sent to mHandler without re-implementing the handler. It is a noop by
	 * default.
	 */
	protected void onHandlerMessage(Message m) { /* noop */
	}

	public Bundle getBundle() {
		return mBundle;
	}

	public void commitBundle(Bundle icicle) {
		mBundle = icicle;
	}
	
	private void sendIsPlaying() {
		sendIsPlaying(0);
	}
	
	private void sendIsPlaying(int progress) {
		if (mPlayerHaterListener != null) {
			mPlayerHaterListener.onPlaying(progress);
		}
	}

	private void sendIsLoading() {
		if (mPlayerHaterListener != null) {
			mPlayerHaterListener.onLoading();
		}
	}
	
	private void sendIsPaused() {
		if (mPlayerHaterListener != null) {
			mPlayerHaterListener.onPaused();
		}
	}
	
	private void sendIsStopped() {
		if (mPlayerHaterListener != null) {
			mPlayerHaterListener.onStopped();
		}
	}
}
