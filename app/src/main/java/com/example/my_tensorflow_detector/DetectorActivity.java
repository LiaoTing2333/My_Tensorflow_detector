package com.example.my_tensorflow_detector;

/**
 * Created by 不闻不问不听不看不在乎~ on 2018/11/18.
 */

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.my_tensorflow_detector.env.BorderedText;
import com.example.my_tensorflow_detector.env.ImageUtils;
import com.example.my_tensorflow_detector.env.Logger;
import com.example.my_tensorflow_detector.tracking.MultiBoxTracker;
import com.example.my_tensorflow_detector.OverlayView.DrawCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
           // "file:///android_asset/frozen_inference_graph.pb";
    "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE =
           // "file:///android_asset/graph.txt";
    "file:///android_asset/coco_labels_list.txt";

    private enum DetectorMode {
        TF_OD_API, YOLO;
    }

    //图片识别模型的种类
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    //最小的识别率60%
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.45f;

    private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;
    //理想的图片大小
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    //是否存储之前的图片
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    //文本的大小像素（设备独立像素）
    private static final float TEXT_SIZE_DIP = 10;
    //屏幕的方向
    private Integer sensorOrientation;

    private Classifier detector;
    //持续的时间
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    //创建多框检测器类对象
    private MultiBoxTracker tracker;

    //亮度
    private byte[] luminanceCopy;
    //描述文本类
    private BorderedText borderedText;

    OverlayView trackingOverlay;

    private List<Classifier.Recognition> results;

    private String detector_classes;
    private float detector_confidence;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        Log.e("onPreviewSizeChosen", "hello");
        //单位转换，将10dip转换成px值（像素值）
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        //创建边缘文本类对象
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);//等宽字体

        tracker = new MultiBoxTracker(this);
        //输入图片的大小
        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            //调用tensorflow库
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
        //获取尺寸的长宽
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
        //方向
        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        //压缩质量参数 Config.ARGB_8888 32位的ARGB位图
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        //裁剪图片跟模型图片一样大小,返回一个矩阵类
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        //求矩阵的逆矩阵
        cropToFrameTransform = new Matrix();
        //返回值为布尔型，判断该矩阵是否有逆矩阵
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        //调用MultiBoxTracker中的draw()方法
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

/*
        //调用CameraActivity中的addCallback中的方法
        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        Log.e("CameraActivity", "addCallback");
                        if (!isDebug()) {
                            return;
                        }
                        final Bitmap copy = cropCopyBitmap;
                        if (copy == null) {
                            return;
                        }
                        // 设置背景颜色（透明度，红， 黄， 蓝）0为完全不透明， 255是完全透明
                        final int backgroundColor = Color.argb(100, 0, 0, 0);
                        canvas.drawColor(backgroundColor);

                        final Matrix matrix = new Matrix();
                        final float scaleFactor = 2;
                        //放大图像
                        matrix.postScale(scaleFactor, scaleFactor);
                        //平移图像到右下角
                        matrix.postTranslate(
                                canvas.getWidth() - copy.getWidth() * scaleFactor,
                                canvas.getHeight() - copy.getHeight() * scaleFactor);
                        canvas.drawBitmap(copy, matrix, new Paint());

                        //可变长的数组，添加运行状态
                        final Vector<String> lines = new Vector<String>();
                        if (detector != null) {
                            final String statString = detector.getStatString();
                            final String[] statLines = statString.split("\n");
                            for (final String line : statLines) {
                                lines.add(line);
                            }
                        }
                        lines.add("");

                        lines.add("Frame: " + previewWidth + "x" + previewHeight);
                        lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                        lines.add("Rotation: " + sensorOrientation);
                        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

                        Log.e("lines ", lines.toString());
                        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
                    }
                });
                */
        Log.e("onPreview", "finish");
    }

    //重写CameraActivity中的processImage方法
    @Override
    protected void processImage() {
        Log.e("DetectorActivity", "processImage");
        ++timestamp;//时间戳
        final long currTimestamp = timestamp;
        //获取YUV编码
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                sensorOrientation,
                originalLuminance,
                timestamp);
        trackingOverlay.postInvalidate();//刷新

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();//启动可复用缓冲区的线程
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        //（像素数组,偏移量,(一行的像素个数)bitmap的宽度,开始绘图的X坐标,开始绘图的Y坐标,图的宽度,图的长度）
        //调用CameraActivity中的getRbgBytes()，启动将YUV420SP转换成ARGB的线程，返回亮度值
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        //亮度复制数组
        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        //该方法可用于数组之间的复制
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        //开启缓冲线程
        readyForNextImage();

        //在croppedBitmap中建立画布
        final Canvas canvas = new Canvas(croppedBitmap);
        //（bitmap, matrix, paint）
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }
        //调用CameraActivity中的runInBackground()方法
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        //从开机到现在的毫秒数
                        final long startTime = SystemClock.uptimeMillis();
                        Log.e("croppedBitmap", croppedBitmap.toString());
                        //调用TensorflowObjectDetectedApiModel中的recognizeImage()方法
                        //返回识别的结果
                        results = detector.recognizeImage(croppedBitmap);
                        //识别过程中耗费的时间
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        //复制位图
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        //在cropCopyBitmap中创建画布
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        //创建画笔
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);//只绘制图形轮廓
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            //获取预测结果，如果准确率大于最小值，则画出识别框
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                detector_classes = result.getTitle();
                                detector_confidence = result.getConfidence();
                                //画矩形
                                canvas.drawRect(location, paint);
                                //判断一个矩形经过变换后是否还是矩形，测量location放入location
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                        trackingOverlay.postInvalidate();

                        requestRender();
                        computingDetection = false;
                    }
                });

        Button ok = (Button) findViewById(R.id.yes);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
                Bundle b = new Bundle();
                b.putString("style","take_photo");
                b.putParcelable("bitmap", cropCopyBitmap);
                b.putString("detector_classes",detector_classes);
                b.putFloat("detector_confidence", detector_confidence*100);
                Intent intent = new Intent(DetectorActivity.this, ShowActivity.class);
                intent.putExtras(b);
                startActivity(intent);
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(final boolean debug) {
        detector.enableStatLogging(debug);
    }

}
