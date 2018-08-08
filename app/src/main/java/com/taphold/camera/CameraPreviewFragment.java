package com.taphold.camera;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.SessionType;
import com.taphold.camera.camerabutton.BitmapHelper;
import com.taphold.camera.camerabutton.CameraButton;

import java.io.File;

/**
 * Created by hassanraza on 3/20/2017.
 */

public class CameraPreviewFragment extends Fragment implements CameraButton.OnPhotoEventListener, CameraButton.OnVideoEventListener {
    private static final String TAG = "Camera2Api";
    private Context mContext;
    private View view;

    private CameraButton mCameraButton;
    private CameraView mCameraView;
    private boolean mCapturingPicture;
    private boolean mCapturingVideo;
    public static final int DELAY_TIME = 200;

    public interface PreviewContract {
        void ImageTaken(byte[] imageBase64);

        void VideoTaken(Uri imageBase64);
    }

    private PreviewContract getContract() {
        return ((PreviewContract) getActivity());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.mContext = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_latest_camera_layout, container, false);

        return (view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCameraView = view.findViewById(R.id.camera);
        mCameraView.addCameraListener(new CameraListener() {
            public void onCameraOpened(CameraOptions options) {
            }

            public void onPictureTaken(byte[] jpeg) {
                onPicture(jpeg);
            }

            @Override
            public void onVideoTaken(File video) {
                super.onVideoTaken(video);
                onVideo(video);
            }
        });

        mCameraButton = view.findViewById(R.id.custom_camera_picture);
        mCameraButton.setIcons(new Bitmap[]{
                BitmapHelper.getBitmap(mContext, R.drawable.ic_transparent_24dp),
                BitmapHelper.getBitmap(mContext, R.drawable.ic_stop_black_28dp)
        });
        mCameraButton.setOnPhotoEventListener(this);
        mCameraButton.setOnVideoEventListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraView.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        mCameraView.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCameraView.destroy();
    }

    public void restartCamera() {
        mCameraView.start();
        mCameraView.setSessionType(SessionType.PICTURE);
    }

    private void onPicture(byte[] jpeg) {
        mCapturingPicture = false;
        mCapturingVideo = false;
        getContract().ImageTaken(jpeg);
        mCameraView.stop();
    }

    private void onVideo(File video) {
        mCapturingPicture = false;
        mCapturingVideo = false;
        getContract().VideoTaken(Uri.fromFile(video));
        mCameraView.stop();
    }

    private void message(String content, boolean important) {
        int length = important ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
        Toast.makeText(mContext, content, length).show();
    }

    //
    @Override
    public void onCameraClick() {
        capturePhoto();
    }

    @Override
    public void onVideoLongClick() {
        mCapturingVideo = true;
        mCameraView.stop();
        mCameraView.setSessionType(SessionType.VIDEO);
        mCameraView.start();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCapturingVideo = false;
            }
        }, (DELAY_TIME - 10));
    }

    @Override
    public void onVideoStart() {
        mCameraButton.setIconsPosition(1);
        captureVideo();
    }

    @Override
    public void onVideoFinish() {
        mCameraButton.setIconsPosition(0);
        if (mCameraView.isCapturingVideo()) {
            mCameraView.stopCapturingVideo();
        }
    }

    @Override
    public void onVideoCancel() {
    }

    private void capturePhoto() {
        if (mCapturingPicture || mCapturingVideo) return;
        mCapturingPicture = true;
        mCameraView.capturePicture();
    }

    private void captureVideo() {
        if (mCapturingPicture || mCapturingVideo) {
            mCameraButton.setIconsPosition(0);
            return;
        }
        if (mCameraView.getSessionType() != SessionType.VIDEO) {
            message("Can't record video while session type is 'picture'.", false);
            return;
        }

        mCapturingVideo = true;
        mCameraView.startCapturingVideo(null, 17000);
    }
}
