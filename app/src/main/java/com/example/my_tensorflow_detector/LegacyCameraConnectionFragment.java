package com.example.my_tensorflow_detector;

/**
 * Created by 不闻不问不听不看不在乎~ on 2018/11/18.
 */

import android.app.Fragment;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.example.my_tensorflow_detector.env.ImageUtils;
import com.example.my_tensorflow_detector.env.Logger;

import java.io.IOException;
import java.util.List;


public class LegacyCameraConnectionFragment extends Fragment {
    private String  TAG = "LegacyCameraConnection";
    private Camera camera;
    private static final Logger LOGGER = new Logger();
    private Camera.PreviewCallback imageListener;
    private Size desiredSize;

    /**
     * The layout identifier to inflate for this Fragment.
     */
    private int layout;

    //相机预览
    private AutoFitTextureView textureView;

    //线程类
    private HandlerThread backgroundThread;

    //该类是利用Integer去管理object对象，它能在索引数中快速的查找到所需的结果
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    //向其中存入信息，（index, value）
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

   //SurfaceView的工作方式是创建一个置于应用窗口之后的新窗口，不能使用变换，设置透明度等
    //TextureView则可以，实现它，需要获取用于渲染内容的SurfaceTexture（创建其对象，实现SurfaceTextureListener接口）
    //SurfaceTexture能铺货一个图像流的一帧
    //但是只能用在硬件加速的窗口即Android.layerType=software
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                //在SurfaceTexture准备使用时调用
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {

                    //获取一个Camera对象
                    int index = getCameraId();
                    camera = Camera.open(index);

                    try {
                        //获取camera拍照参数
                        Camera.Parameters parameters = camera.getParameters();
                        //获取手机支持的对焦模式
                        List<String> focusModes = parameters.getSupportedFocusModes();
                        //如果手机支持的对焦模式中有持续对焦，则设置手机的对焦模式为持续对焦
                        if (focusModes != null
                                && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                            Log.e("fousModes", focusModes.toString());
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        }
                        //获取Camera支持的Size列表，即视频预览大小即输出到SurfaceView中的视频图片的分辨率大小
                        List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
                        //创建Size数组实例
                        Size[] sizes = new Size[cameraSizes.size()];
                        int i = 0;
                        for (Camera.Size size : cameraSizes) {
                            //创建size实例
                            sizes[i++] = new Size(size.width, size.height);
                            //Log.e("sizes: ", sizes[i].toString());
                        }
                        //选择最佳的尺寸
                        Size previewSize =
                                CameraConnectionFragment.chooseOptimalSize(
                                        sizes, desiredSize.getWidth(), desiredSize.getHeight());
                        Log.e("Size: previewSize", previewSize.toString());
                        //设置视频图片的预览大小
                        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                        //设置camera预览的方向
                        camera.setDisplayOrientation(90);
                        //设置camera的拍照参数
                        camera.setParameters(parameters);
                        //将SurfaceView连接到摄像机，实现实时的相机预览
                        camera.setPreviewTexture(texture);
                    } catch (IOException exception) {
                        camera.release();//释放相机资源
                    }
                    //当相机预览前先分配一个buffer地址
                    camera.setPreviewCallbackWithBuffer(imageListener);
                    //获取相机的视频图片预览大小
                    Camera.Size s = camera.getParameters().getPreviewSize();
                    Log.e("Camera.Size: ", s.toString());
                    //复用原来开辟的内存空间
                    camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);
                    //调用AutoFitTextureText的setAspectRatio方法
                    textureView.setAspectRatio(s.height, s.width);

                    camera.startPreview();//预览取景
                    Log.e("Camera_start","构造" );
                }
                //当SurfaceTexture的缓冲区大小更改时调用
                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {}
                //当SurfaceTexture即将被销毁时调用
                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }
                //当指定SurfaceTexture的更新时调用
                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
            };

    //构造函数
    public LegacyCameraConnectionFragment(
            final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
        this.imageListener = imageListener;
        this.layout = layout;
        this.desiredSize = desiredSize;
        Log.e("LegacyCameraConnect", "构造");
    }

    //为碎片创建视图加载布局时调用
    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        Log.e("LegacyCameraConnect", "onCreateView");
        return inflater.inflate(layout, container, false);
    }

    //创建view
    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        Log.e("LegacyCameraConnect", "onViewCreated");
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }
    //与碎片关联的活动已创建完毕时调用
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.e("LegacyCameraConnect", "onActivityCreated");
    }

    //当活动准备好和用户进行交互的时候调用
    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        //开启相机背景线程
        startBackgroundThread();
        //如果textureView创建了，则直接取景预览，否则先实现其接口
        if (textureView.isAvailable()) {
            camera.startPreview();//开始预览取景
        } else {
            //实现surfaceTextureListener接口
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
        Log.e("LegacyCameraConnect","finish" );
    }

    @Override
    public void onPause() {
        stopCamera();//关闭相机
        stopBackgroundThread();//关闭相机背景线程
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.e("LegacyCameraConnect", "onStart");
    }

    //启动相机背景线程
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
    }

    //关闭相机背景线程
    private void stopBackgroundThread() {
        //当messageQueue中为空时才调用该方法
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();//若遇到耗时长的子线程，主线程需要让子线程执行完后再执行后续
            backgroundThread = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    protected void stopCamera() {
        if (camera != null) {
            camera.stopPreview();//停止预览
            camera.setPreviewCallback(null);//相机预览回调接口设为null，需要在stopPreview()之后
            camera.release();//释放相机资源
            camera = null;
        }
    }
    //Camera类中的getNumberOfCameras可以获取当前设备中的摄像头个数,getCameraInfo可以获取每个摄像头的信息
    //Camera.CameraInfo类中有两个字段，facing定义值：前置和后置，orientation旋转角度
    //返回后置摄像头的下标
    private int getCameraId() {
        CameraInfo ci = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
                return i;
        }
        return -1; // No camera found
    }
}
