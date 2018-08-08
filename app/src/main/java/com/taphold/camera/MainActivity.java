package com.taphold.camera;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

import static pub.devrel.easypermissions.AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
	private static final int EP_ALL_PERM = 22021;
	private Context mContext;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mContext = this;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ( requestCode == DEFAULT_SETTINGS_REQ_CODE ) {
			String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
			if ( EasyPermissions.hasPermissions(this, perms) ) {
				startActivity(new Intent(mContext, CameraActivity.class));
			}
			else {
				Toast.makeText(mContext, "" + mContext.getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}
	
	@AfterPermissionGranted( EP_ALL_PERM )
	public void askForCameraPermission() {
		String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
		if ( EasyPermissions.hasPermissions(this, perms) ) {
			startActivity(new Intent(MainActivity.this, CameraActivity.class));
		}
		else {
			EasyPermissions.requestPermissions(R.style.AppCompatProgressDialogStyle, this, getString(R.string.permission_rationale),
					EP_ALL_PERM, perms);
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
	}
	
	@Override
	public void onPermissionsGranted(int requestCode, List< String > perms) {
	}
	
	@Override
	public void onPermissionsDenied(int requestCode, List< String > perms) {
		if ( EasyPermissions.somePermissionPermanentlyDenied(this, perms) ) {
			new AppSettingsDialog.Builder(this, getString(R.string.permission_rationale), R.style.AppCompatProgressDialogStyle)
					.setPositiveButton(getString(R.string.open_permissions))
					.setNegativeButton(getString(R.string.btn_cancel), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(mContext, "" + getApplicationContext().getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
							finish();
						}
					}).setRequestCode(DEFAULT_SETTINGS_REQ_CODE)
					.build()
					.show();
		}
		else {
			Toast.makeText(mContext, "" + getApplicationContext().getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	public void OnOpenCameraButtonClicked(View v) {
		askForCameraPermission();
	}
}
