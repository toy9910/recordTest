package com.example.rectest

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.acos
import kotlin.math.round
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {
    private val TAG = "ObjectDetectionHelper"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val joint = Array(21) { FloatArray(3) }
        for(i in 0..19) {
            for(j in 0..2) {
                joint[i][j] = (Random().nextInt(100-1) + 1).toFloat()
            }
        }


        val v1 = joint.slice(0..19).toMutableList()
        for(i in 4..16 step(4)) {
            v1[i] = v1[0]
        }
        var v2 = joint.slice(1..20)
        val v = Array(20) { FloatArray(3) }

        for(i in 0..19) {
            for(j in 0..2) {
                v[i][j] = v2[i][j] - v1[i][j]
            }
        }
        Log.d(TAG, "onCreate: $v")

        for(i in 0..19) {
            val norm = sqrt(v[i][0] * v[i][0] + v[i][1] * v[i][1] + v[i][2] * v[i][2])
            for(j in 0..2) {
                v[i][j] /= norm
            }
        }
        Log.d(TAG, "onCreate: $v")

        val tmpv1 = mutableListOf<FloatArray>()
        for(i in 0..18) {
            if(i != 3 && i != 7 && i != 11 && i != 15) {
                tmpv1.add(v[i])
            }
        }
        val tmpv2 = mutableListOf<FloatArray>()
        for(i in 1..19) {
            if(i != 4 && i != 8 && i != 12 && i != 16) {
                tmpv2.add(v[i])
            }
        }

        val einsum = FloatArray(15)
        for( i in 0..14) {
            einsum[i] = tmpv1[i][0] * tmpv2[i][0] + tmpv1[i][1] * tmpv2[i][1] + tmpv1[i][2] * tmpv2[i][2]
        }
        val angle = FloatArray(15)
        val data = FloatArray(15)
        for(i in 0..14) {
            angle[i] = Math.toDegrees(acos(einsum[i]).toDouble()).toFloat()
            data[i] = round(angle[i] * 100000) / 100000
        }



        // Interpreter와 Input 초기화
        val interpreter = getTfliteInterpreter("converted_model.tflite")
        val byteBuffer = ByteBuffer.allocateDirect(15*4).order(ByteOrder.nativeOrder())

        byteBuffer.putFloat(25.519785f)
        byteBuffer.putFloat(9.558166f)
        byteBuffer.putFloat(7.7847457f)
        byteBuffer.putFloat(87.54472f)
        byteBuffer.putFloat(83.6965f)
        byteBuffer.putFloat(31.182438f)
        byteBuffer.putFloat(105.54568f)
        byteBuffer.putFloat(76.19455f)
        byteBuffer.putFloat(29.814428f)
        byteBuffer.putFloat(112.78215f)
        byteBuffer.putFloat(76.71497f)
        byteBuffer.putFloat(29.114666f)
        byteBuffer.putFloat(117.87272f)
        byteBuffer.putFloat(63.79507f)
        byteBuffer.putFloat(42.364155f)

        // Output 초기화
        val modelOutput = ByteBuffer.allocateDirect(26*4).order(ByteOrder.nativeOrder())
        modelOutput.rewind()

        interpreter!!.run(byteBuffer,modelOutput)

        // 이 코드는 왜 있어야 하는지 모르겠음. 없으면 에러남
        val outputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1,26), DataType.FLOAT32)
        outputFeature0.loadBuffer(modelOutput)

        // ByteBuffer to FloatBuffer
        val outputsFloatBuffer = modelOutput.asFloatBuffer()
        val outputs = mutableListOf<Float>()
        for(i in 1..26) {
            outputs.add(outputsFloatBuffer.get())
        }
        Log.d(TAG, "outputs : $outputs")
        val sortedOutput = outputs.sortedDescending()
        val index = outputs.indexOf(sortedOutput[0])
    }

    private fun getTfliteInterpreter(path: String): Interpreter? {
        try {
            return Interpreter(loadModelFile(this, path)!!)
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun loadModelFile(activity: Activity, path: String): MappedByteBuffer? {
        val fileDescriptor = activity.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}