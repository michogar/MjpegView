/**
 * 
 */
package com.michogarcia.mjpegview;

import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "MjpegView";

	public final static int POSITION_UPPER_LEFT = 9;
	public final static int POSITION_UPPER_RIGHT = 3;
	public final static int POSITION_LOWER_LEFT = 12;
	public final static int POSITION_LOWER_RIGHT = 6;

	public final static int SIZE_STANDARD = 1;
	public final static int SIZE_BEST_FIT = 4;
	public final static int SIZE_FULLSCREEN = 8;

	private Context context;
	private MjpegViewThread thread;
	private MjpegInputStream mIn = null;
	private boolean showFps = false;
	private boolean mRun = false;
	private boolean surfaceDone = false;
	private boolean viewing = false;
	private Paint overlayPaint;
	private int overlayTextColor;
	private int overlayBackgroundColor;
	private int ovlPos;
	private int dispWidth;
	private int dispHeight;
	private int displayMode;
	private ImjpegViewListener listener;
	private String url;

	public class MjpegViewThread extends AsyncTask<Void, Void, Void> {
		private SurfaceHolder mSurfaceHolder;
		private int frameCounter = 0;
		private long start;
		private Bitmap ovl, bitmap;

		public MjpegViewThread(SurfaceHolder surfaceHolder, Context context) {
			mSurfaceHolder = surfaceHolder;
		}

		private Rect destRect(int bmw, int bmh) {
			int tempx;
			int tempy;
			if (displayMode == MjpegView.SIZE_STANDARD) {
				tempx = (dispWidth / 2) - (bmw / 2);
				tempy = (dispHeight / 2) - (bmh / 2);
				return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
			}
			if (displayMode == MjpegView.SIZE_BEST_FIT) {
				float bmasp = (float) bmw / (float) bmh;
				bmw = dispWidth;
				bmh = (int) (dispWidth / bmasp);
				if (bmh > dispHeight) {
					bmh = dispHeight;
					bmw = (int) (dispHeight * bmasp);
				}
				tempx = (dispWidth / 2) - (bmw / 2);
				tempy = (dispHeight / 2) - (bmh / 2);
				return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
			}
			if (displayMode == MjpegView.SIZE_FULLSCREEN)
				return new Rect(0, 0, dispWidth, dispHeight);
			return null;
		}

		public void setSurfaceSize(int width, int height) {
			synchronized (mSurfaceHolder) {
				dispWidth = width;
				dispHeight = height;
			}
		}

		private Bitmap makeFpsOverlay(Paint p, String text) {
			Rect b = new Rect();
			p.getTextBounds(text, 0, text.length(), b);
			int bwidth = b.width() + 2;
			int bheight = b.height() + 2;
			Bitmap bm = Bitmap.createBitmap(bwidth, bheight,
					Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(bm);
			p.setColor(overlayBackgroundColor);
			c.drawRect(0, 0, bwidth, bheight, p);
			p.setColor(overlayTextColor);
			c.drawText(text, -b.left + 1,
					(bheight / 2) - ((p.ascent() + p.descent()) / 2) + 1, p);
			return bm;
		}

		@Override
		protected Void doInBackground(Void... params) {

			PorterDuffXfermode mode = new PorterDuffXfermode(
					PorterDuff.Mode.DST_OVER);
			bitmap = null;
			int width;
			int height;
			Rect destRect;
			Canvas c = null;
			Paint p = new Paint();
			String fps = "";
			boolean sucess = false;

			mIn = MjpegInputStream.read(url);

			if (mIn == null) {
				onError();
			}

			while (mRun) {
				if (surfaceDone) {
					try {
						try {
							bitmap = mIn.readMjpegFrame();
							sucess = true;
						} catch (IOException e) {
							sucess = false;
							break;
						}
						c = mSurfaceHolder.lockCanvas();
						Log.d(TAG, "Lock");
						synchronized (mSurfaceHolder) {
							destRect = destRect(bitmap.getWidth(),
									bitmap.getHeight());
							c.drawColor(overlayBackgroundColor);
							c.drawBitmap(bitmap, null, destRect, p);
							if (showFps) {
								p.setXfermode(mode);
								if (ovl != null) {
									height = ((ovlPos & 1) == 1) ? destRect.top
											: destRect.bottom - ovl.getHeight();
									width = ((ovlPos & 8) == 8) ? destRect.left
											: destRect.right - ovl.getWidth();
									c.drawBitmap(ovl, width, height, null);
								}
								p.setXfermode(null);
								frameCounter++;
								if ((System.currentTimeMillis() - start) >= 1000) {
									fps = String.valueOf(frameCounter) + "fps";
									frameCounter = 0;
									start = System.currentTimeMillis();
									ovl = makeFpsOverlay(overlayPaint, fps);
								}
							}
						}
					} finally {
						if (c != null) {
							mSurfaceHolder.unlockCanvasAndPost(c);
							Log.d(TAG, "UnLock");
						}
					}
					if (sucess) {
						if(listener!=null)
							listener.sucess();
					} else {
						onError();
					}
				}
			}
			return null;
		}

		@Override
		protected void onCancelled() {
			Log.d(TAG, "Cancelled Thread");
		}

		@Override
		protected void onPreExecute() {
			Log.d(TAG, "Pre executed Thread");
			start = System.currentTimeMillis();
		}

		private void onError() {
			stopPlayback();
			if(listener!=null)
				listener.error();
			mRun = false;
		}

		public Bitmap getBitmap() {
			return bitmap;
		}
	}

	private void init(Context context) {
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		thread = new MjpegViewThread(holder, context);
		setFocusable(true);
	}

	public MjpegView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }


	public void startPlayback() {
		mRun = true;
		init(context);
		thread.execute();
	}

	public void stopPlayback() {
		if (mIn != null) {
			thread.cancel(true);
			mRun = false;
			try {
				mIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public MjpegView(Context context, AttributeSet attrs,
			ImjpegViewListener listener) {
		super(context, attrs);
		this.context = context;
		this.listener = listener;
	}

	public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
		thread.setSurfaceSize(w, h);
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceDone = false;
		stopPlayback();
	}

	public MjpegView(Context context, ImjpegViewListener listener) {
		super(context);
		this.context = context;
		this.listener = listener;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		surfaceDone = true;
	}

	public void showFps(boolean b) {
		showFps = b;
	}

	public void setSource(String URL) {
		if (URL == null) {
			if(listener!=null)
				listener.error();
		} else {
			// mIn = source;
			url = URL;
			startPlayback();
		}
	}

	public void setOverlayPaint(Paint p) {
		overlayPaint = p;
	}

	public void setOverlayTextColor(int c) {
		overlayTextColor = c;
	}

	public void setOverlayBackgroundColor(int c) {
		overlayBackgroundColor = c;
	}

	public void setOverlayPosition(int p) {
		ovlPos = p;
	}

	public void setDisplayMode(int s) {
		displayMode = s;
	}

	public void setDispWidth(int dispWidth) {
		this.dispWidth = dispWidth;
	}

	public void setDispHeight(int dispHeight) {
		this.dispHeight = dispHeight;
	}

	public boolean isViewing() {
		return viewing;
	}

	public ImjpegViewListener getListener() {
		return listener;
	}

	public void destroy() {
		stopPlayback();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean executed = false;
		int action = event.getAction();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			Log.d(TAG, "Touched!!!");
			Bitmap bm = thread.getBitmap();
			if(listener!=null)
				listener.hasBitmap(bm);
			executed = true;
			break;

		default:
			break;
		}
		return executed;
	}

}
