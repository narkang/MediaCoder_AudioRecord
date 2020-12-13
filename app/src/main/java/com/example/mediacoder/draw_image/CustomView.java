package com.example.mediacoder.draw_image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class CustomView extends View {

    Paint paint = new Paint();
    Bitmap bitmap;

    public CustomView(Context context, Bitmap bitmap) {
        super(context);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        this.bitmap = bitmap;  // 获取bitmap
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 不建议在onDraw做任何分配内存的操作
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
    }
}
