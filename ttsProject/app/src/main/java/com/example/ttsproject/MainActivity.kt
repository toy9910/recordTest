package com.example.ttsproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ttsproject.databinding.ActivityMainBinding
import com.google.mediapipe.solutioncore.CameraInput
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.hands.HandLandmark
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.math.acos
import kotlin.math.round
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val TAG = "MainActivity"
    private lateinit var binding : ActivityMainBinding
    private lateinit var tts : TextToSpeech
    private lateinit var hands : Hands
    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<HandsResult>

    private val classes = arrayListOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ",
        "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", "ㅏ", "ㅐ",
        "ㅑ", "ㅓ", "ㅔ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                Toast.makeText(this@MainActivity, "Permission Granted!",Toast.LENGTH_SHORT).show()
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Toast.makeText(this@MainActivity, "Permission Denied!",Toast.LENGTH_SHORT).show()
            }

        }

        TedPermission.with(this)
            .setPermissionListener(permissionListener)
            .setPermissions(Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_NETWORK_STATE)
            .check()


        tts = TextToSpeech(this, this)
        binding.btnPlay.setOnClickListener {
            speakOut()
        }
        binding.btnRecord.setOnClickListener {
            startSTT()
        }
        setupStreamingModePipeline()

    }

    override fun onInit(p0: Int) {
        if(p0 == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            speakOut()
        }
    }

    private fun startCamera() {
        cameraInput.start(
            this,
            hands.glContext,
            CameraInput.CameraFacing.FRONT,
            glSurfaceView.width,
            glSurfaceView.height
        )
    }

    override fun onResume() {
        super.onResume()
        cameraInput = CameraInput(this)
        cameraInput.setNewFrameListener { hands.send(it) }
        glSurfaceView.post { this.startCamera() }
        glSurfaceView.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()

//        glSurfaceView.visibility = View.GONE
//        cameraInput.close()
    }

    private fun setupStreamingModePipeline() {
        hands = Hands(
            this,
            HandsOptions.builder()
                .setStaticImageMode(false)
                .setMaxNumHands(1)
                .setRunOnGpu(true)
                .build()
        )
        hands.setErrorListener { message, e -> Log.e(TAG, "MediaPipe Hands error: $message") }

        cameraInput = CameraInput(this)
        cameraInput.setNewFrameListener { hands.send(it) }

        glSurfaceView = SolutionGlSurfaceView(this, hands.glContext, hands.glMajorVersion)
        glSurfaceView.setSolutionResultRenderer(HandsResultGlRenderer())
        glSurfaceView.setRenderInputImage(true)
        hands.setResultListener {
            logWristLandmark(it, false)
            glSurfaceView.setRenderData(it)
            glSurfaceView.requestRender()
        }

        glSurfaceView.post(this::startCamera)

        binding.previewDisplayLayout.apply {
            removeAllViewsInLayout()
            addView(glSurfaceView)
            glSurfaceView.visibility = View.VISIBLE
            requestLayout()
        }
    }

    private fun stopCurrentPipeline() {
        if(cameraInput != null) {
            cameraInput.setNewFrameListener(null)
            cameraInput.close()
        }
    }

    private fun speakOut() {
        val text = binding.etSentence.text as CharSequence
        tts.setPitch(1f)
        tts.setSpeechRate(1f)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "id1")
    }

    private  fun startSTT() {
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener())
            startListening(speechRecognizerIntent)
        }

    }
    /***
     *  SpeechToText 기능 세팅
     */
    private fun recognitionListener() = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) = Toast.makeText(this@MainActivity, "음성인식 시작", Toast.LENGTH_SHORT).show()

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when(error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Toast.makeText(this@MainActivity, "퍼미션 없음", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onResults(results: Bundle) {
            Toast.makeText(this@MainActivity, "음성인식 종료", Toast.LENGTH_SHORT).show()
            binding.tvResult.text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0]
        }
    }



    private fun logWristLandmark(result: HandsResult, showPixelValues: Boolean) {
        if (result.multiHandLandmarks().isEmpty()) {
            return
        }
        val wristLandmark = result.multiHandLandmarks()[0].landmarkList[HandLandmark.WRIST]
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            val width = result.inputBitmap().width
            val height = result.inputBitmap().height
            Log.i(
                TAG, String.format(
                    "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                    wristLandmark.x * width, wristLandmark.y * height
                )
            )
        } else {
//            Log.i(
//                TAG, String.format(
//                    "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
//                    wristLandmark.x, wristLandmark.y
//                )
//            )
            translate(result)
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return
        }
        val wristWorldLandmark =
            result.multiHandWorldLandmarks()[0].landmarkList[HandLandmark.WRIST]
        Log.i(
            TAG, String.format(
                "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                        + " approximate geometric center): x=%f , y=%f , z=%f ",
                wristWorldLandmark.x, wristWorldLandmark.y, wristWorldLandmark.z
            )
        )
    }

    private fun translate(result : HandsResult){
        if (result.multiHandLandmarks().isEmpty()) {
            return
        }
        val landmarkList = result.multiHandLandmarks()[0].landmarkList
        val joint = Array(21){FloatArray(3)}
        for(i in 0..19) {
            joint[i][0] = landmarkList[i].x
            joint[i][1] = landmarkList[i].y
            joint[i][2] = landmarkList[i].z
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
        //Log.d(TAG, "onCreate: $v")

        for(i in 0..19) {
            val norm = sqrt(v[i][0] * v[i][0] + v[i][1] * v[i][1] + v[i][2] * v[i][2])
            for(j in 0..2) {
                v[i][j] /= norm
            }
        }
        //Log.d(TAG, "onCreate: $v")

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

        val interpreter = getTfliteInterpreter("converted_model.tflite")
        val byteBuffer = ByteBuffer.allocateDirect(15*4).order(ByteOrder.nativeOrder())

        for(d in data) {
            byteBuffer.putFloat(d)
        }

        val modelOutput = ByteBuffer.allocateDirect(26*4).order(ByteOrder.nativeOrder())
        modelOutput.rewind()

        interpreter!!.run(byteBuffer,modelOutput)

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
        Log.d(TAG, "translate: ${classes[index]}")
        runOnUiThread {
            binding.tvResult.text = classes[index]
        }

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