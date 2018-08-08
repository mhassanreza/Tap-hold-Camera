package com.taphold.camera;

import android.app.FragmentTransaction;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import com.otaliastudios.cameraview.CameraLogger;

public class CameraActivity extends AppCompatActivity implements CameraConfirmationFragment.ConfirmationContract, CameraPreviewFragment.PreviewContract {


    private Context mContext;
    private static final String TAG_CAMERA = "CameraFragment";
    private static final String TAG_CONFIRM = "ConfirmationFragment";
    private CameraPreviewFragment cameraFrag;
    private CameraConfirmationFragment confirmFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE);
        mContext = this;
        initFragments();
    }

    private void initFragments() {
        cameraFrag = new CameraPreviewFragment();
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.add(android.R.id.content, cameraFrag, TAG_CAMERA);
        fragmentTransaction.commit();
        confirmFrag = CameraConfirmationFragment.newInstance();
        getFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, confirmFrag, TAG_CONFIRM)
                .commit();

        if (!cameraFrag.isVisible() && !confirmFrag.isVisible()) {
            getFragmentManager()
                    .beginTransaction()
                    .hide(confirmFrag)
                    .show(cameraFrag)
                    .commit();
        }
    }

    @Override
    public void openCamera() {
        if (cameraFrag != null) {
            cameraFrag.restartCamera();
            getFragmentManager()
                    .beginTransaction()
                    .hide(confirmFrag)
                    .show(cameraFrag)
                    .commit();
        }
    }

    @Override
    public void ImageTaken(byte[] imageBase64) {
        if (confirmFrag != null) {
            confirmFrag.setImage(imageBase64);
            getFragmentManager()
                    .beginTransaction()
                    .hide(cameraFrag)
                    .show(confirmFrag)
                    .commit();
        }
    }

    @Override
    public void VideoTaken(Uri uri) {
        if (confirmFrag != null) {
            confirmFrag.setVideo(uri);
            getFragmentManager()
                    .beginTransaction()
                    .hide(cameraFrag)
                    .show(confirmFrag)
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            getFragmentManager().popBackStack();
        }
    }
}
