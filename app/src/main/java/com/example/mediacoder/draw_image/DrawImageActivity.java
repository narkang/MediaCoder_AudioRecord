package com.example.mediacoder.draw_image;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.mediacoder.R;
import com.example.mediacoder.util.DisplayUtil;

//https://www.cnblogs.com/renhui/p/7456956.html
public class DrawImageActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw_image);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.test, options);

        int screenWidth = getWindowManager().getDefaultDisplay().getWidth();

        BitmapFactory.Options options2 = new BitmapFactory.Options();
        options.inSampleSize = options.outWidth / screenWidth;

        Bitmap testBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test, options2);

        //https://blog.csdn.net/wzygis/article/details/40508699/
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(testBitmap, screenWidth, DisplayUtil.dip2px(this, 100), true);

        //方式一
        ImageView iv1 = findViewById(R.id.iv_1);

        iv1.setImageBitmap(testBitmap);

        //方式二
        SurfaceView iv2 = findViewById(R.id.iv_2);
        iv2.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                if (surfaceHolder == null) {
                    return;
                }

                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);

                Canvas canvas = surfaceHolder.lockCanvas();  // 先锁定当前surfaceView的画布
                canvas.drawBitmap(scaledBitmap, 0, 0, paint); //执行绘制操作
                surfaceHolder.unlockCanvasAndPost(canvas); // 解除锁定并显示在界面上
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        //方式三
        FrameLayout fly = findViewById(R.id.fly);
        CustomView customView = new CustomView(this, scaledBitmap);
        fly.addView(customView);
    }
}

