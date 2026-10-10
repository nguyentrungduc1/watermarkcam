package com.nguyentrungduc1.watermarkcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

final class SkinSegmentation {
    static Bitmap confidence(Context context, Bitmap source) {
        ImageSegmenter.ImageSegmenterOptions options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath("models/selfie_multiclass_256x256.tflite").build())
                .setOutputCategoryMask(false).setOutputConfidenceMasks(true).build();
        // MPImage owns and recycles its bitmap when closed; the retoucher still needs source.
        MPImage input = new BitmapImageBuilder(source.copy(Bitmap.Config.ARGB_8888, false)).build();
        try (ImageSegmenter segmenter = ImageSegmenter.createFromOptions(context, options)) {
            ImageSegmenterResult result = segmenter.segment(input);
            List<MPImage> masks = result.confidenceMasks().get();
            try {
                MPImage face = masks.get(3), body = masks.get(2);
                FloatBuffer faceValues = ByteBufferExtractor.extract(face).order(ByteOrder.nativeOrder()).asFloatBuffer();
                FloatBuffer bodyValues = ByteBufferExtractor.extract(body).order(ByteOrder.nativeOrder()).asFloatBuffer();
                int[] pixels = new int[face.getWidth() * face.getHeight()];
                for (int i = 0; i < pixels.length; i++) {
                    // Include adjacent neck skin; never threshold the mask into polygon-like regions.
                    float probability = Math.max(0, Math.min(1, faceValues.get(i) + bodyValues.get(i)));
                    int value = Math.round(255 * probability);
                    pixels[i] = Color.rgb(value, value, value);
                }
                Bitmap mask = Bitmap.createBitmap(pixels, face.getWidth(), face.getHeight(), Bitmap.Config.ARGB_8888);
                Bitmap sized = Bitmap.createScaledBitmap(mask, source.getWidth(), source.getHeight(), true);
                if (sized != mask) mask.recycle();
                return sized;
            } finally {
                for (MPImage mask : masks) mask.close();
            }
        } finally {
            input.close();
        }
    }
}
