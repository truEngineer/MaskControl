package app.mask.control

import android.content.ContentUris
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.esafirm.imagepicker.model.Image
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.face.FaceDetector
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class MainViewModel : ViewModel() {

    companion object {
        private const val MODEL_FILE = "model.tflite"
        private const val LABELS = "labels.txt"
        private const val LABEL_MASK = "mask"
        private const val LABEL_NO_MASK = "no_mask"

        private fun formatString(text: String, float: Float): String =
            "$text: ${String.format("%.1f", float * 100)}%"
    }

    val drawableLiveData = MutableLiveData<Drawable>()

    fun onActivityResult(context: Context, image: Image) = context.onResult(image)

    private fun Context.onResult(image: Image) {
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            image.id
        )

        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }

        bitmap = Bitmap. createScaledBitmap(
            bitmap,
            500,
            (bitmap.height / (bitmap.width / 500F)).toInt(),
            true
        )

        drawableLiveData.value = bitmap.toDrawable(resources)

        val inputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Paints
        val myRectPaint = Paint()
        myRectPaint.strokeWidth = 4F
        myRectPaint.style = Paint.Style.STROKE
        val myTextPaint = Paint()
        myRectPaint.strokeWidth = 2F
        myTextPaint.style = Paint.Style.FILL_AND_STROKE

        // Canvas
        val tempBitmap = createBitmap(
            inputBitmap.width,
            inputBitmap.height,
            Bitmap.Config.RGB_565
        )
        val tempCanvas = Canvas(tempBitmap)
        tempCanvas.drawBitmap(inputBitmap, 0F, 0F, null)

        // FaceDetector
        val faceDetector = FaceDetector.Builder(applicationContext)
            .setTrackingEnabled(false)
            .build()

        // Detect the faces
        val frame = Frame.Builder().setBitmap(inputBitmap).build()
        val faces = faceDetector.detect(frame)

        // Mark out the identified face
        for (i in 0 until faces.size()) {
            val thisFace = faces.valueAt(i)
            val left = thisFace.position.x
            val top = thisFace.position.y
            val right = left + thisFace.width
            val bottom = top + thisFace.height
            val bitmapCropped = Bitmap.createBitmap(
                inputBitmap,
                left.toInt(),
                top.toInt(),
                if (right.toInt() > inputBitmap.width) {
                    inputBitmap.width - left.toInt()
                } else {
                    thisFace.width.toInt()
                },
                if (bottom.toInt() > inputBitmap.height) {
                    inputBitmap.height - top.toInt()
                } else {
                    thisFace.height.toInt()
                }
            )
            val label = predict(bitmapCropped)
            val with = label[LABEL_MASK] ?: 0F
            val without = label[LABEL_NO_MASK] ?: 0F

            val prediction = if (with > without) {
                myRectPaint.color = Color.GREEN
                myTextPaint.color = Color.GREEN
                Toast.makeText(this, "opened", Toast.LENGTH_SHORT).show()
                formatString(LABEL_MASK, with)
            } else {
                myRectPaint.color = Color.RED
                myTextPaint.color = Color.RED
                Toast.makeText(this, "closed", Toast.LENGTH_SHORT).show()
                formatString(LABEL_NO_MASK, without)
            }
            myTextPaint.textSize = thisFace.width / 8
            myTextPaint.textAlign = Paint.Align.LEFT
            tempCanvas.drawText(prediction, left, top - 8F, myTextPaint)
            tempCanvas.drawRoundRect(RectF(left, top, right, bottom), 2F, 2F, myRectPaint)
        }

        drawableLiveData.value = BitmapDrawable(resources, tempBitmap)
        // Release the FaceDetector
        faceDetector.release()
    }

    private fun Context.predict(input: Bitmap): MutableMap<String, Float> {

        val modelFile = FileUtil.loadMappedFile(this, MODEL_FILE)
        val model = Interpreter(modelFile, Interpreter.Options())
        val labels = FileUtil.loadLabels(this, LABELS)

        val imageDataType = model.getInputTensor(0).dataType()
        val inputShape = model.getInputTensor(0).shape()

        val outputDataType = model.getOutputTensor(0).dataType()
        val outputShape = model.getOutputTensor(0).shape()

        var inputImageBuffer = TensorImage(imageDataType)
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType)

        val cropSize = kotlin.math.min(input.width, input.height)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(inputShape[1], inputShape[2], ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(127.5f, 127.5f))
            .build()

        inputImageBuffer.load(input)
        inputImageBuffer = imageProcessor.process(inputImageBuffer)

        model.run(inputImageBuffer.buffer, outputBuffer.buffer.rewind())

        val labelOutput = TensorLabel(labels, outputBuffer)

        return labelOutput.mapWithFloatValue
    }
}
