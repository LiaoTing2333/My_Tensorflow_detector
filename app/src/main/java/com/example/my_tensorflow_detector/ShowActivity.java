package com.example.my_tensorflow_detector;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.my_tensorflow_detector.env.ImageUtils;
import com.example.my_tensorflow_detector.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 不闻不问不听不看不在乎~ on 2018/11/30.
 */

public class ShowActivity extends Activity {
    private Bitmap bmp;
    ImageView imageView;
    TextView detector_classes;
    TextView detector_confidence;

    private Bitmap pre_bitmap;
    private Bitmap bitmap;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    OverlayView trackingOverlay;
    //创建多框检测器类对象
    private MultiBoxTracker tracker;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private long timestamp = 0;
    private List<Classifier.Recognition> results;
    private int[] intValues =  new int[1000 *1000];

    private String classes = null;
    private float confidence = -1;
    String imagePath = null;

    private Classifier detector;
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/frozen_inference_graph.pb";
    //"file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE =
            "file:///android_asset/graph.txt";
    //"file:///android_asset/coco_labels_list.txt";

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1:
                    String classes = msg.obj.toString().split(":")[0];
                    detector_classes.setText(classes);
                    float confidence = Float.parseFloat(msg.obj.toString().split(":")[1])*100;
                    detector_confidence.setText(confidence+"%");

                    break;
                case 0:
                    detector_classes.setText("failed to identify!");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.show_activity);

        imageView = (ImageView) findViewById(R.id.imageView);
        Log.e("bitmap", imageView.getTop()+":"+imageView.getLeft());

        detector_classes = (TextView) findViewById(R.id.detector_classes);
        detector_confidence = (TextView) findViewById(R.id.detector_confidence);
        //获取从Intent获取的数据
        Intent intent = getIntent();
        Bundle b = intent.getExtras();
        //如果是拍照
        if (b.getString("style").equals("take_photo")) {
            bitmap = (Bitmap) b.getParcelable("bitmap");
            classes = b.getString("detector_classes");
            confidence = b.getFloat("detector_confidence");
            detector_classes.setText(classes);
            detector_confidence.setText(confidence+"%");

        } else if(b.getString("style").equals("choose_photo")){//从相册中选择
            imagePath = b.getString("imagePath");
            if (imagePath != null) {
                pre_bitmap = BitmapFactory.decodeFile(imagePath);
                bitmap = changeBitmap(pre_bitmap, 300, 300);
                bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth()/4, bitmap.getHeight()/4);

                detect_image(bitmap);
           }

        }

        imageView.setImageBitmap(bitmap);

    }

    private Bitmap changeBitmap(Bitmap bitmap, int rx, int ry){
        int height=bitmap.getHeight();
        int width=bitmap.getWidth();
        Log.e("width height", height+":"+width);
        //缩放比列
        float scaleWidth=((float)rx)/width;
        float scaleHeight=((float)ry)/height;
        //矩阵类
        Matrix matrix=new Matrix();
        matrix.postScale(scaleWidth,scaleHeight);

        if(width>height){
            matrix.postRotate(90);
        }

        //位图裁剪
        bitmap=Bitmap.createBitmap(bitmap,0,0,width,height, matrix, true);
        Log.e("changebitmap", bitmap.getWidth()+":"+bitmap.getHeight());
        return bitmap;
    }

    private void  detect_image(Bitmap pre_bitmap){
        Bundle bundle = new Bundle();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message message = Message.obtain(handler);
                try {
                    //调用tensorflow库
                    detector = TensorFlowObjectDetectionAPIModel.create(
                            getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);

                } catch (final IOException e) {
                    Toast toast =
                            Toast.makeText(
                                    getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                    toast.show();
                    finish();
                }

                results = detector.recognizeImage(pre_bitmap);
                Log.e("result", results.size()+""+results.get(0).getTitle()+results.get(0).getConfidence());
                message.what = 0;
                for (final Classifier.Recognition result : results) {
                    final RectF location = result.getLocation();
                    //获取预测结果，如果准确率大于最小值，则画出识别框
                    if (location != null && result.getConfidence() >= 0.47f){
                        Log.e("result", result.getTitle()+":"+result.getConfidence());
                        message.what = 1;
                        message.obj = result.getTitle()+":"+result.getConfidence()+":"+result.getLocation();
                        //message.obj = result.getLocation();
                        Log.e("location", result.getLocation().left+"");

                        Canvas canvas = new Canvas(bitmap);
                        Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);//只绘制图形轮廓
                        paint.setStrokeWidth(2.0f);

                        RectF rectF = new RectF(result.getLocation());

                        canvas.drawRect(rectF, paint);

                        message.setData(bundle);
                        //handler.sendMessage(message);
                    }
                    Log.e("time", SystemClock.uptimeMillis()+"");
                }
                handler.sendMessage(message);
            }
        }).start();
    }

}
