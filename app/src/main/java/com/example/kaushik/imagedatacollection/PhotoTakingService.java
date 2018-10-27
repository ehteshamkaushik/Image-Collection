package com.example.kaushik.imagedatacollection;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

public class PhotoTakingService extends Service{
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    String imageName;
    static Camera camera = null;
    private FaceDetector detector;
    int imageNo = 0;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            final Handler handler = new Handler();
            TimerTask timertask = new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            takePhoto(PhotoTakingService.this);
                            imageNo++;
                            //Toast.makeText(PhotoTakingService.this, "Pic " + imageNo, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            };
            Timer timer = new Timer();
            timer.schedule(timertask, 0, 2000);
            stopSelf(msg.arg1);
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();
    }

    @Override
    public int onStartCommand (final Intent intent, int flags, final int startId) {
        //showMessage("Started Service for " + imageName);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //imageName = intent.getStringExtra("imageName");
                //showMessage("Image Name : " + imageName);
                HandlerThread thread = new HandlerThread("ServiceStartArguments",
                        Process.THREAD_PRIORITY_BACKGROUND);
                thread.start();
                mServiceLooper = thread.getLooper();
                mServiceHandler = new ServiceHandler(mServiceLooper);

                Message msg = mServiceHandler.obtainMessage();
                msg.arg1 = startId;
                mServiceHandler.sendMessage(msg);
            }
        }).start();
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void takePhoto(final Context context) {
        final SurfaceView preview = new SurfaceView(context);
        SurfaceHolder holder = preview.getHolder();
        final WindowManager wm = (WindowManager)context
                .getSystemService(Context.WINDOW_SERVICE);
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            //The preview must happen at or after this point or takePicture fails
            public void surfaceCreated(SurfaceHolder holder) {
                showMessage("Surface created");

                try {
                    camera = Camera.open(1);
                    showMessage("Opened camera");

                    try {
                        camera.setPreviewDisplay(holder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    camera.startPreview();
                    showMessage("Started preview for " + imageName);

                    camera.takePicture(null, null, new android.hardware.Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(final byte[] data, Camera camera) {
                            camera.release();
                            showMessage("Stopped Preview For " + imageName);
                            showMessage("Took picture");
                            if (data != null)
                            {
                                Bitmap bitmapTemp = BitmapFactory.decodeByteArray(data , 0, data .length);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(-90);
                                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmapTemp, 0, 0, bitmapTemp.getWidth(), bitmapTemp.getHeight(),
                                        matrix, true);
                                int width = rotatedBitmap.getWidth();
                                int height = rotatedBitmap.getHeight();
                                float scaleWidth = ((float) 600) / width;
                                float scaleHeight = ((float) 800) / height;
                                Matrix matrix1 = new Matrix();
                                matrix1.postScale(scaleWidth, scaleHeight);
                                Bitmap bitmap = Bitmap.createBitmap(rotatedBitmap, 0, 0, width, height,
                                        matrix1, false);
                                try {
                                    scanFaces(bitmap);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                if (bitmap != null)
                                {
                                    File file=new File(Environment.getExternalStorageDirectory()+"/dirr");
                                    if(!file.isDirectory()){
                                        file.mkdir();
                                    }
                                    file=new File(Environment.getExternalStorageDirectory()+"/dirr",imageNo+".jpg");
                                    try
                                    {
                                        FileOutputStream fileOutputStream=new FileOutputStream(file);
                                        bitmap.compress(Bitmap.CompressFormat.JPEG,100, fileOutputStream);
                                        fileOutputStream.flush();
                                        fileOutputStream.close();
                                        showMessage("Saved File " + file.getAbsolutePath());
                                        camera.release();
                                        onDestroy();
                                        wm.removeViewImmediate(preview);
                                    }
                                    catch(IOException e){
                                        e.printStackTrace();
                                    }
                                    catch(Exception exception)
                                    {
                                        exception.printStackTrace();
                                    }
                                }
                                else
                                {
                                    showMessage("Null Bitmap");
                                }
                            }
                            else
                            {
                                showMessage("Null Data");
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();

                    //throw new RuntimeException(e);
                }
//                if (camera != null) {
//                    camera.release();
//                    onDestroy();
//                    wm.removeViewImmediate(preview);
//                }
            }

            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
        });


        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1, //Must be at least 1x1
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                1,
                //Don't know if this is a safe default
                PixelFormat.UNKNOWN);

        //Don't set the preview visibility to GONE or INVISIBLE
        wm.addView(preview, params);
    }

    private static void showMessage(String message) {
        Log.i("Camera", message);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        showMessage("destroying");
        super.onDestroy();
    }

    private void scanFaces(Bitmap bitmap) throws Exception {
        if (detector.isOperational() && bitmap != null) {
            Bitmap editedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap
                    .getHeight(), bitmap.getConfig());
            float scale = getResources().getDisplayMetrics().density;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(255, 61, 61));
            paint.setTextSize((int) (14 * scale));
            paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            Canvas canvas = new Canvas(editedBitmap);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            Frame frame = new Frame.Builder().setBitmap(editedBitmap).build();
            SparseArray<Face> faces = detector.detect(frame);
            Face face = faces.valueAt(0);
//                canvas.clipRect(
//                        face.getPosition().x,
//                        face.getPosition().y,
//                        face.getPosition().x + face.getWidth(),
//                        face.getPosition().y + face.getHeight());
//                canvas.drawRect(
//                        face.getPosition().x,
//                        face.getPosition().y,
//                        face.getPosition().x + face.getWidth(),
//                        face.getPosition().y + face.getHeight(), paint);
//
//                for (Landmark landmark : face.getLandmarks()) {
//                    int cx = (int) (landmark.getPosition().x);
//                    int cy = (int) (landmark.getPosition().y);
//                    canvas.drawCircle(cx, cy, 5, paint);
//                    scanResults.setText(scanResults.getText()+String.valueOf(landmark.getType())+'\n');
//                }



            int x,y,h,w;
            if(face.getPosition().x<0){
                x = 0;
                w = (int)(face.getWidth() + face.getPosition().x);
            }
            else{
                x = (int) face.getPosition().x;
                w = (int)face.getWidth();
            }
            if(face.getPosition().y<0){
                y = 0;
                h = (int)(face.getHeight() + face.getPosition().y);
            }
            else{
                y = (int) face.getPosition().y;
                h = (int) face.getHeight();
            }
            Bitmap nnew = Bitmap.createBitmap(editedBitmap, x,y,w, h);
            Bitmap resized = Bitmap.createScaledBitmap(nnew, 300, 300, true);
            Frame frame1 = new Frame.Builder().setBitmap(resized).build();
            SparseArray<Face> faces1 = detector.detect(frame1);
            float left_eye_x, right_eye_x, left_nose_x, right_nose_x, left_mouth_x, right_mouth_x;
            float left_eye_y, right_eye_y, left_nose_y, right_nose_y, left_mouth_y, right_mouth_y;
            left_eye_x = faces1.valueAt(0).getLandmarks().get(1).getPosition().x;
            left_eye_y = faces1.valueAt(0).getLandmarks().get(1).getPosition().y;
            right_eye_x = faces1.valueAt(0).getLandmarks().get(0).getPosition().x;
            right_eye_y = faces1.valueAt(0).getLandmarks().get(0).getPosition().y;
            left_mouth_x = faces1.valueAt(0).getLandmarks().get(5).getPosition().x;
            left_mouth_y = faces1.valueAt(0).getLandmarks().get(5).getPosition().y;
            right_mouth_x = faces1.valueAt(0).getLandmarks().get(6).getPosition().x;
            right_mouth_y = faces1.valueAt(0).getLandmarks().get(6).getPosition().y;
            left_nose_x = (faces1.valueAt(0).getLandmarks().get(2).getPosition().x+faces1.valueAt(0).getLandmarks().get(3).getPosition().x)/2;
            left_nose_y = (faces1.valueAt(0).getLandmarks().get(2).getPosition().y+faces1.valueAt(0).getLandmarks().get(3).getPosition().y)/2;
            right_nose_x = (faces1.valueAt(0).getLandmarks().get(2).getPosition().x+faces1.valueAt(0).getLandmarks().get(4).getPosition().x)/2;
            right_nose_y = (faces1.valueAt(0).getLandmarks().get(2).getPosition().y+faces1.valueAt(0).getLandmarks().get(4).getPosition().y)/2;
            float md1, md2, md3, md4, md5, md6;
            showMessage("ekhane");
            md1 = (float)Math.sqrt(Math.pow((left_eye_x - left_mouth_x), 2) + Math.pow((left_eye_y - left_mouth_y), 2));
            md5 = (float)Math.sqrt(Math.pow((left_eye_x - right_mouth_x), 2) + Math.pow((left_eye_y - right_mouth_y), 2));
            md6 = (float)Math.sqrt(Math.pow((right_eye_x - left_mouth_x), 2) + Math.pow((right_eye_y - left_mouth_y), 2));
            md2 = (float)Math.sqrt(Math.pow((right_eye_x - right_mouth_x), 2) + Math.pow((right_eye_y - right_mouth_y), 2));
            md3 = (float)Math.sqrt(Math.pow((left_nose_x - left_mouth_x), 2) + Math.pow((left_nose_y - left_mouth_y), 2));
            md4 = (float)Math.sqrt(Math.pow((right_nose_x - right_mouth_x), 2) + Math.pow((right_nose_y - right_mouth_y), 2));

            showMessage("ekhane1");
            md1 = md1 / (md5 + md6);
            md2 = md2 / (md5 + md6);
            md3 = md3 / (md5 + md6);
            md4 = md4 / (md5 + md6);
            md1 = (float) Math.floor(md1*1000)/1000;
            md2 = (float) Math.floor(md2*1000)/1000;
            md3 = (float) Math.floor(md3*1000)/1000;
            md4 = (float) Math.floor(md4*1000)/1000;
            ArrayList<Float> values = new ArrayList<>();
            values.add(2*md1);
            values.add(2*md2);
            values.add(2*md3);
            values.add(2*md4);
            Collection<Float> features = values;

//                scanResults.setText(scanResults.getText() + "md1: "+ String.valueOf(2*md1) + "\n");
//                scanResults.setText(scanResults.getText() + "md2: "+ String.valueOf(2*md2) + "\n");
//                scanResults.setText(scanResults.getText() + "md3: "+ String.valueOf(2*md3) + "\n");
//                scanResults.setText(scanResults.getText() + "md4: "+ String.valueOf(2*md4) + "\n");
//                scanResults.setText(scanResults.getText() + "\nPrediction: "+ readFileAndPredict(features) + "\n");

            String content = "\n\nParams for image "+ imageName + ":" +
                    "\ndistanceParameter1 = "+ String.valueOf(2*md1) +
                    "\ndistanceParameter2 = "+ String.valueOf(2*md2) +
                    "\ndistanceParameter3 = "+ String.valueOf(2*md3) +
                    "\ndistanceParameter4 = "+ String.valueOf(2*md4);

            File file=new File(Environment.getExternalStorageDirectory()+"/dirr");
            if(!file.isDirectory()){
                file.mkdir();
            }
            file=new File(Environment.getExternalStorageDirectory()+"/dirr","ImageCollectorLog.txt");
            if (!file.exists())
            {
                try {
                    file.createNewFile(); // ok if returns false, overwrite
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try
            {
                BufferedWriter bw = null;
                FileWriter fw = null;

                fw = new FileWriter(file.getAbsoluteFile(), true);
                bw = new BufferedWriter(fw);

                bw.write(content);
                bw.flush();
                fw.flush();
                bw.close();
                fw.close();
            }
            catch(IOException e){
                e.printStackTrace();
            }
            catch(Exception exception)
            {
                exception.printStackTrace();
            }


        }
    }
}