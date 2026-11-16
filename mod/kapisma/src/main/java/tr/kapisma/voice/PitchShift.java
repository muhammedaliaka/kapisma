package tr.kapisma.voice;

/**
 * Gercek zamanli perde dusurucu (cift okuma noktali gecikme hatti, capraz sonumleme): kare uzunlugu ve sure degismez, gecikme sabit.
 * Katil Kim'de herkesin sesi ayni kalin tona cevrilir. Tek kanalli 16 bit PCM, kare basina cagrilir.
 */
public final class PitchShift {
	private static final int SIZE = 16384;
	private static final int WIN = 2048;           // ~43 ms @ 48 kHz
	private final float[] buf = new float[SIZE];
	private final float[] hann = new float[WIN + 1];
	private int w;
	private float phase;
	private final float ratio;

	/** ratio < 1: kalin (0.6 = yaklasik bir oktav alti). */
	public PitchShift(float ratio) {
		this.ratio = ratio;
		for (int i = 0; i <= WIN; i++) hann[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / WIN));
	}

	public void reset() {
		java.util.Arrays.fill(buf, 0f);
		phase = 0;
		w = 0;
	}

	private float tap(float delay) {
		float pos = w - delay;
		while (pos < 0) pos += SIZE;
		int i0 = (int) pos;
		float f = pos - i0;
		return buf[i0 % SIZE] * (1 - f) + buf[(i0 + 1) % SIZE] * f;
	}

	public short[] process(short[] in) {
		short[] out = new short[in.length];
		float step = (1f - ratio) / WIN;
		for (int i = 0; i < in.length; i++) {
			buf[w] = in[i];
			float p1 = phase, p2 = phase + 0.5f;
			if (p2 >= 1f) p2 -= 1f;
			float o = tap(p1 * WIN + 2) * hann[(int) (p1 * WIN)] + tap(p2 * WIN + 2) * hann[(int) (p2 * WIN)];
			o *= 1.15f;
			out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(o)));
			w = (w + 1) % SIZE;
			phase += step;
			if (phase >= 1f) phase -= 1f;
		}
		return out;
	}
}
