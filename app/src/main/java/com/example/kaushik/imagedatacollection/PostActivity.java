package com.example.kaushik.imagedatacollection;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PostActivity extends AppCompatActivity implements SensorEventListener, ActivityCompat.OnRequestPermissionsResultCallback{
    ImageCapture imageCapture;
    float pressure=0;
    int del=0;
    double acceleration=0, norm_Of_g=0;
    int inclination=0, rotation=0, inclinationTotal=0;
    int i;
    int l1 = 0, sugg = 0;
    long a1=0,b1=0;
    long c1=0;
    long startTime,endTime;
    float startSpeed, endSpeed,startAcc,endAcc;
    int startAngle,endAngle, startRotation,endRotation;
    private long lastUpdate = 0;
    private float last_x=0, last_y=0, last_z=0;
    public Vibrator v;
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    float speed,accelationSquareRoot=0,accelationTotal=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        TextView userNameTextView = findViewById(R.id.userNameTextViewId);
        final TextView postNoTextView = findViewById(R.id.imageNoTextViewId);
        final Button nextButton = findViewById(R.id.nextButtonId);
        final ImageView postImageView = findViewById(R.id.postImageViewId);
        final ImageButton isLikedImageButton = findViewById(R.id.isLikedImageButtonId);
        final TextView isLikedTextView = findViewById(R.id.isLikedTextViewId);
        final EditText commentEditText = findViewById(R.id.commentEditTextId);
        Button commentButton = findViewById(R.id.commentButtonId);
        final ListView commentListView = findViewById(R.id.commentListViewId);
        final int[] postNo = {1};
        final int[] count = {1};
        final boolean[] isLiked = {false};
        final List<String> comments = new ArrayList<>();
        final long keyBoardDynamicsFeatures[][]=new long[20][9];
        commentEditText.setFocusableInTouchMode(true);
        //commentEditText.setFocusable(false);
        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        final ArrayAdapter<String> commentListAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, comments);
        commentListView.setAdapter(commentListAdapter);

        final Intent intent = getIntent();
        final String userName = intent.getExtras().getString("userName");
        userNameTextView.setText(userName);
        initializePost(postNo[0], postNoTextView, postImageView, isLikedTextView);
        //Intent serviceIntent = new Intent(PostActivity.this, PhotoTakingService.class);
        //PostActivity.this.startService(serviceIntent);

        imageCapture = new ImageCapture(getApplicationContext());
        imageCapture.openCamera();

        final Handler handler = new Handler();
        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        imageCapture.takePicture();
                        //Toast.makeText(PhotoTakingService.this, "Time", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };
        Timer timer = new Timer();
        timer.schedule(timertask, 0, 2000);

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (postNo[0] == 14)
                {
                    nextButton.setText("Finish");
                }
                if (postNo[0] >= 15)
                {
                    Intent intent1 = new Intent(PostActivity.this, MainActivity.class);
                    startActivity(intent1);
                }
                else
                {
                    count[0] = 1;
                    postNo[0]++;
                    initializePost(postNo[0], postNoTextView, postImageView, isLikedTextView);
                    isLikedImageButton.setImageResource(R.drawable.like_unpessed);
                    comments.clear();
                    commentListAdapter.notifyDataSetChanged();
                    setListViewHeightBasedOnChildren(commentListView);
                    isLiked[0] = false;
                }
            }
        });

        final int[] charCount = {0};

        isLikedImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isLiked[0])
                {
                    isLiked[0] = false;
                    isLikedImageButton.setImageResource(R.drawable.like_unpessed);
                    isLikedTextView.setText("You have not liked this post yet");
                }
                else
                {
                    isLiked[0] = true;
                    isLikedImageButton.setImageResource(R.drawable.like_pressed);
                    isLikedTextView.setText("You have liked this post");

                }
            }
        });


        commentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                if (charSequence.length() - charCount[0] == 10)
                {
                    charCount[0] += 10;
                }

            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        commentEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus)
                {

                    a1=System.currentTimeMillis();
                    startTime=a1;
                    startAcc=0;
                    startSpeed=0;
                    startAngle=0;
                    startRotation=0;
                    endTime=0;
                    endAcc=0;
                    endSpeed=0;
                    endAngle=0;
                    endRotation=0;
                    //Toast.makeText(PostActivity.this, "startTime: "+String.valueOf(a1), Toast.LENGTH_SHORT).show();

                }
                else if(!hasFocus)
                {
                    b1=System.currentTimeMillis();
                    if(b1>=a1)
                    {

                        c1=c1+(b1-a1);
                    }
                    //Toast.makeText(PostActivity.this, "endTime: "+String.valueOf(b1), Toast.LENGTH_SHORT).show();

                    keyBoardDynamicsFeatures[postNo[0]][1]=keyBoardDynamicsFeatures[postNo[0]][1]+c1; //Time
                    keyBoardDynamicsFeatures[postNo[0]][2]= (long) (keyBoardDynamicsFeatures[postNo[0]][2]+speed); //speed
                    keyBoardDynamicsFeatures[postNo[0]][3]= (long) (keyBoardDynamicsFeatures [postNo[0]][3]+accelationTotal); //acceleration
                    keyBoardDynamicsFeatures[postNo[0]][4]=  (keyBoardDynamicsFeatures [postNo[0]][4]+inclination); //angle
                    keyBoardDynamicsFeatures[postNo[0]][5]=  (keyBoardDynamicsFeatures [postNo[0]][5]+rotation); //rotation
                    keyBoardDynamicsFeatures[postNo[0]][6]= (long) (keyBoardDynamicsFeatures[postNo[0]][6]+pressure); //touch force
                    keyBoardDynamicsFeatures[postNo[0]][7]= (long) (keyBoardDynamicsFeatures[postNo[0]][7]+del); //delete
                    keyBoardDynamicsFeatures[postNo[0]][8]= (long) (keyBoardDynamicsFeatures[postNo[0]][8]+sugg); //sugg
                    speed=0;
                    accelationTotal=0;
                    accelationSquareRoot=0;
                    rotation=0;
                    inclination=0;
                    c1=0;
                    pressure=0;
                    del=0;
                }
            }
        });

        commentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                l1=0;
                String comment = commentEditText.getText().toString();
                commentEditText.clearFocus();
                if (!comment.isEmpty()) {
                    comments.add(comment);
                    commentListAdapter.notifyDataSetChanged();
                    setListViewHeightBasedOnChildren(commentListView);
                    commentEditText.setText("");
                    double typingSpeed, shakePerLength,accelerationPerLength,anglePerLength,rotationPerLength;

                    keyBoardDynamicsFeatures[postNo[0]][0]=comment.length();
                    //lengthIimeSpeedAcceleration[j][2]=lengthIimeSpeedAcceleration[j][0]/lengthIimeSpeedAcceleration[j][1];
                    //Log.e("try",lengthIimeSpeedAccelerationAngleRotation[j][0]+"");
                    //values to submit on server



                    typingSpeed=(double)keyBoardDynamicsFeatures[postNo[0]][0]*1000/(double)keyBoardDynamicsFeatures[postNo[0]][1];
                    shakePerLength=keyBoardDynamicsFeatures[postNo[0]][2]/keyBoardDynamicsFeatures[postNo[0]][0];
                    accelerationPerLength=keyBoardDynamicsFeatures[postNo[0]][3]/keyBoardDynamicsFeatures[postNo[0]][0];
                    anglePerLength=keyBoardDynamicsFeatures[postNo[0]][4]/keyBoardDynamicsFeatures[postNo[0]][0];
                    rotationPerLength=keyBoardDynamicsFeatures[postNo[0]][5]/keyBoardDynamicsFeatures[postNo[0]][0];



                    String content = "\n\nComment : " + comment + "\nTyping Speed : " + typingSpeed+
                            "\nShake Per Length : "+shakePerLength+"\nAcceleration Per Length : "+
                            accelerationPerLength+
                            "\nAngel Per Length : "+anglePerLength+"\nRotation Per Length : "+rotationPerLength+"\n";

                    //Toast.makeText(PostActivity.this, content, Toast.LENGTH_SHORT).show();
                    InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                    File file=new File(Environment.getExternalStorageDirectory()+"/dirr");
                    if(!file.isDirectory()){
                        file.mkdir();
                    }
                    file=new File(Environment.getExternalStorageDirectory()+"/dirr","CommentCollectorLog.txt");
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
        });
    }

    public void initializePost(int postNo, TextView postNoTextView, ImageView postImageView,
                                      TextView isLikedTextView)
    {
        postNoTextView.setText("Post " + postNo);
        final int postId = PostActivity.this.getResources().getIdentifier("post"+ postNo, "drawable", getPackageName());
        postImageView.setImageResource(postId);
        isLikedTextView.setText("You have not liked this post yet");
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(PostActivity.this, "Please finish the survey", Toast.LENGTH_LONG).show();
        moveTaskToBack(false);
    }

    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                speed = speed+ (Math.abs(x + y + z - last_x - last_y - last_z)/ diffTime * 10000);
                acceleration = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2)) - SensorManager.GRAVITY_EARTH;

                accelationSquareRoot = (x*x + y*y + z*z)/ (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);

                norm_Of_g = Math.sqrt(x*x + y*y + z*z);
                inclination =  (int) Math.round(Math.toDegrees(Math.acos(x/norm_Of_g)));

                if (inclination < 25 || inclination > 155)
                {
                    // device is flat
                    inclinationTotal=inclinationTotal+Math.abs(inclination);
                }
                else
                {
                    // device is not flat
                    rotation = rotation+ Math.abs((int) Math.round(Math.toDegrees(Math.atan2(x/norm_Of_g, y/norm_Of_g))));
                }
                accelationTotal=accelationTotal+Math.abs(accelationSquareRoot);

                last_x=x;
                last_y=y;
                last_z=z;

                endSpeed=speed;
                endAcc= (float) acceleration;
                endAngle=inclinationTotal;
                endRotation=rotation;

            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    protected void onPause() {
        super.onPause();
        senSensorManager.unregisterListener(this);
    }

    protected void onResume() {
        super.onResume();
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }


    private void getPermissions(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                //Requesting permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    @Override //Override from ActivityCompat.OnRequestPermissionsResultCallback Interface
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted
                }
                return;
            }
        }
    }
    private static void showMessage(String message) {
        Log.i("File", message);
    }
}
