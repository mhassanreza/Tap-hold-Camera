/*
 * Copyright (C) 2018 Artem Hluhovskyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taphold.camera.camerabutton;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;

/**
 * Used only for retrieving vector drawables from resources
 * Not included to library due to dependency on support-v4
 */
public class BitmapHelper {
	
	public static Bitmap getBitmap(Context context, @DrawableRes int drawableId) {
		Drawable drawable = ContextCompat.getDrawable(context, drawableId);
		return getBitmap(drawable);
	}
	
	@SuppressLint( "ObsoleteSdkInt" )
	public static Bitmap getBitmap(Drawable drawable) {
		if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
			drawable = (DrawableCompat.wrap(drawable)).mutate();
		}
		
		Bitmap bitmap = Bitmap.createBitmap(
				drawable.getIntrinsicWidth(),
				drawable.getIntrinsicHeight(),
				Bitmap.Config.ARGB_8888);
		
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);
		
		return bitmap;
	}
}
