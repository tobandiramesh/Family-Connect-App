package com.familyconnect.app.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Real WebRTC audio manager using offer/answer SDP and ICE candidates.
 */
class WebRTCManager(private val context: Context) {
    private val TAG = "WebRTCManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onLocalOfferCreated: ((String) -> Unit)? = null
    var onLocalAnswerCreated: ((String) -> Unit)? = null
    var onIceCandidateGenerated: ((candidate: String, sdpMLineIndex: Int, sdpMid: String) -> Unit)? = null

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "WebRTC factory initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC factory: ${e.message}")
        }
    }

    fun initializePeerConnection() {
        disposePeerConnectionOnly()

        val factory = peerConnectionFactory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                // Public relay for testing so calls can connect across strict NATs/mobile carriers.
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
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    remoteAudioTrack = track
                    remoteAudioTrack?.setEnabled(true)
                }
            }
        })

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
        // Keep speaker on by default for better audibility during testing.
        audioManager.isSpeakerphoneOn = true
        remoteAudioTrack?.setEnabled(true)
    }

    fun stopAudioCall() {
        onConnectionStateChanged?.invoke(false)
        disposePeerConnectionOnly()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false
    }

    fun toggleLocalAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        audioManager.isMicrophoneMute = !enabled
    }

    fun toggleRemoteAudio(enabled: Boolean) {
        // Speaker button should only change audio output route.
        // Do not mute remote track here, otherwise users may think call has no audio.
        remoteAudioTrack?.setEnabled(true)
        audioManager.isSpeakerphoneOn = enabled
    }

    private fun disposePeerConnectionOnly() {
        try {
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
        stopAudioCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
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

