package com.localserver

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.arthenica.ffmpegkit.FFmpegKit
import com.google.common.util.concurrent.ListenableFuture
import com.localserver.databinding.ActivityMainBinding
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.apache.commons.io.FileUtils
import java.io.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), ImageAnalysis.Analyzer, View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private var serverUp = false
    private var message = "First message"
    private var ffmpegSessionId: Long = 0
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setListeners()
        //setCamera()
    }

    private fun setListeners() {
        binding.serverButton.setOnClickListener(this)
        binding.sendMessgae.setOnClickListener(this)
        binding.pickFile.setOnClickListener(this)
        binding.stopFfmpeg.setOnClickListener(this)
        binding.streamCamera.setOnClickListener(this)
    }

    private fun setCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            imageAnalysis.setAnalyzer(mainExecutor, this)
        }

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )

    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {

        /*val yBuffer = image.image?.planes?.get(0)?.buffer // Y
        val vuBuffer = image.image?.planes?.get(2)?.buffer // VU

        val ySize = yBuffer?.remaining()
        val vuSize = vuBuffer?.remaining()

        val nv21 = ByteArray(ySize!! + vuSize!!)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()*/

        //writeYuvFile(image.image!!)
        image.close()
    }

    private fun writeYuvFile(mImage: Image) {
        /*val tempFile: File
        val cDir: File = baseContext.cacheDir
        tempFile = File(cDir.path.toString() + "/" + "textFile.yuv")

        val writer: FileWriter?
        try {
            writer = FileWriter(tempFile)
            writer.write("hello workd!")
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }*/

        val data = NV21toJPEG(YUV_420_888toNV21(mImage), mImage.width, mImage.height)
        FileUtils.writeByteArrayToFile(File(baseContext.cacheDir, "frame.yuv"), data)

        /*val file = File(baseContext.cacheDir, "frame.yuv")
        val output: FileOutputStream?
        var buffer: ByteBuffer?
        var bytes: ByteArray
        var success = false

        val prebuffer: ByteBuffer = ByteBuffer.allocate(16)
        prebuffer.putInt(mImage.getWidth())
            .putInt(mImage.getHeight())
            .putInt(mImage.getPlanes().get(1).getPixelStride())
            .putInt(mImage.getPlanes().get(1).getRowStride())

        try {
            output = FileOutputStream(file)
            output.write(prebuffer.array()) // write meta information to file
            // Now write the actual planes.
            for (i in 0..2) {
                buffer = mImage.getPlanes().get(i).getBuffer()
                bytes = ByteArray(buffer.remaining()) // makes byte array large enough to hold image
                buffer.get(bytes) // copies image from buffer to byte array
                output.write(bytes) // write the byte array to file
            }
            success = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            mImage.close()
        }*/
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val nv21: ByteArray
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        return nv21
    }


    private fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return out.toByteArray()
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.serverButton -> {
                serverUp = if (!serverUp) {
                    startServer(5000)
                    true
                } else {
                    stopServer()
                    false
                }
            }
            R.id.send_messgae -> {
                message = binding.message.text.toString()
                if (serverUp) {
                    mHttpServer?.createContext("/", rootHandler)
                    binding.message.text = null
                    Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.pick_file -> {
                pickVideo()
            }
            R.id.stop_ffmpeg -> {
                FFmpegKit.cancel(ffmpegSessionId)
            }
            R.id.stream_camera -> {
                streamAndroidCamera()
            }
        }
    }

    private fun pickVideo() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_PICK
        startActivityForResult(Intent.createChooser(intent, "Select Video"), 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 100) {
            if (data?.data != null) {
                val uriPathHelper = URIPathHelper()
                val videoFullPath = uriPathHelper.getPath(
                    this,
                    data.data!!
                ) // Use this video path according to your logic
                streamVideoOnUdp(videoFullPath)
            }
        }
    }

    private fun sendResponse(httpExchange: HttpExchange, responseText: String) {
        httpExchange.sendResponseHeaders(200, responseText.length.toLong())
        val os = httpExchange.responseBody
        os.write(responseText.toByteArray())
        os.close()
    }

    private var mHttpServer: HttpServer? = null

    private fun startServer(port: Int) {
        try {
            mHttpServer = HttpServer.create(InetSocketAddress(port), 0)
            mHttpServer!!.executor = Executors.newCachedThreadPool()
            mHttpServer!!.createContext("/", rootHandler)
            mHttpServer!!.start()
            binding.serverTextView.text = getString(R.string.server_running)
            binding.serverButton.text = getString(R.string.stop_server)
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun stopServer() {
        if (mHttpServer != null) {
            mHttpServer!!.stop(0)
            binding.serverTextView.text = getString(R.string.server_down)
            binding.serverButton.text = getString(R.string.start_server)
        }
    }

    private val rootHandler = HttpHandler { exchange ->
        run {
            // Get request method
            when (exchange!!.requestMethod) {
                "GET" -> {
                    sendResponse(exchange, message)
                }
            }
        }
    }

    private fun streamVideoOnUdp(videoPath: String?) {
        FFmpegKit.executeAsync("-re -i $videoPath -vcodec copy -acodec copy -f mpegts udp://@localhost:8089?pkt_size=1319",
            { session ->
                ffmpegSessionId = session.sessionId
                val state = session.state
                val returnCode = session.returnCode
                // CALLED WHEN SESSION IS EXECUTED
                Log.d(
                    "thisisdata",
                    String.format(
                        "FFmpeg process exited with state %s and rc %s.%s",
                        state,
                        returnCode,
                        session.failStackTrace
                    )
                )
            }, {
                // CALLED WHEN SESSION PRINTS LOGS
                Log.d("thisisdata", "logs: ${it.message}")
            }) {
            // CALLED WHEN SESSION GENERATES STATISTICS
            Log.d("thisisdata", "stats: $it")
        }
    }

    private fun streamAndroidCamera() {
        FFmpegKit.executeAsync("-loglevel debug -f android_camera -camera_index 0 -fpsprobesize 0 -framerate 15 -probesize 32 -s 640x480 -rtbufsize 1M -i input -g 30 -b:v 980k -r 25 -b:a 28k -ar 16000 -f mpegts udp://@localhost:8089?pkt_size=1319 -hide_banner",
            { session ->
                ffmpegSessionId = session.sessionId
                val state = session.state
                val returnCode = session.returnCode
                // CALLED WHEN SESSION IS EXECUTED
                Log.d(
                    "thisisdata",
                    String.format(
                        "FFmpeg process exited with state %s and rc %s.%s",
                        state,
                        returnCode,
                        session.failStackTrace
                    )
                )
            }, {
                // CALLED WHEN SESSION PRINTS LOGS
                Log.d("thisisdata", "logs: ${it.message}")
            }) {
            // CALLED WHEN SESSION GENERATES STATISTICS
            Log.d("thisisdata", "stats: $it")
        }
    }

}