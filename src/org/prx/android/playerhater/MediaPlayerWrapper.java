package org.prx.android.playerhater;

import java.io.FileDescriptor;
import java.io.IOException;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.Uri;
import android.util.Log;

public class MediaPlayerWrapper implements OnBufferingUpdateListener,
		OnCompletionListener, OnErrorListener, OnInfoListener,
		OnPreparedListener, OnSeekCompleteListener {

	private MediaPlayer mMediaPlayer;

	public static final int IDLE = 0;
	public static final int END = 1;
	public static final int ERROR = -1;
	public static final int INITIALIZED = 2;
	public static final int PREPARING = 3;
	public static final int PREPARED = 4;
	public static final int STARTED = 5;
	public static final int STOPPED = 6;
	public static final int PAUSED = 7;
	public static final int PLAYBACK_COMPLETED = 8;

	private static final String TAG = "MediaPlayerWrapper";

	private OnErrorListener mErrorListener;
	private OnPreparedListener mPreparedListener;
	private OnCompletionListener mCompletionListener;
	private OnBufferingUpdateListener mBufferingUpdateListener;
	private OnInfoListener mInfoListener;
	private OnSeekCompleteListener mSeekCompleteListener;

	private int mState;
	private int mPrevState;

	public MediaPlayerWrapper() {
		this.mMediaPlayer = new MediaPlayer();
		this.mState = IDLE;
		this.mMediaPlayer.setOnBufferingUpdateListener(this);
		this.mMediaPlayer.setOnCompletionListener(this);
		this.mMediaPlayer.setOnErrorListener(this);
		this.mMediaPlayer.setOnInfoListener(this);
		this.mMediaPlayer.setOnPreparedListener(this);
		this.mMediaPlayer.setOnSeekCompleteListener(this);
	}

	public int getState() {
		return this.mState;
	}

	public void reset() {
		this.mState = IDLE;
		this.mMediaPlayer.reset();
	}

	public void release() {
		this.mMediaPlayer.release();
		this.mState = END;
	}

	public void prepare() throws IOException, IllegalStateException {
		if (this.mState == INITIALIZED || this.mState == STOPPED) {
			this.mMediaPlayer.prepare();
			this.mState = PREPARED;
		} else {
			throw (new IllegalStateException());
		}
	}

	public void prepareAsync() throws IllegalStateException {
		if (this.mState == INITIALIZED || this.mState == STOPPED) {
			this.mMediaPlayer.prepareAsync();
			this.mState = PREPARING;
		} else {
			throw (new IllegalStateException());
		}
	}

	public void start() throws IllegalStateException {
		if (this.mState == PREPARED || this.mState == STARTED
				|| this.mState == PAUSED || this.mState == PLAYBACK_COMPLETED) {
			this.mMediaPlayer.start();
			this.mState = STARTED;
		} else {
			throw (new IllegalStateException());
		}
	}

	public void pause() throws IllegalStateException {
		if (this.mState == STARTED || this.mState == PAUSED) {
			this.mMediaPlayer.pause();
			this.mState = PAUSED;
		} else {
			throw (new IllegalStateException());
		}
	}

	public void stop() throws IllegalStateException {
		if (this.mState == PREPARED || this.mState == STARTED
				|| this.mState == STOPPED || this.mState == PAUSED
				|| this.mState == PLAYBACK_COMPLETED) {
			this.mMediaPlayer.stop();
			this.mState = STOPPED;
		} else {
			throw (new IllegalStateException());
		}
	}

	public void seekTo(int msec) {
		if (this.mState == PREPARED || this.mState == STARTED
				|| this.mState == PAUSED || this.mState == PLAYBACK_COMPLETED) {
			this.mPrevState = this.mState;
			this.mState = PREPARING;
			this.mMediaPlayer.seekTo(msec);
		} else {
			throw (new IllegalStateException());
		}
	}

	public boolean isPlaying() {
		return this.mMediaPlayer.isPlaying();
	}

	public int getCurrentPosition() {
		if (this.mState == STARTED || this.mState == PAUSED
				|| this.mState == STOPPED || this.mState == PLAYBACK_COMPLETED) {
			return this.mMediaPlayer.getCurrentPosition();
		}
		return 0;
	}

	public int getDuration() {
		if (this.mState == PREPARED || this.mState == STARTED
				|| this.mState == PAUSED || this.mState == PLAYBACK_COMPLETED) {
			return this.mMediaPlayer.getDuration();
		}
		return 0;
	}

	public void setAudioStreamType(int streamtype) {
		this.mMediaPlayer.setAudioStreamType(streamtype);
	}

	public void setDataSource(FileDescriptor fd) throws IllegalStateException,
			IOException, IllegalArgumentException, SecurityException {
		try {
			this.mMediaPlayer.setDataSource(fd);
			this.mState = INITIALIZED;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setDataSource(String path) throws IllegalStateException,
			IOException, IllegalArgumentException, SecurityException {
		this.mMediaPlayer.setDataSource(path);
		this.mState = INITIALIZED;
	}

	public void setDataSource(Context context, Uri uri)
			throws IllegalStateException, IOException,
			IllegalArgumentException, SecurityException {
		this.mMediaPlayer.setDataSource(context, uri);
		this.mState = INITIALIZED;
	}

	public void setDataSource(FileDescriptor fd, long offset, long length)
			throws IllegalStateException, IOException, IllegalArgumentException {
		this.mMediaPlayer.setDataSource(fd, offset, length);
	}

	public void setOnErrorListener(OnErrorListener errorListener) {
		this.mErrorListener = errorListener;
	}

	public void setOnPreparedListener(OnPreparedListener preparedListener) {
		this.mPreparedListener = preparedListener;
	}

	public void setOnBufferingUpdateListener(
			OnBufferingUpdateListener bufferingUpdateListener) {
		this.mBufferingUpdateListener = bufferingUpdateListener;
	}

	public void setOnCompletionListener(OnCompletionListener completionListener) {
		this.mCompletionListener = completionListener;
	}

	public void setOnInfoListener(OnInfoListener infoListener) {
		this.mInfoListener = infoListener;
	}

	public void setOnSeekCompleteListener(
			OnSeekCompleteListener seekCompleteListener) {
		this.mSeekCompleteListener = seekCompleteListener;
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		Log.d(TAG, "Buffering Update"); 
		if (this.mBufferingUpdateListener != null) {
			this.mBufferingUpdateListener.onBufferingUpdate(mp, percent);
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		Log.d(TAG, "Completion"); 
		this.mState = PLAYBACK_COMPLETED;
		if (this.mCompletionListener != null) {
			this.mCompletionListener.onCompletion(mp);
		}
		stop(); 
		reset(); 
		 
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.d(TAG, "Error"); 
		this.mState = ERROR;
		Boolean response = false;
		if (this.mErrorListener != null) {
			response = this.mErrorListener.onError(mp, what, extra);
		}
		return response;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		Log.d(TAG, "info"); 
		if (this.mInfoListener != null) {
			return this.mInfoListener.onInfo(mp, what, extra);
		}
		return false;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		this.mState = PREPARED;
		if (this.mPreparedListener != null) {
			this.mPreparedListener.onPrepared(mp);
		}
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		this.mState = this.mPrevState;
		if (this.mSeekCompleteListener != null) {
			this.mSeekCompleteListener.onSeekComplete(mp);
		}
	}

	public void setVolume(float leftVolume, float rightVolume) {
		mMediaPlayer.setVolume(leftVolume, rightVolume);
	}
}
