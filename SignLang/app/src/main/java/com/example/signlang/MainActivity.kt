package com.example.signlang

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.signlang.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener


class MainActivity : AppCompatActivity(), HBRecorderListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var recorder: HBRecorder
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == RESULT_OK) {
            //Start screen recording
            recorder.startScreenRecording(result.data, result.resultCode)
        }
    }
    val SCREEN_RECORD_REQUEST_CODE = 1000


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TedPermission.create().setPermissionListener(object : PermissionListener {
            override fun onPermissionGranted() {

            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {

            }

        }).setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.RECORD_AUDIO).check()


        recorder = HBRecorder(this, this)
        recorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path)
        val st = recorder.filePath
        Log.d("TAG", "onCreate: ${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path}")

        binding.toggle.setOnClickListener {
            if(binding.toggle.isChecked) {
                Log.d("TAG", "onCreate: true")
                startRecordingScreen()
            }
            else {
                Log.d("TAG", "onCreate: false")
                recorder.stopScreenRecording()
            }
        }
    }

    override fun HBRecorderOnStart() {
    }

    override fun HBRecorderOnComplete() {
    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
    }

    private fun startRecordingScreen() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager?.createScreenCaptureIntent()
        resultLauncher.launch(permissionIntent)
    }
}