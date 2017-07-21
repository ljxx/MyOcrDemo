package com.ylx.myocrdemo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.yanzhenjie.durban.Controller;
import com.yanzhenjie.durban.Durban;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import static com.ylx.myocrdemo.OcrUtil.ENGLISH_LANGUAGE;
import static com.ylx.myocrdemo.OcrUtil.TESSBASE_PATH;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    CameraView mCameraView;
    ImageView mImageView;
    private Button mOpenBtn, mKnowBtn;


    /**
     * TessBaseAPI初始化用到的第一个参数，是个目录。
     */
    public static final String DATAPATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    /**
     * 在DATAPATH中新建这个目录，TessBaseAPI初始化要求必须有这个目录。
     */
    private static final String tessdata = DATAPATH + "tessdata";
    /**
     * TessBaseAPI初始化测第二个参数，就是识别库的名字不要后缀名。
     */
    private static final String DEFAULT_LANGUAGE = "eng";
//    private static final String DEFAULT_LANGUAGE = "chi_sim";
    /**
     * assets中的文件名
     */
    private static final String DEFAULT_LANGUAGE_NAME = DEFAULT_LANGUAGE + ".traineddata";
    /**
     * 保存到SD卡中的完整文件名
     */
    private static final String LANGUAGE_PATH = tessdata + File.separator + DEFAULT_LANGUAGE_NAME;

    public static boolean isStartScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCopy();
        setContentView(R.layout.activity_main);
        mCameraView = (CameraView) findViewById(R.id.main_camera);
        mImageView = (ImageView) findViewById(R.id.main_image);
        mOpenBtn = (Button) findViewById(R.id.open_btn);
        mKnowBtn = (Button) findViewById(R.id.know_btn);
        initListener();
    }

    private static final int REQUEST_CODE = 123;
    /**
     * 监听
     */
    private void initListener() {
        /**
         * 开始识别
         */
        mKnowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isStartScanning = true;
            }
        });

        /**
         * 从相册选取
         */
        mOpenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent innerIntent = new Intent();
                innerIntent.setAction(Intent.ACTION_GET_CONTENT);
                innerIntent.setType("image/*");
                Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
                startActivityForResult(wrapperIntent, REQUEST_CODE);
                isStartScanning = false;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE:
                    // 从本地取出照片
                    String[] proj = {MediaStore.Images.Media.DATA};
                    // 获取选中图片的路径
                    String photoPath = null;
                    Cursor cursor = getContentResolver().query(data.getData(), proj, null, null, null);
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        photoPath = cursor.getString(columnIndex);
                        if (photoPath == null) {
                            photoPath = ScanUtils.getPath(getApplicationContext(), data.getData());
                            setResult(photoPath);
                        }
                    }
                    cursor.close();
                    break;
                case 200:
                    ArrayList<String> mImageList = Durban.parseResult(data);
                    String mPicUrl = mImageList.get(0);
                    Log.i("=picurl===","==="+mPicUrl);
                    if(!TextUtils.isEmpty(mPicUrl)){
                        jiexi(mPicUrl);
                    }
                    break;
            }
        }
    }

    private ProgressDialog dialog;
    private void jiexi(String mPicUrl) {

        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = new ProgressDialog(this);
        dialog.setTitle("请稍等");
        dialog.setMessage("正在识别中...");
        dialog.setIndeterminate(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.show();

        Bitmap bitmap = BitmapUtils.getCompressedBitmap(mPicUrl);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);


        //开始识别
        ScanEnglish(bitmap, new MyCallBack() {
            @Override
            public void response(String result) {
                isStartScanning = false;
                if(dialog != null){
                    dialog.dismiss();
                }
                if(!TextUtils.isEmpty(result)){
                    ResponseResultActivity.jumpResponseResultActivity(MainActivity.this, result);
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "主人，我没看清楚。。。", Toast.LENGTH_SHORT).show();
                        }
                    });

                }
            }
        });
    }

    /**
     * 识别英文
     *
     * @param bmp      需要识别的图片
     * @param callBack 结果回调（携带一个String 参数即可）
     */
    public static void ScanEnglish(final Bitmap bmp, final MyCallBack callBack) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                TessBaseAPI baseApi = new TessBaseAPI();
                //初始化OCR的字体数据，TESSBASE_PATH为路径，ENGLISH_LANGUAGE指明要用的字体库（不用加后缀）
                if (baseApi.init(TESSBASE_PATH, ENGLISH_LANGUAGE)) {
                    //设置识别模式
                    baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                    baseApi.setImage(bmp);
                    //开始识别
                    String result = "";
                    result = baseApi.getUTF8Text();
                    baseApi.clear();
                    baseApi.end();
                    callBack.response(result);
                }

            }
        }).start();
    }

    private void setResult(String picturePath) {

        Durban.with(MainActivity.this)
                .statusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                .toolBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .navigationBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .inputImagePaths(picturePath)
                //.outputDirectory(cropDirectory)
                .maxWidthHeight(500, 500)
                .aspectRatio(1, 1)
                .compressFormat(Durban.COMPRESS_JPEG)
                .compressQuality(90)
                // Gesture: ROTATE, SCALE, ALL, NONE.
                .gesture(Durban.GESTURE_ALL)
                .controller(Controller.newBuilder()
                        .enable(false)
                        .rotation(true)
                        .rotationTitle(true)
                        .scale(true)
                        .scaleTitle(true)
                        .build())
                .requestCode(200)
                .start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isStartScanning = false;
        CameraView.isScanning = false;
        mCameraView.setTag(mImageView);
    }

    /**
     * 权限请求值
     */
    private static final int PERMISSION_REQUEST_CODE=0;
    private void initCopy(){
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }

        //Android6.0之前安装时就能复制，6.0之后要先请求权限，所以6.0以上的这个方法无用。
        copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME);
    }

    /**
     * 将assets中的识别库复制到SD卡中
     * @param path  要存放在SD卡中的 完整的文件名。这里是"/storage/emulated/0//tessdata/chi_sim.traineddata"
     * @param name  assets中的文件名 这里是 "chi_sim.traineddata"
     */
    public void copyToSD(String path, String name) {
        Log.i(TAG, "copyToSD: "+path);
        Log.i(TAG, "copyToSD: "+name);

        //如果存在就删掉
        File f = new File(path);
        if (f.exists()){
            f.delete();
        }
        if (!f.exists()){
            File p = new File(f.getParent());
            if (!p.exists()){
                p.mkdirs();
            }
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InputStream is=null;
        OutputStream os=null;
        try {
            is = this.getAssets().open(name);
            File file = new File(path);
            os = new FileOutputStream(file);
            byte[] bytes = new byte[2048];
            int len = 0;
            while ((len = is.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null)
                    is.close();
                if (os != null)
                    os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }



}
