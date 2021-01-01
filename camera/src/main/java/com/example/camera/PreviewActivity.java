package com.example.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.support.media.ExifInterface;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import java.io.IOException;

//https://www.jianshu.com/p/95cd95e961d7
//https://blog.csdn.net/qq_33240767/article/details/82254208
public class PreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        String filePath = getIntent().getStringExtra("filePath");

        ImageView imageView1 = findViewById(R.id.iv);

        imageView1.setImageBitmap(BitmapFactory.decodeFile(filePath));

        ImageView imageView2 = findViewById(R.id.iv2);
        imageView2.setImageBitmap(setBitmapDegreeZero(filePath));
    }

    public int getPictureDegree(String filePath) {
        int degree = 0;
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (exifInterface != null) {
            //获得图片拍摄角度，第二个的作用是如果这个属性不存在，则作为默认值返回
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
                default:
                    //相机方向
                    degree = 90;
                    break;
            }
            return degree;
        }
        return 0;
    }
    /** 旋转图片
     * @param imgPath 原图路径
     * @ param imgPath
     */
    public Bitmap setBitmapDegreeZero(String imgPath) {
        Bitmap mBitmap = null;
        int degree = getPictureDegree(imgPath);
        mBitmap = BitmapFactory.decodeFile(imgPath);
        if (degree != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(),
                    mBitmap.getHeight(), matrix, true);
        }
        return mBitmap;
    }
}