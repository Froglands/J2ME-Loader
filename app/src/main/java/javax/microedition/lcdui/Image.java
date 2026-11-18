/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.shell.AppClassLoader;

import ru.playsoftware.j2meloader.util.PNGUtils;

public class Image {
	private Bitmap bitmap;
	private Graphics graphics;
	/** Ukuran logis gambar (yang dilihat game). */
	private final Rect bounds;
	/** Ukuran piksel bitmap sebenarnya = bounds * scale. */
	private final Rect bitmapBounds;
	/** Kelipatan resolusi bitmap terhadap ukuran logis (1 = normal, 4 = HD 4x). */
	private final int scale;
	private boolean isBlackWhiteAlpha;

	/** Skala HD global: 0 = belum dicek. Aktif jika game punya resource /hd/scale.txt. */
	private static int hdScale = 0;

	public Image(Bitmap bitmap) {
		this(bitmap, 1);
	}

	public Image(Bitmap bitmap, int scale) {
		if (bitmap == null) {
			throw new NullPointerException();
		}
		if (scale < 1) {
			scale = 1;
		}
		this.bitmap = bitmap;
		this.scale = scale;
		bounds = new Rect(0, 0, bitmap.getWidth() / scale, bitmap.getHeight() / scale);
		bitmapBounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
	}

	/**
	 * Skala HD yang dipakai game ini. Diambil dari resource "/hd/scale.txt" di dalam jar game;
	 * jika tidak ada, hasilnya 1 sehingga perilaku emulator tidak berubah sama sekali.
	 */
	public static synchronized int getHdScale() {
		if (hdScale == 0) {
			int s = 1;
			try (InputStream stream = AppClassLoader.getResourceAsStream(null, "/hd/scale.txt")) {
				if (stream != null) {
					byte[] buf = new byte[8];
					int n = stream.read(buf);
					if (n > 0) {
						s = Integer.parseInt(new String(buf, 0, n).trim());
					}
				}
			} catch (Exception ignored) {
			}
			hdScale = (s >= 1 && s <= 8) ? s : 1;
		}
		return hdScale;
	}

	/** Kelipatan resolusi bitmap gambar ini terhadap ukuran logisnya. */
	public int getScale() {
		return scale;
	}

	/** Area piksel bitmap yang dipakai (ukuran logis * scale). */
	public Rect getBitmapBounds() {
		return bitmapBounds;
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public static Image createImage(int width, int height) {
		return createImage(width, height, Color.WHITE);
	}

	public static Image createImage(int width, int height, int argb) {
		int s = getHdScale();
		Bitmap bitmap = Bitmap.createBitmap(width * s, height * s, Bitmap.Config.ARGB_8888);
		if (argb != 0) bitmap.eraseColor(argb);
		return new Image(bitmap, s);
	}

	public static Image createImage(String resname) throws IOException {
		int s = getHdScale();
		if (s > 1) {
			Image hd = loadHdImage(resname, s);
			if (hd != null) {
				return hd;
			}
		}
		Bitmap b;
		try (InputStream stream = AppClassLoader.getResourceAsStream(null, resname)) {
			if (stream == null) {
				throw new IOException("Can't read image: " + resname);
			}
			b = PNGUtils.getFixedBitmap(stream);
		}
		if (b == null) {
			throw new IOException("Can't decode image: " + resname);
		}
		return new Image(b);
	}

	/** Muat versi HD "/hd/<nama>" dari sebuah resource; null jika tidak ada. */
	private static Image loadHdImage(String resname, int s) {
		if (resname == null || resname.isEmpty()) {
			return null;
		}
		String name = resname.replace('\\', '/');
		while (name.startsWith("/")) {
			name = name.substring(1);
		}
		try (InputStream stream = AppClassLoader.getResourceAsStream(null, "/hd/" + name)) {
			if (stream == null) {
				return null;
			}
			Bitmap b = PNGUtils.getFixedBitmap(stream);
			if (b == null || b.getWidth() % s != 0 || b.getHeight() % s != 0) {
				return null;
			}
			return new Image(b, s);
		} catch (IOException e) {
			return null;
		}
	}

	public static Image createImage(InputStream stream) throws IOException {
		Bitmap b = PNGUtils.getFixedBitmap(stream);
		if (b == null) {
			throw new IOException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
		Bitmap b = PNGUtils.getFixedBitmap(imageData, imageOffset, imageLength);
		if (b == null) {
			throw new IllegalArgumentException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(Image image, int x, int y, int width, int height, int transform) {
		int s = image.scale;
		Matrix m = transform == 0 ? null : Sprite.transformMatrix(transform, width * s / 2.0f, height * s / 2.0f);
		return new Image(Bitmap.createBitmap(image.bitmap, x * s, y * s, width * s, height * s, m, false), s);
	}

	public static Image createImage(Image source) {
		if (source.isMutable())
			return new Image(Bitmap.createBitmap(source.bitmap), source.scale);
		return source;
	}

	public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
		if (!processAlpha) {
			final int length = width * height;
			int[] tmp = new int[length];
			for (int i = 0; i < length; i++) {
				tmp[i] = rgb[i] | 0xFF000000;
			}
			rgb = tmp;
		}
		return new Image(Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888));
	}

	public Graphics getGraphics() {
		return new Graphics(this);
	}

	public boolean isMutable() {
		return bitmap.isMutable();
	}

	public int getWidth() {
		return bounds.right;
	}

	public int getHeight() {
		return bounds.bottom;
	}

	public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
		if (scale == 1) {
			bitmap.getPixels(rgbData, offset, scanlength, x, y, width, height);
			return;
		}
		// bitmap HD: ambil sampel di tengah tiap blok scale x scale (ukuran logis tetap)
		int half = scale / 2;
		for (int r = 0; r < height; r++) {
			int row = offset + r * scanlength;
			for (int c = 0; c < width; c++) {
				rgbData[row + c] = bitmap.getPixel((x + c) * scale + half, (y + r) * scale + half);
			}
		}
	}

	void copyTo(Image dst) {
		dst.getSingleGraphics().getCanvas().drawBitmap(bitmap, bitmapBounds, bounds, null);
	}

	void copyTo(Image dst, int x, int y) {
		Rect r = new Rect(x, y, x + bounds.right, y + bounds.bottom);
		dst.getSingleGraphics().getCanvas().drawBitmap(bitmap, bitmapBounds, r, null);
	}

	public Graphics getSingleGraphics() {
		if (graphics == null) {
			graphics = getGraphics();
		}
		return graphics;
	}

	void setSize(int width, int height) {
		if (width * scale > bitmap.getWidth() || height * scale > bitmap.getHeight()) {
			Bitmap b = bitmap;
			bitmap = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888);
			bounds.right = width;
			bounds.bottom = height;
			bitmapBounds.right = width * scale;
			bitmapBounds.bottom = height * scale;
			if (graphics != null) {
				graphics.reset(0, 0, width, height);
				if (scale == 1) {
					graphics.getCanvas().drawBitmap(b, 0, 0, null);
				}
			}
		} else {
			bounds.right = width;
			bounds.bottom = height;
			bitmapBounds.right = width * scale;
			bitmapBounds.bottom = height * scale;
			if (graphics != null) {
				graphics.reset(0, 0, width, height);
			}
		}
	}

	public Rect getBounds() {
		return bounds;
	}

	public boolean isBlackWhiteAlpha() {
		return isBlackWhiteAlpha;
	}

	public void setBlackWhiteAlpha(boolean blackWhiteAlpha) {
		isBlackWhiteAlpha = blackWhiteAlpha;
	}
}
