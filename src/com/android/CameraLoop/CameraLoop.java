package com.android.CameraLoop;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;

class Global {
	static double pi = 0.0;
	static int camera_interval = 1000;
	static int pi_iterations = 1000;
}

public class CameraLoop extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		Preview mPreview = new Preview(this);
		DrawOnTop mDraw = new DrawOnTop(this);

		setContentView(mPreview);
		addContentView(mDraw, new LayoutParams
				(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	}

}

class TimerSnap extends TimerTask implements Camera.PictureCallback {
	Camera myCamera;
	Context myContext;
	static int counter = 0;
	public TimerSnap(Context con, Camera c) {
		super();
		myCamera = c;
		myContext = con;
		counter += 1;
	}

	@Override
	public void run() {
		myCamera.takePicture(null, null, null, this);
	}

	public void onPictureTaken(byte[] data, Camera camera) {
		// Write the file to local storage
		String FILENAME = "image.jpg";
		FileOutputStream fos = null;
		try {
			fos = myContext.openFileOutput(FILENAME, Context.MODE_PRIVATE);
			fos.write(data);
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Upload to the local server
		// TODO: actually do this!
		
		// Kick off the appropriately long pi calculation
		MonteCarloPi mpi = new MonteCarloPi();
		
		// Restart camera preview
		myCamera.startPreview();
		// Start timer
		Timer t = new Timer("snap", true);
		t.schedule(new TimerSnap(myContext, myCamera), 1000);
		
	}
}

class DrawOnTop extends View {

	String text;

	public DrawOnTop(Context context) {
		super(context);
		text = "Pi: " + Double.toString(Global.pi);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		
		text = "Pi: " + Double.toString(Global.pi);
		
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(Color.RED);
		paint.setAntiAlias(true);
		
		// Draw a rectangle
		Rect r = new Rect(40, 250, 440, 370);
		paint.setTextSize(28);
		canvas.drawText(text, 10, 40, paint);
		canvas.drawRect(r, paint);

		super.onDraw(canvas);
	}

}


class MonteCarloPi extends TimerTask {
	
	Random rand;
	
	public MonteCarloPi() {
		rand = new Random();
	}
	
	@Override
	public void run() {
		int inside, outside;
		inside = outside = 0;
		for(int i=0; i<Global.pi_iterations; i++) {
			float x = rand.nextFloat();
			float y = rand.nextFloat();
			if(Math.sqrt( Math.pow(x-0.5,2) + Math.pow(y-0.5, 2)) > 0.5) {
				outside += 1;
			} else {
				inside += 1;
			}
		}
		Global.pi = (inside / outside) * 4;
	}
}


class HTTPUpload {
	int serverResponseCode;
	String serverResponseMessage;
	public HTTPUpload(String filepath) {
		HttpURLConnection connection = null;
		DataOutputStream outputStream = null;
		DataInputStream inputStream = null;

		String pathToOurFile = filepath; //"/data/file_to_send.mp3";
		String urlServer = "http://192.168.1.1/handle_upload.php";
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary =  "*****";

		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1*1024*1024;

		try
		{
		FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile) );

		URL url = new URL(urlServer);
		connection = (HttpURLConnection) url.openConnection();

		// Allow Inputs & Outputs
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setUseCaches(false);

		// Enable POST method
		connection.setRequestMethod("POST");

		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

		outputStream = new DataOutputStream( connection.getOutputStream() );
		outputStream.writeBytes(twoHyphens + boundary + lineEnd);
		outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + pathToOurFile +"\"" + lineEnd);
		outputStream.writeBytes(lineEnd);

		bytesAvailable = fileInputStream.available();
		bufferSize = Math.min(bytesAvailable, maxBufferSize);
		buffer = new byte[bufferSize];

		// Read file
		bytesRead = fileInputStream.read(buffer, 0, bufferSize);

		while (bytesRead > 0)
		{
		outputStream.write(buffer, 0, bufferSize);
		bytesAvailable = fileInputStream.available();
		bufferSize = Math.min(bytesAvailable, maxBufferSize);
		bytesRead = fileInputStream.read(buffer, 0, bufferSize);
		}

		outputStream.writeBytes(lineEnd);
		outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

		// Responses from the server (code and message)
		serverResponseCode = connection.getResponseCode();
		serverResponseMessage = connection.getResponseMessage();

		fileInputStream.close();
		outputStream.flush();
		outputStream.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}	
	}
}

//----------------------------------------------------------------------

class Preview extends SurfaceView implements SurfaceHolder.Callback {
	SurfaceHolder myHolder;
	Camera myCamera;
	Context myContext;
	
	Preview(Context context) {
		super(context);
		myContext = context;
		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		myHolder = getHolder();
		myHolder.addCallback(this);
		myHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		myCamera = Camera.open();
		try {
			myCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		myCamera.stopPreview();
		myCamera = null;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int
			w, int h) {
		// Now that the size is known, set up the camera parameters and begin
		// the preview.
		Camera.Parameters parameters = myCamera.getParameters();
		parameters.setPreviewSize(w, h);
		myCamera.setParameters(parameters);
		myCamera.startPreview();
		
		// Start timer
		Timer t = new Timer("snap", true);
		t.schedule(new TimerSnap(myContext, myCamera), 1000);
	}
}

