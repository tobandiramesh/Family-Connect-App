package com.familyconnect.app.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * WebRTC manager supporting both audio-only and video calls.
 */
class WebRTCManager(private val context: Context) {
    private val TAG = "WebRTCManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Video fields
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set
    var remoteVideoTrack: VideoTrack? = null
        private set
    private var isVideoEnabled = false
    private var usingFrontCamera = true

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onLocalOfferCreated: ((String) -> Unit)? = null
    var onLocalAnswerCreated: ((String) -> Unit)? = null
    var onIceCandidateGenerated: ((candidate: String, sdpMLineIndex: Int, sdpMid: String) -> Unit)? = null
    var onRemoteVideoTrackReceived: ((VideoTrack) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Keep references so we can remove sinks on cleanup
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase.eglBaseContext, true, true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "WebRTC factory initialized with video support")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC factory: ${e.message}")
        }
    }

    fun initializePeerConnection(withVideo: Boolean = false) {
        disposePeerConnectionOnly()
        isVideoEnabled = withVideo

        val factory = peerConnectionFactory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        )

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> onConnectionStateChanged?.invoke(true)
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> onConnectionStateChanged?.invoke(false)
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                onIceCandidateGenerated?.invoke(
                    candidate.sdp,
                    candidate.sdpMLineIndex,
                    candidate.sdpMid.orEmpty()
                )
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}

            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    remoteAudioTrack = track
                    remoteAudioTrack?.setEnabled(true)
                } else if (track is VideoTrack) {
                    Log.d(TAG, "Remote VIDEO track received via onAddTrack")
                    remoteVideoTrack = track
                    remoteVideoTrack?.setEnabled(true)
                    // Attach to renderer if already initialized, and notify on main thread
                    mainHandler.post {
                        remoteRenderer?.let { track.addSink(it) }
                        onRemoteVideoTrackReceived?.invoke(track)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    remoteAudioTrack = track
                    remoteAudioTrack?.setEnabled(true)
                } else if (track is VideoTrack) {
                    Log.d(TAG, "Remote VIDEO track received via onTrack")
                    remoteVideoTrack = track
                    remoteVideoTrack?.setEnabled(true)
                    mainHandler.post {
                        remoteRenderer?.let { track.addSink(it) }
                        onRemoteVideoTrackReceived?.invoke(track)
                    }
                }
            }
        })

        // Always add audio track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("LOCAL_AUDIO", localAudioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack)

        // Add video track if this is a video call
        if (withVideo) {
            startLocalVideo(factory)
        }
    }

    private fun startLocalVideo(factory: PeerConnectionFactory) {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val backCamera = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            val cameraName = frontCamera ?: backCamera ?: return
            usingFrontCamera = cameraName == frontCamera

            videoCapturer = enumerator.createCapturer(cameraName, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            localVideoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
            videoCapturer!!.startCapture(640, 480, 30)

            localVideoTrack = factory.createVideoTrack("LOCAL_VIDEO", localVideoSource)
            localVideoTrack?.setEnabled(true)
            peerConnection?.addTrack(localVideoTrack)
            Log.d(TAG, "Local video started with camera: $cameraName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local video: ${e.message}")
        }
    }

    fun initLocalRenderer(renderer: SurfaceViewRenderer) {
        try {
            // Release previous if any
            releaseLocalRenderer()
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setMirror(usingFrontCamera)
            renderer.setZOrderMediaOverlay(true)
            localRenderer = renderer
            localVideoTrack?.addSink(renderer)
            Log.d(TAG, "Local renderer initialized, track=${localVideoTrack != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing local renderer: ${e.message}")
        }
    }

    fun initRemoteRenderer(renderer: SurfaceViewRenderer) {
        try {
            releaseRemoteRenderer()
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setMirror(false)
            remoteRenderer = renderer
            remoteVideoTrack?.addSink(renderer)
            Log.d(TAG, "Remote renderer initialized, track=${remoteVideoTrack != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing remote renderer: ${e.message}")
        }
    }

    fun releaseLocalRenderer() {
        try {
            localRenderer?.let { r ->
                localVideoTrack?.removeSink(r)
                r.release()
            }
            localRenderer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing local renderer: ${e.message}")
        }
    }

    fun releaseRemoteRenderer() {
        try {
            remoteRenderer?.let { r ->
                remoteVideoTrack?.removeSink(r)
                r.release()
            }
            remoteRenderer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing remote renderer: ${e.message}")
        }
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontFacing: Boolean) {
                usingFrontCamera = isFrontFacing
                Log.d(TAG, "Camera switched, front=$isFrontFacing")
            }
            override fun onCameraSwitchError(error: String?) {
                Log.e(TAG, "Camera switch error: $error")
            }
        })
    }

    fun toggleLocalVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun createOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(SimpleSdpObserver("setLocal(offer)"), desc)
                onLocalOfferCreated?.invoke(desc.description)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setLocal offer failed: $error")
            }
        }, constraints)
    }

    fun handleRemoteOffer(offerSdp: String) {
        val pc = peerConnection ?: return
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(SimpleSdpObserver("setRemote(offer)"), offer)
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(SimpleSdpObserver("setLocal(answer)"), desc)
                onLocalAnswerCreated?.invoke(desc.description)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setLocal answer failed: $error")
            }
        }, MediaConstraints())
    }

    fun handleRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(SimpleSdpObserver("setRemote(answer)"), answer)
    }

    fun addRemoteIceCandidate(candidate: String, sdpMLineIndex: Int, sdpMid: String) {
        val pc = peerConnection ?: return
        if (candidate.isBlank() || sdpMLineIndex < 0) return
        pc.addIceCandidate(IceCandidate(sdpMid.ifBlank { null }, sdpMLineIndex, candidate))
    }

    fun startAudioCall() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = true
        remoteAudioTrack?.setEnabled(true)
    }

    fun stopCall() {
        onConnectionStateChanged?.invoke(false)
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.message}")
        }
        videoCapturer?.dispose()
        videoCapturer = null
        releaseLocalRenderer()
        releaseRemoteRenderer()
        disposePeerConnectionOnly()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false
        isVideoEnabled = false
    }

    // Keep backward compat alias
    fun stopAudioCall() = stopCall()

    fun toggleLocalAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        audioManager.isMicrophoneMute = !enabled
    }

    fun toggleRemoteAudio(enabled: Boolean) {
        remoteAudioTrack?.setEnabled(true)
        audioManager.isSpeakerphoneOn = enabled
    }

    private fun disposePeerConnectionOnly() {
        try {
            remoteVideoTrack = null
            localVideoTrack?.setEnabled(false)
            localVideoTrack = null
            localVideoSource?.dispose()
            localVideoSource = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            remoteAudioTrack = null
            localAudioTrack?.setEnabled(false)
            localAudioTrack = null
            localAudioSource?.dispose()
            localAudioSource = null
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing peer connection: ${e.message}")
        }
    }

    fun dispose() {
        stopCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
    }

    private class SimpleSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {
            Log.d("WebRTCManager", "$tag success")
        }

        override fun onCreateFailure(error: String?) {
            Log.e("WebRTCManager", "$tag create failure: $error")
        }

        override fun onSetFailure(error: String?) {
            Log.e("WebRTCManager", "$tag set failure: $error")
        }
    }
}

