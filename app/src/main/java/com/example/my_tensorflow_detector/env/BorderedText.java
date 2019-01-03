package com.example.my_tensorflow_detector.env;

/**
 * Created by 不闻不问不听不看不在乎~ on 2018/11/18.
 */


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.util.Vector;


//封装冗余的边缘文本
public class BorderedText {
    private final Paint interiorPaint;
    private final Paint exteriorPaint;

    private final float textSize;

    //构造函数
    public BorderedText(final float textSize) {
        this(Color.WHITE, Color.BLACK, textSize);
    }

    //构造函数
    public BorderedText(final int interiorColor, final int exteriorColor, final float textSize) {
        interiorPaint = new Paint();// 创建内部的画笔
        interiorPaint.setTextSize(textSize);//设置字体尺寸
        interiorPaint.setColor(interiorColor);//设置画笔颜色
        interiorPaint.setStyle(Style.FILL);//设置画笔的风格为实心
        interiorPaint.setAntiAlias(false);//设置有锯齿
        interiorPaint.setAlpha(255);//设置通道数

        exteriorPaint = new Paint();
        exteriorPaint.setTextSize(textSize);
        exteriorPaint.setColor(exteriorColor);
        exteriorPaint.setStyle(Style.FILL_AND_STROKE);//填充内部跟描边
        exteriorPaint.setStrokeWidth(textSize / 8);//设置边的宽度
        exteriorPaint.setAntiAlias(false);
        exteriorPaint.setAlpha(255);

        this.textSize = textSize;
    }

    //设置字体样式
    public void setTypeface(Typeface typeface) {
        interiorPaint.setTypeface(typeface);
        exteriorPaint.setTypeface(typeface);
    }

    //绘制文本（要绘制的文本，绘制原点X的坐标， 绘制远点Y的坐标， 画笔）
    public void drawText(final Canvas canvas, final float posX, final float posY, final String text) {
        //X,Y坐标是左下角的坐标
        canvas.drawText(text, posX, posY, exteriorPaint);
        canvas.drawText(text, posX, posY, interiorPaint);
    }

    public void drawLines(Canvas canvas, final float posX, final float posY, Vector<String> lines) {
        int lineNum = 0;
        for (final String line : lines) {
            drawText(canvas, posX, posY - getTextSize() * (lines.size() - lineNum - 1), line);
            ++lineNum;
        }
    }

    public void setInteriorColor(final int color) {
        interiorPaint.setColor(color);
    }

    public void setExteriorColor(final int color) {
        exteriorPaint.setColor(color);
    }

    public float getTextSize() {
        return textSize;
    }

    public void setAlpha(final int alpha) {
        interiorPaint.setAlpha(alpha);
        exteriorPaint.setAlpha(alpha);
    }

    public void getTextBounds(
            final String line, final int index, final int count, final Rect lineBounds) {
        interiorPaint.getTextBounds(line, index, count, lineBounds);
    }

    public void setTextAlign(final Align align) {
        interiorPaint.setTextAlign(align);
        exteriorPaint.setTextAlign(align);
    }
}
