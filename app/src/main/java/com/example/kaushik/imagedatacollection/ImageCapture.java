package com.example.kaushik.imagedatacollection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;



public class ImageCapture extends AsyncTask<String, String, String> {

    Context context;
    private static final String TAG = "CCV2WithoutPreview";
    private String mCameraId;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private Handler backgroundHandlerLog;
    private HandlerThread backgroundThreadLog;
    private File file;
    private static FaceDetector detector;
    int imageCount = 1;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    public ImageCapture(Context context)
    {
        this.context = context;
        detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();
    }


    @Override
    protected String doInBackground(String... strings) {
        return null;
    }

    public void openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            startBackgroundThread();
            //startBackgroundThreadLog();
            manager.openCamera(mCameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
                imageReader.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            showMessage("On Error" + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };
    private static void showMessage(String message) {
        Log.i("File", message);
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "ImageAvailable");
            Image image = reader.acquireNextImage();
            backgroundHandler.post(new ImageSaver(image, file));
            //backgroundHandlerLog.post(new ImageLogSaver(image));
        }

    };

    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
            //stopBackgroundThreadLog();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThreadLog() {
        backgroundThreadLog = new HandlerThread("CameraBackground");
        backgroundThreadLog.start();
        backgroundHandlerLog = new Handler(backgroundThreadLog.getLooper());
    }

    private void stopBackgroundThreadLog() {
        backgroundThreadLog.quitSafely();
        try {
            backgroundThreadLog.join();
            backgroundThreadLog = null;
            backgroundThreadLog = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraCaptureSession() {
        try {

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Configuration Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() {
        file = getOutputMediaFile();
        try {
            if (null == mCameraDevice) {
                showMessage("Camera is Null");
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.d(TAG, file.toString());
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Toast.makeText(context, "Captured " + imageCount, Toast.LENGTH_SHORT).show();
    }

    private class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            Bitmap mbitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            try {
                scanFaces(mbitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void scanFaces(Bitmap bitmap) throws Exception {
            if (detector.isOperational() && bitmap != null) {
                showMessage("Inside Scan");
                Bitmap editedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap
                        .getHeight(), bitmap.getConfig());
                float scale = context.getResources().getDisplayMetrics().density;
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
                showMessage("ekhane");
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

                String content = "\n\nParams for image " + imageCount +":" +
                        "\ndistanceParameter1 = "+ String.valueOf(2*md1) +
                        "\ndistanceParameter2 = "+ String.valueOf(2*md2) +
                        "\ndistanceParameter3 = "+ String.valueOf(2*md3) +
                        "\ndistanceParameter4 = "+ String.valueOf(2*md4);
                showMessage(content);


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
            else showMessage("Bitmap is Null");
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Camera2Test");

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        // Create a media file name
        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + imageCount + ".jpg");
        imageCount++;

        return mediaFile;
    }

}
