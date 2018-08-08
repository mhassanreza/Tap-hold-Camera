package com.taphold.camera;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import com.otaliastudios.cameraview.AspectRatio;
import com.otaliastudios.cameraview.CameraUtils;

/**
 * The type Latest confirmation fragment.
 */
public class CameraConfirmationFragment extends Fragment implements View.OnClickListener {
    private ImageButton mIbDone;
    private ImageView mIVMainImage;
    private VideoView mVideoView;

    public interface ConfirmationContract {
        void openCamera();
    }

    private ConfirmationContract getContract() {
        return ((ConfirmationContract) getActivity());
    }

    public static CameraConfirmationFragment newInstance() {
        CameraConfirmationFragment result = new CameraConfirmationFragment();
        Bundle args = new Bundle();
        result.setArguments(args);
        return (result);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }


    @Override
    public void onAttach(Activity activity) {
        if (!(activity instanceof ConfirmationContract)) {
            throw new IllegalStateException("Hosting activity must implement ConfirmationContract interface");
        }
        super.onAttach(activity);
    }

    /**
     * Initializing all view elements
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_custom_confirmation_layout, container, false);

        mIVMainImage = v.findViewById(R.id.iv_main_image);
        mIbDone = v.findViewById(R.id.ib_done);
        mVideoView = v.findViewById(R.id.vv_video);
        mVideoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playVideo();
            }
        });
        setListeners();
        return (v);
    }

    void playVideo() {
        if (mVideoView.isPlaying()) return;
        mVideoView.start();
    }

    private void setListeners() {
        mIbDone.setOnClickListener(this);
    }

    public void setImage(byte[] imageBase64) {
        mIVMainImage.setVisibility(View.VISIBLE);
        mVideoView.setVisibility(View.GONE);
        if (mIVMainImage != null) {
            CameraUtils.decodeBitmap(imageBase64, 1000, 1000, new CameraUtils.BitmapCallback() {
                @Override
                public void onBitmapReady(Bitmap bitmap) {
                    mIVMainImage.setImageBitmap(bitmap);
                }
            });
        }
    }

    public void setVideo(Uri videoUri) {
        mIVMainImage.setVisibility(View.GONE);
        mVideoView.setVisibility(View.VISIBLE);
        MediaController controller = new MediaController(getActivity());
        if (mVideoView != null) {
            controller.setAnchorView(mVideoView);
            controller.setMediaPlayer(mVideoView);
            mVideoView.setMediaController(controller);
            mVideoView.setVideoURI(videoUri);
            mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
                    float videoWidth = mp.getVideoWidth();
                    float videoHeight = mp.getVideoHeight();
                    float viewWidth = mVideoView.getWidth();
                    lp.height = (int) (viewWidth * (videoHeight / videoWidth));
                    mVideoView.setLayoutParams(lp);
                    playVideo();
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoView.isPlaying()) {
            mVideoView.stopPlayback();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_done:
                getContract().openCamera();
                if (mVideoView.isPlaying()) {
                    mVideoView.stopPlayback();
                }
                break;
        }
    }
}
